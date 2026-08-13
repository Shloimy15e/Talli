package dev.dynamiq.talli.service;

import dev.dynamiq.talli.config.McpOAuthProperties;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Service
public class OAuthAccessTokenService {

    private final OAuth2AuthorizationService authorizations;
    private final UserRepository users;
    private final McpOAuthProperties properties;

    public OAuthAccessTokenService(
            OAuth2AuthorizationService authorizations,
            UserRepository users,
            McpOAuthProperties properties) {
        this.authorizations = authorizations;
        this.users = users;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        var authorization = authorizations.findByToken(rawToken, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null
                || !authorization.getAccessToken().isActive()
                || !authorization.getAuthorizedScopes().contains("mcp")) {
            return Optional.empty();
        }

        Map<String, Object> claims = authorization.getAccessToken().getClaims();
        if (!properties.resource().equals(claims.get("resource"))
                || !hasAudience(claims.get("aud"), properties.resource())) {
            return Optional.empty();
        }

        return users.findByEmail(authorization.getPrincipalName())
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()));
    }

    private boolean hasAudience(Object claim, String expectedAudience) {
        if (claim instanceof Collection<?> audiences) {
            return audiences.contains(expectedAudience);
        }
        return expectedAudience.equals(claim);
    }
}
