package dev.dynamiq.talli.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.mcp.events.McpEventException;
import dev.dynamiq.talli.mcp.events.McpEventService;
import dev.dynamiq.talli.model.Role;
import dev.dynamiq.talli.model.Permission;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.RoleRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.ApiTokenService;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real SDK tool transport alongside the event-service RPC boundary. */
@RefreshDatabaseTest
@AutoConfigureMockMvc
class McpEventsProtocolTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private ApiTokenService tokens;
    @Autowired private EntityManager entityManager;
    @MockitoBean private McpEventService events;

    @Test
    void discoveryUsesModernCapabilitiesAndStandardIconMetadata() throws Exception {
        when(events.isEnabled()).thenReturn(true);
        mvc.perform(rpc(token(), """
                {"jsonrpc":"2.0","id":"discover","method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resultType").value("complete"))
                .andExpect(jsonPath("$.result.supportedVersions[0]").value("2026-07-28"))
                .andExpect(jsonPath("$.result.capabilities.tools").isMap())
                .andExpect(jsonPath("$.result.capabilities.events").isMap())
                .andExpect(jsonPath("$.result.serverInfo.title").value("Talli"))
                .andExpect(jsonPath("$.result.serverInfo.icons[0].src").value("http://localhost:8080/brand/talli-mcp-icon.png"))
                .andExpect(jsonPath("$.result.serverInfo.icons[0].mimeType").value("image/png"))
                .andExpect(jsonPath("$.result.serverInfo.icons[0].sizes[0]").value("512x512"));
    }

    @Test
    void disabledEventsAreNotAdvertisedBeforeRollout() throws Exception {
        when(events.isEnabled()).thenReturn(false);
        mvc.perform(rpc(token(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.capabilities.tools").isMap())
                .andExpect(jsonPath("$.result.capabilities.events").doesNotExist());
    }

    @Test
    void advertisedVersionActuallyListsAndCallsToolsWithModernMetadata() throws Exception {
        String token = token();
        mvc.perform(rpc(token, """
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("find_clients", "get_email_conversation"));
        mvc.perform(rpc(token, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"},"name":"find_clients","arguments":{"limit":1}}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("Access Denied"));
        Permission permission = new Permission();
        permission.setName("view-clients");
        entityManager.persist(permission);
        Role role = roles.findByName("mcp-protocol-test").orElseThrow();
        role.setPermissions(new java.util.HashSet<>(Set.of(permission)));
        roles.saveAndFlush(role);
        mvc.perform(rpc(token, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"},"name":"find_clients","arguments":{"limit":1}}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content").isArray());
    }

    @Test
    void legacyInitializeAndToolsRemainAvailable() throws Exception {
        String token = token();
        mvc.perform(rpc(token, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"legacy-test","version":"1"}}}
                """).header("MCP-Protocol-Version", "2025-06-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("talli"));
        mvc.perform(rpc(token, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """).header("MCP-Protocol-Version", "2025-06-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray());
    }

    @Test
    void eventRequestsUseAuthenticatedPrincipalAndPreserveRpcIds() throws Exception {
        when(events.catalog(any())).thenReturn(mapper.readTree("{\"events\":[]}"));
        when(events.subscribe(any(), any())).thenReturn(mapper.readTree("{\"id\":\"sub_test\",\"refreshBefore\":\"2026-10-08T00:00:00Z\",\"cursor\":null,\"truncated\":false}"));
        when(events.unsubscribe(any(), any())).thenReturn(mapper.createObjectNode());
        String token = token();
        mvc.perform(rpc(token, "{\"jsonrpc\":\"2.0\",\"id\":\"catalog\",\"method\":\"events/list\",\"params\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("catalog"))
                .andExpect(jsonPath("$.result.events").isEmpty());
        mvc.perform(rpc(token, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"events/subscribe\",\"params\":{\"name\":\"email.received\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.id").value("sub_test"));
        mvc.perform(rpc(token, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"events/unsubscribe\",\"params\":{\"name\":\"email.received\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
        var principal = org.mockito.ArgumentCaptor.forClass(Authentication.class);
        verify(events).catalog(principal.capture());
        assertThat(principal.getValue().isAuthenticated()).isTrue();
        assertThat(principal.getValue().getName()).isEqualTo("protocol-test@example.test");
    }

    @Test
    void callbackErrorsHaveCategorizedReasonAndDoNotExposeRequestSecrets() throws Exception {
        when(events.subscribe(any(), any())).thenThrow(new McpEventException(-32015, "challenge_failed", "Callback verification failed"));
        mvc.perform(rpc(token(), """
                {"jsonrpc":"2.0","id":7,"method":"events/subscribe","params":{"delivery":{"url":"https://callback.example/secret-route","secret":"whsec_private"}}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32015))
                .andExpect(jsonPath("$.error.data.reason").value("challenge_failed"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("whsec_private", "secret-route"));
    }

    @Test
    void invalidParamsAndUnknownEventMethodsAreJsonRpcErrors() throws Exception {
        String token = token();
        mvc.perform(rpc(token, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"events/subscribe\",\"params\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.error.code").value(-32602));
        mvc.perform(rpc(token, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"events/list\",\"params\":{\"cursor\":\"bad\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.error.code").value(-32602));
        mvc.perform(rpc(token, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"events/unknown\",\"params\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.error.code").value(-32601));
        verifyNoInteractions(events);
    }

    @Test
    void eventNotificationsHaveNoResponseOrSubscriptionSideEffects() throws Exception {
        mvc.perform(rpc(token(), "{\"jsonrpc\":\"2.0\",\"method\":\"events/subscribe\",\"params\":{}}"))
                .andExpect(status().isAccepted())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
        verifyNoInteractions(events);
    }

    @Test
    void eventEndpointRequiresTheExistingBearerAuthentication() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"events/list\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(events);
    }

    private MockHttpServletRequestBuilder rpc(String token, String body) {
        return post("/mcp").header("Authorization", "Bearer " + token)
                .header("MCP-Protocol-Version", "2026-07-28")
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String token() {
        Role role = new Role();
        role.setName("mcp-protocol-test");
        role = roles.save(role);
        User user = new User();
        user.setName("Protocol test");
        user.setEmail("protocol-test@example.test");
        user.setPassword("unused");
        user.setRoles(Set.of(role));
        return tokens.generate(users.save(user), "Protocol test");
    }
}
