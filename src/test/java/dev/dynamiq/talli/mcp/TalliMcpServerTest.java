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
                        "log_time", "start_timer", "stop_timer", "log_expense",
                        "record_payment", "set_invoice_ach_link",
                        "preview_client_email", "send_client_email");
    }

    @Test
    void endpointRequiresBearerToken() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void toolInvocationEnforcesTalliPermissions() throws Exception {
        Role role = new Role();
        role.setName("mcp-no-access");
        role = roles.save(role);

        User user = new User();
        user.setName("MCP test");
        user.setEmail("mcp-no-access@example.test");
        user.setPassword("unused");
        user.setRoles(Set.of(role));
        user = users.save(user);
        String token = apiTokens.generate(user, "MCP test");

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
}
