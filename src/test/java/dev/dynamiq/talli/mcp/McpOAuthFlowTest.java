package dev.dynamiq.talli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.model.Permission;
import dev.dynamiq.talli.model.Role;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.RoleRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RefreshDatabaseTest
@AutoConfigureMockMvc
class McpOAuthFlowTest {

    private static final String ISSUER = "http://localhost:8080";
    private static final String RESOURCE = ISSUER + "/mcp";
    private static final String REDIRECT_URI = "https://chatgpt.com/connector/oauth/talli-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRepository users;

    @Test
    @Sql("/db/migration/V43__oauth_authorization_server.sql")
    void supportsChatGptDiscoveryRegistrationPkceRefreshAndLiveUserRevocation() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value(RESOURCE))
                .andExpect(jsonPath("$.authorization_servers[0]").value(ISSUER));

        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").value(ISSUER + "/oauth/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value(ISSUER + "/oauth/token"))
                .andExpect(jsonPath("$.registration_endpoint").value(ISSUER + "/oauth/register"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));

        mockMvc.perform(post("/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"redirect_uris":["https://attacker.example/oauth/callback"],"scope":"mcp"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_client_metadata"));

        JsonNode registration = json(mockMvc.perform(post("/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client_name":"ChatGPT",
                                  "redirect_uris":["%s"],
                                  "token_endpoint_auth_method":"none",
                                  "grant_types":["authorization_code","refresh_token"],
                                  "response_types":["code"],
                                  "scope":"mcp offline_access mcp"
                                }
                                """.formatted(REDIRECT_URI)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token_endpoint_auth_method").value("client_secret_post"))
                .andReturn());

        String clientId = registration.get("client_id").asText();
        String clientSecret = registration.get("client_secret").asText();
        User user = createMcpUser();
        MockHttpSession session = authenticatedSession(user);
        String verifier = "a-very-long-pkce-verifier-that-is-safe-for-an-oauth-authorization-code-flow";

        mockMvc.perform(get("/oauth/authorize")
                        .session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", clientId)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "mcp offline_access")
                        .queryParam("state", "test-state")
                        .queryParam("code_challenge", challenge(verifier))
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_target"));

        MvcResult authorization = mockMvc.perform(get("/oauth/authorize")
                        .session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", clientId)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "mcp offline_access")
                        .queryParam("state", "test-state")
                        .queryParam("code_challenge", challenge(verifier))
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("resource", RESOURCE))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirect = authorization.getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith(REDIRECT_URI);
        var redirectParameters = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams();
        assertThat(redirectParameters.getFirst("state")).isEqualTo("test-state");
        String code = redirectParameters.getFirst("code");
        assertThat(code).isNotBlank();

        JsonNode tokens = json(mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code", code)
                        .param("code_verifier", verifier)
                        .param("resource", RESOURCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope", containsString("mcp")))
                .andReturn());

        String accessToken = tokens.get("access_token").asText();
        String refreshToken = tokens.get("refresh_token").asText();
        invokeMcp(accessToken).andExpect(status().isOk());

        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret)
                        .param("refresh_token", refreshToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_target"));

        JsonNode refreshed = json(mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret)
                        .param("refresh_token", refreshToken)
                        .param("resource", RESOURCE))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(refreshed.get("access_token").asText()).isNotEqualTo(accessToken);
        assertThat(refreshed.get("refresh_token").asText()).isNotEqualTo(refreshToken);

        user.setEnabled(false);
        entityManager.flush();

        invokeMcp(refreshed.get("access_token").asText())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString(
                        "resource_metadata=\"" + ISSUER + "/.well-known/oauth-protected-resource\"")));
    }

    private User createMcpUser() {
        Permission permission = new Permission();
        permission.setName("view-clients");
        entityManager.persist(permission);

        Role role = new Role();
        role.setName("oauth-mcp-reader");
        role.setPermissions(new HashSet<>(Set.of(permission)));
        role = roles.save(role);

        User user = new User();
        user.setName("OAuth MCP test");
        user.setEmail("oauth-mcp@example.test");
        user.setPassword("unused");
        user.setRoles(new HashSet<>(Set.of(role)));
        return users.saveAndFlush(user);
    }

    private MockHttpSession authenticatedSession(User user) {
        var authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, new ArrayList<>(List.of(
                        new SimpleGrantedAuthority("ROLE_oauth-mcp-reader"),
                        new SimpleGrantedAuthority("view-clients"))));
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext);
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions invokeMcp(String accessToken) throws Exception {
        return mockMvc.perform(post("/mcp")
                .header("Authorization", "Bearer " + accessToken)
                .header("MCP-Protocol-Version", "2025-06-18")
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_clients","arguments":{"limit":1}}}
                        """));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
