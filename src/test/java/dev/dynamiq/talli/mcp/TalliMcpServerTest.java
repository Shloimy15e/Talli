package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Role;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.RoleRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.ApiTokenService;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RefreshDatabaseTest
@AutoConfigureMockMvc
class TalliMcpServerTest {

    @Autowired
    private McpStatelessSyncServer server;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private ApiTokenService apiTokens;

    @Test
    void publishesTheExpectedToolSurface() {
        assertThat(server.listTools())
                .extracting(tool -> tool.name())
                .containsExactlyInAnyOrder(
                        "find_clients", "find_projects", "find_time_entries", "find_expenses",
                        "current_timer", "find_invoices", "get_invoice", "find_subscriptions", "run_report",
                        "create_client", "update_client", "create_project", "update_project",
                        "log_time", "start_timer", "stop_timer", "update_time_entry", "delete_time_entry",
                        "log_expense",
                        "update_expense", "delete_expense",
                        "create_subscription", "update_subscription", "delete_subscription",
                        "cancel_subscription", "reactivate_subscription", "record_subscription_charge",
                        "link_expense_to_subscription", "unlink_expense_from_subscription",
                        "record_payment", "delete_payment", "set_invoice_ach_link",
                        "preview_client_email", "send_client_email");
    }

    @Test
    void endpointRequiresBearerToken() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void modernDiscoveryFallsBackToTheSupportedLegacyHandshake() throws Exception {
        String token = tokenForRole("mcp-discovery");

        mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc":"2.0",
                                  "id":"discover-1",
                                  "method":"server/discover",
                                  "params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("discover-1"))
                .andExpect(jsonPath("$.error.code").value(-32601))
                .andExpect(jsonPath("$.error.message").value("Method not found"));
    }

    @Test
    void toolInvocationEnforcesTalliPermissions() throws Exception {
        String token = tokenForRole("mcp-no-access");

        mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + token)
                        .header("MCP-Protocol-Version", "2025-06-18")
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_clients","arguments":{"limit":1}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("isError", "true", "Access Denied"));
    }

    private String tokenForRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        role = roles.save(role);

        User user = new User();
        user.setName("MCP test");
        user.setEmail(roleName + "@example.test");
        user.setPassword("unused");
        user.setRoles(Set.of(role));
        user = users.save(user);
        return apiTokens.generate(user, "MCP test");
    }
}
