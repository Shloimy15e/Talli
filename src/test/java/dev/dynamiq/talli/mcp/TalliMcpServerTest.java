package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Role;
import dev.dynamiq.talli.model.Permission;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.RoleRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.ApiTokenService;
import dev.dynamiq.talli.service.AgentEmailService;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.service.EmailSenderProfileService;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import jakarta.persistence.EntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    @Autowired
    private Environment environment;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AgentEmailService agentEmails;

    @Autowired
    private EmailSenderProfileService senderProfiles;

    @Autowired
    private EmailRepository emails;

    @MockitoBean
    private EmailService emailService;

    @Test
    void publishesTheExpectedToolSurface() {
        var tools = server.listTools();
        assertThat(tools)
                .extracting(tool -> tool.name())
                .containsExactlyInAnyOrder(
                        "find_clients", "find_client_emails", "get_email_conversation", "find_projects", "find_time_entries", "find_expenses",
                        "current_timer", "find_invoices", "get_invoice", "find_subscriptions", "run_report",
                        "create_client", "update_client", "create_project", "update_project",
                        "log_time", "start_timer", "stop_timer", "update_time_entry", "delete_time_entry",
                        "log_expense",
                        "update_expense", "delete_expense",
                        "create_subscription", "update_subscription", "delete_subscription",
                        "cancel_subscription", "reactivate_subscription", "record_subscription_charge",
                        "link_expense_to_subscription", "unlink_expense_from_subscription",
                        "record_payment", "delete_payment", "set_invoice_ach_link",
                        "list_email_senders", "preview_client_email", "send_client_email");

        for (String toolName : Set.of("preview_client_email", "send_client_email")) {
            var schema = tools.stream()
                    .filter(tool -> toolName.equals(tool.name()))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema();
            Object oversightParameter = schema.properties().get("includeOversightCc");
            assertThat(oversightParameter)
                    .as(toolName + " includeOversightCc schema")
                    .isInstanceOf(java.util.Map.class);
            assertThat(((java.util.Map<?, ?>) oversightParameter).get("type"))
                    .isEqualTo("boolean");
            assertThat(schema.required()).doesNotContain("includeOversightCc");
        }
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

    @Test
    void discoveryExplainsExistingCapabilitiesAndUsesPublishedArgumentNames() {
        var tools = server.listTools();
        var invoices = tools.stream().filter(tool -> "find_invoices".equals(tool.name())).findFirst().orElseThrow();
        assertThat(invoices.description())
                .contains("outstanding balance", "offset", "get_invoice", "record_payment/delete_payment", "set_invoice_ach_link")
                .doesNotContain("writes are intentionally unavailable");
        var subscriptions = tools.stream().filter(tool -> "find_subscriptions".equals(tool.name())).findFirst().orElseThrow();
        assertThat(subscriptions.description()).contains("active and cancelled", "subscriptionId", "recorded charges");
        assertThat(subscriptions.inputSchema().required()).doesNotContain("active", "offset", "limit");
        var projectUpdate = tools.stream().filter(tool -> "update_project".equals(tool.name())).findFirst().orElseThrow();
        assertThat(projectUpdate.description()).contains("currentRate", "rateChangeReason");
        assertThat(projectUpdate.inputSchema().properties()).containsKeys("currentRate", "rateChangeReason", "status", "billable", "notes");
        var emailPreview = tools.stream().filter(tool -> "preview_client_email".equals(tool.name())).findFirst().orElseThrow();
        assertThat(emailPreview.inputSchema().properties()).containsKeys("senderEmail", "templateId", "includeSignature", "replyToEmailId");
        assertThat(emailPreview.inputSchema().required()).doesNotContain("senderEmail", "templateId", "includeSignature", "replyToEmailId");
        for (String name : Set.of("preview_client_email", "send_client_email")) {
            var emailTool = tools.stream().filter(tool -> name.equals(tool.name())).findFirst().orElseThrow();
            assertThat(emailTool.inputSchema().properties()).containsKeys("clientId", "toAddress");
            assertThat(emailTool.inputSchema().required()).doesNotContain("clientId", "toAddress");
            assertThat(emailTool.description()).contains("standalone", "toAddress");
        }
        assertThat(environment.getRequiredProperty("spring.ai.mcp.server.instructions"))
                .contains("cancel, reactivate", "record charges", "link or unlink", "any bank or payment provider",
                        "threaded replies", "camelCase", "nextOffset");
    }

    @Test
    void emailConversationReadRequiresAdminRole() throws Exception {
        String token = tokenForRole("mcp-send-only");

        mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + token)
                        .header("MCP-Protocol-Version", "2025-06-18")
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_email_conversation","arguments":{"emailId":1}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("isError", "true", "Access Denied"));
    }

    @Test
    void standaloneEmailStillRequiresSendPermissionAndAdminForReplies() throws Exception {
        for (String role : Set.of("mcp-no-access", "mcp-send-only")) {
            String token = tokenForRole(role);
            if ("mcp-send-only".equals(role)) {
                Permission permission = new Permission();
                permission.setName("send-emails");
                entityManager.persist(permission);
                Role senderRole = roles.findByName(role).orElseThrow();
                senderRole.setPermissions(new java.util.HashSet<>(Set.of(permission)));
                roles.saveAndFlush(senderRole);
            }
            mockMvc.perform(post("/mcp")
                            .header("Authorization", "Bearer " + token)
                            .header("MCP-Protocol-Version", "2025-06-18")
                            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"preview_client_email","arguments":{"toAddress":"new@example.test","subject":"Hello","body":"Body","replyToEmailId":1}}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                            .contains("isError", "true", "Access Denied"));
        }
    }

    @Test
    void approvedStandaloneEmailPersistsWithoutClientAndHasReadableConversation() {
        senderProfiles.create("info@dynamiq.dev", "Dynamiq", "<p>Dynamiq</p>", true);
        when(emailService.sendMessage(any(), anyString(), anyList(), anyList(), anyString(),
                anyString(), any(), anyList(), any()))
                .thenReturn(new EmailService.Result(null, "standalone-provider-id", "<standalone@dynamiq.dev>", "info@dynamiq.dev"));
        var preview = agentEmails.preview("operator@example.test", null, "Hello", "Body", null,
                false, false, null, null, "new@example.test");
        var sent = agentEmails.send("operator@example.test", null, "Hello", "Body", null,
                false, false, null, preview.previewToken(), true, null, "new@example.test");
        Long emailId = sent.email().getId();
        entityManager.flush();
        entityManager.clear();

        var saved = emails.findById(emailId).orElseThrow();
        assertThat(saved.getClient()).isNull();
        assertThat(saved.getToAddress()).isEqualTo("new@example.test");
        assertThat(saved.getSource()).isEqualTo("mcp");
        assertThat(saved.getInitiatedBy()).isEqualTo("operator@example.test");
        assertThat(saved.getResendId()).isEqualTo("standalone-provider-id");
        assertThat(saved.getThreadRootId()).isEqualTo(emailId);
        assertThat(emails.findConversation(emailId)).extracting(email -> email.getId()).containsExactly(emailId);
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
