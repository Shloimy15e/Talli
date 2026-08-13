package dev.dynamiq.talli.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dynamiq.talli.config.McpOAuthProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/oauth")
public class OAuthClientRegistrationController {

    private static final Set<String> SUPPORTED_GRANTS = Set.of("authorization_code", "refresh_token");
    private static final Set<String> SUPPORTED_SCOPES = Set.of("mcp", "offline_access");

    private final RegisteredClientRepository clients;
    private final JdbcOperations jdbcOperations;
    private final PasswordEncoder passwordEncoder;
    private final McpOAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthClientRegistrationController(
            RegisteredClientRepository clients,
            JdbcOperations jdbcOperations,
            PasswordEncoder passwordEncoder,
            McpOAuthProperties properties) {
        this.clients = clients;
        this.jdbcOperations = jdbcOperations;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegistrationRequest request) {
        validate(request);

        Integer existingClients = jdbcOperations.queryForObject(
                "SELECT COUNT(*) FROM oauth2_registered_client", Integer.class);
        if (existingClients != null && existingClients >= properties.dynamicClientLimit()) {
            throw new RegistrationException("Dynamic client registration limit reached");
        }

        Instant issuedAt = Instant.now();
        String clientId = "talli_" + UUID.randomUUID();
        String clientSecret = randomSecret();

        RegisteredClient.Builder client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientIdIssuedAt(issuedAt)
                .clientName(normalizedClientName(request.clientName()))
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("mcp")
                .scope("offline_access")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                        .accessTokenTimeToLive(properties.accessTokenTtl())
                        .refreshTokenTimeToLive(properties.refreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .build());
        request.redirectUris().forEach(client::redirectUri);
        clients.save(client.build());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("client_id", clientId);
        response.put("client_secret", clientSecret);
        response.put("client_id_issued_at", issuedAt.getEpochSecond());
        response.put("client_secret_expires_at", 0);
        response.put("client_name", normalizedClientName(request.clientName()));
        response.put("redirect_uris", request.redirectUris());
        response.put("token_endpoint_auth_method", "client_secret_post");
        response.put("grant_types", List.of("authorization_code", "refresh_token"));
        response.put("response_types", List.of("code"));
        response.put("scope", "mcp offline_access");
        return ResponseEntity.status(201).body(response);
    }

    @ExceptionHandler(RegistrationException.class)
    ResponseEntity<Map<String, String>> invalidRegistration(RegistrationException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_client_metadata",
                "error_description", exception.getMessage()));
    }

    private void validate(RegistrationRequest request) {
        if (request.redirectUris() == null || request.redirectUris().isEmpty()
                || request.redirectUris().size() > 5) {
            throw new RegistrationException("One to five redirect_uris are required");
        }
        request.redirectUris().forEach(this::validateRedirectUri);

        if (request.grantTypes() != null
                && (!request.grantTypes().contains("authorization_code")
                || !SUPPORTED_GRANTS.containsAll(request.grantTypes()))) {
            throw new RegistrationException("Only authorization_code and refresh_token grants are supported");
        }
        if (request.responseTypes() != null
                && (request.responseTypes().size() != 1 || !request.responseTypes().contains("code"))) {
            throw new RegistrationException("Only the code response type is supported");
        }
        if (request.tokenEndpointAuthMethod() != null
                && !Set.of("none", "client_secret_post", "client_secret_basic")
                .contains(request.tokenEndpointAuthMethod())) {
            throw new RegistrationException("Unsupported token endpoint authentication method");
        }
        if (request.scope() != null) {
            Set<String> requestedScopes = Arrays.stream(request.scope().strip().split("\\s+"))
                    .filter(scope -> !scope.isBlank())
                    .collect(Collectors.toSet());
            if (!SUPPORTED_SCOPES.containsAll(requestedScopes) || !requestedScopes.contains("mcp")) {
                throw new RegistrationException("Only mcp and offline_access scopes are supported");
            }
        }
    }

    private void validateRedirectUri(String value) {
        if (value == null || value.isBlank()) {
            throw new RegistrationException("redirect_uris must contain valid URLs");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new RegistrationException("redirect_uris must contain valid URLs");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean currentCallback = path.matches("^/connector/oauth/[^/]+$");
        boolean legacyCallback = "/connector_platform_oauth_redirect".equals(path);
        boolean permitted = "https".equalsIgnoreCase(uri.getScheme())
                && "chatgpt.com".equalsIgnoreCase(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null
                && (currentCallback || legacyCallback);
        if (!permitted) {
            throw new RegistrationException("redirect_uris must use a ChatGPT OAuth callback URL");
        }
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizedClientName(String value) {
        if (value == null || value.isBlank()) {
            return "ChatGPT Talli connector";
        }
        String normalized = value.strip();
        return normalized.substring(0, Math.min(normalized.length(), 200));
    }

    public record RegistrationRequest(
            @JsonProperty("redirect_uris") List<String> redirectUris,
            @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod,
            @JsonProperty("grant_types") List<String> grantTypes,
            @JsonProperty("response_types") List<String> responseTypes,
            String scope,
            @JsonProperty("client_name") String clientName) {
    }

    private static final class RegistrationException extends RuntimeException {
        private RegistrationException(String message) {
            super(message);
        }
    }
}
