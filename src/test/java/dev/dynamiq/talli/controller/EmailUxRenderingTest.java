package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.service.EmailSenderProfileService;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RefreshDatabaseTest
@AutoConfigureMockMvc
class EmailUxRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailRepository emails;

    @Autowired
    private EmailSenderProfileService senderProfiles;

    @Autowired
    private dev.dynamiq.talli.repository.UserRepository users;

    @Autowired
    private McpStatelessSyncServer mcpServer;

    @BeforeEach
    void createSenderProfiles() {
        var admin = new dev.dynamiq.talli.model.User();
        admin.setEmail("admin@example.test");
        admin.setName("Mail administrator");
        admin.setPassword("unused-in-authenticated-test");
        users.saveAndFlush(admin);
        senderProfiles.create("billing@dynamiq.dev", "Dynamiq Billing",
                "<strong>Billing desk</strong><br>billing@dynamiq.dev", true);
        senderProfiles.create("sales@dynamiq.dev", "Dynamiq Sales",
                "<strong>Sales desk</strong><br>sales@dynamiq.dev", false);
    }

    @Test
    void composeFormRendersActiveSenderChoicesAndTheDefaultIdentity() throws Exception {
        String html = rendered(get("/emails/new"));

        assertThat(html)
                .contains("name=\"senderEmail\"", "data-sender=\"billing@dynamiq.dev\"")
                .contains("value=\"billing@dynamiq.dev\"", "Dynamiq Billing", "Billing desk")
                .contains("value=\"sales@dynamiq.dev\"", "Dynamiq Sales", "Sales desk");
    }

    @Test
    void replyFormRendersTheOriginalRecipientAndThreadContext() throws Exception {
        Email inbound = saveEmail("in", "customer@example.test", "billing@dynamiq.dev",
                "Pricing question", "Can you send the details?", "<customer-message@example.test>", null);

        String html = rendered(get("/emails/new").queryParam("replyToEmailId", inbound.getId().toString()));

        assertThat(html)
                .contains("name=\"replyToEmailId\"", "value=\"" + inbound.getId() + "\"")
                .contains("value=\"customer@example.test\"", "value=\"Re: Pricing question\"")
                .contains("readonly=\"readonly\"")
                .contains("data-sender=\"billing@dynamiq.dev\"");
    }

    @Test
    void legacyReplyFormPrefillsSavedEnvelopeAndKeepsRecipientAndSubjectEditable() throws Exception {
        Email inbound = saveEmail("in", "customer@example.test", "billing@dynamiq.dev",
                "Pricing question", "Can you send the details?", null, null);
        inbound.setCc("teammate@example.test");
        inbound.setBcc("archive@example.test");
        emails.saveAndFlush(inbound);

        String form = rendered(get("/emails/new").queryParam("replyToEmailId", inbound.getId().toString()));
        assertThat(form)
                .contains("name=\"replyToEmailId\"", "value=\"" + inbound.getId() + "\"")
                .contains("value=\"customer@example.test\"", "value=\"Pricing question\"")
                .contains("data-cc=\"teammate@example.test\"", "data-bcc=\"archive@example.test\"")
                .contains("data-sender=\"billing@dynamiq.dev\"")
                .doesNotContain("readonly=\"readonly\"");

        String conversation = rendered(get("/emails/{id}", inbound.getId()));
        assertThat(conversation)
                .contains("/emails/new?replyToEmailId=" + inbound.getId(), "Reply")
                .doesNotContain("does not have a valid reply recipient");
    }

    @Test
    void emailIndexAndConversationShowRenderThreadNavigationForStoredMessageIds() throws Exception {
        Email inbound = saveEmail("in", "customer@example.test", "billing@dynamiq.dev",
                "Pricing question", "Can you send the details?", "<customer-message@example.test>", null);
        Email reply = saveEmail("out", "billing@dynamiq.dev", "customer@example.test",
                "Re: Pricing question", "Here are the details.", "<billing-reply@example.test>", inbound.getId());
        reply.setInReplyTo(inbound.getMessageId());
        reply.setReferencesHeader(inbound.getMessageId());
        emails.saveAndFlush(reply);

        String index = rendered(get("/emails"));
        assertThat(index).contains("Pricing question", "customer@example.test", "Sender profiles");

        String show = rendered(get("/emails/{id}", reply.getId()));
        assertThat(show)
                .contains("Conversation", "2 messages", "Pricing question")
                .contains("/emails/new?replyToEmailId=" + reply.getId())
                .contains("Reply");
    }

    @Test
    void senderProfilesPageEscapesStoredSignatureMarkupWhileShowingTheIdentity() throws Exception {
        String html = rendered(get("/emails/senders"));

        assertThat(html)
                .contains("Sender profiles", "Dynamiq Billing", "billing@dynamiq.dev")
                .contains("&lt;strong&gt;Billing desk&lt;/strong&gt;")
                .doesNotContain("<strong>Billing desk</strong>");
    }

    @Test
    void senderProfilesPageIncludesThePreviewDialogMotionStyles() throws Exception {
        String html = rendered(get("/emails/senders"));

        assertThat(html).contains(".html-preview-dialog", "transition: opacity 180ms",
                "prefers-reduced-motion: reduce");
    }

    @Test
    void fullMailPageReferencesVersionedAssetsThatResolve() throws Exception {
        String html = rendered(get("/emails"));

        assertThat(html)
                .contains("<style id=\"email-signature-styles\">",
                        "@media only screen and (max-width:480px)",
                        ".dynamiq-email-signature .dynamiq-email-signature__contact")
                .doesNotContain("<style id=\"email-signature-styles\"></style>");

        String stylesheet = versionedAsset(html, "href", "/css/mail-composer");
        String script = versionedAsset(html, "src", "/js/mail-composer");

        mockMvc.perform(get(stylesheet)).andExpect(status().isOk());
        mockMvc.perform(get(script)).andExpect(status().isOk());
    }

    @Test
    void publishedMcpSchemaUsesJavaStyleEmailReplyParameterNames() {
        var tools = mcpServer.listTools().stream()
                .collect(java.util.stream.Collectors.toMap(
                        io.modelcontextprotocol.spec.McpSchema.Tool::name,
                        io.modelcontextprotocol.spec.McpSchema.Tool::inputSchema));

        assertThat(tools.get("preview_client_email").properties())
                .containsKeys("clientId", "subject", "body", "senderEmail", "templateId",
                        "includeSignature", "replyToEmailId")
                .doesNotContainKeys("sender_email", "reply_to_email_id");
        assertThat(tools.get("send_client_email").properties())
                .containsKeys("clientId", "subject", "body", "senderEmail", "templateId",
                        "includeSignature", "previewToken", "confirmSend", "replyToEmailId")
                .doesNotContainKeys("sender_email", "preview_token", "confirm_send", "reply_to_email_id");
        assertThat(tools.get("find_client_emails").properties())
                .containsKeys("clientId", "offset", "limit");
        assertThat(tools.get("get_email_conversation").properties())
                .containsKeys("emailId", "offset", "limit");
    }

    private Email saveEmail(String direction, String fromAddress, String toAddress, String subject,
                            String body, String messageId, Long threadRootId) {
        Email email = new Email();
        email.setDirection(direction);
        email.setFromAddress(fromAddress);
        email.setToAddress(toAddress);
        email.setSubject(subject);
        email.setBody(body);
        email.setStatus("in".equals(direction) ? "received" : "sent");
        email.setMessageId(messageId);
        email = emails.saveAndFlush(email);
        email.setThreadRootId(threadRootId == null ? email.getId() : threadRootId);
        return emails.saveAndFlush(email);
    }

    private String versionedAsset(String html, String attribute, String basePath) {
        var matcher = Pattern.compile(attribute + "=\"(" + Pattern.quote(basePath)
                + "-[0-9a-f]{32}\\.[a-z]+)\"").matcher(html);
        assertThat(matcher.find()).as("rendered content-version URL for %s", basePath).isTrue();
        return matcher.group(1);
    }

    private String rendered(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request.session(authenticatedAdminSession()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private MockHttpSession authenticatedAdminSession() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_admin")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
