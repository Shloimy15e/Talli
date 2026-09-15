package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEmailServiceTest {

    private ClientRepository clients;
    private EmailRepository emails;
    private EmailService emailService;
    private EmailThreadService threads;
    private EmailSenderProfileService senders;
    private AgentEmailService service;

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        emails = mock(EmailRepository.class);
        emailService = mock(EmailService.class);
        threads = mock(EmailThreadService.class);
        senders = mock(EmailSenderProfileService.class);
        when(senders.resolve(any())).thenAnswer(invocation -> sender(invocation.getArgument(0)));
        service = new AgentEmailService(clients, emails, emailService,
                new EmailTemplateCatalog(senders), senders, threads, "shloimy@dynamiq.dev");

        Client client = new Client();
        client.setId(7L);
        client.setName("Acme");
        client.setEmail("billing@acme.test");
        when(clients.findById(7L)).thenReturn(Optional.of(client));
        when(emails.save(any(Email.class))).thenAnswer(invocation -> {
            Email email = invocation.getArgument(0);
            if (email.getId() == null) email.setId(99L);
            return email;
        });
        when(emailService.sendMessage(any(EmailSender.class), anyString(), anyList(), anyList(),
                anyString(), anyString(), any(), anyList(), any()))
                .thenReturn(new EmailService.Result("<html></html>", "msg-plain", "<message@dynamiq.dev>", "info@dynamiq.dev"));
    }

    @Test
    void sendsTemplatedEmailWithSelectedSenderSignature() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Invoice update", "Amount < $100", "branded", true, true,
                "billing@dynamiq.dev", null);

        AgentEmailService.SendResult result = service.send("finance@dynamiq.dev", 7L,
                "Invoice update", "Amount < $100", "branded", true, true,
                "billing@dynamiq.dev", preview.previewToken(), true, null);

        verify(emailService).sendMessage(eq(sender("billing@dynamiq.dev")), eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()), eq("Invoice update"),
                eq("Amount < $100"), html.capture(), eq(List.of()), eq(Map.of()));
        assertThat(html.getValue()).contains("Dynamiq Billing", "billing@dynamiq.dev", "Amount &lt; $100");
        assertThat(result.email().getStatus()).isEqualTo("sent");
        assertThat(result.email().getMessageId()).isEqualTo("<message@dynamiq.dev>");
        assertThat(result.email().getSource()).isEqualTo("mcp");
        assertThat(result.email().getInitiatedBy()).isEqualTo("finance@dynamiq.dev");
    }

    @Test
    void sendsPlainEmailWithoutTemplateOrSignature() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Quick note", "Hello", null, false, false, null, null);
        AgentEmailService.SendResult result = service.send("finance@dynamiq.dev", 7L,
                "Quick note", "Hello", null, false, false, null, preview.previewToken(), true, null);

        verify(emailService).sendMessage(eq(sender(null)), eq("billing@acme.test"),
                eq(List.of()), eq(List.of()), eq("Quick note"), eq("Hello"),
                eq(null), eq(List.of()), eq(Map.of()));
        assertThat(preview.ccAddress()).isNull();
        assertThat(result.email().getBodyHtml()).isNull();
    }

    @Test
    void failedSendKeepsMcpSourceAndInitiatingAccountInTheAuditRow() {
        when(emailService.sendMessage(any(EmailSender.class), anyString(), anyList(), anyList(),
                anyString(), anyString(), any(), anyList(), any()))
                .thenThrow(new IllegalStateException("Provider unavailable"));
        var preview = service.preview("operator@example.test", 7L,
                "Quick note", "Hello", null, false, false, null, null);

        AgentEmailService.SendResult result = service.send("operator@example.test", 7L,
                "Quick note", "Hello", null, false, false, null, preview.previewToken(), true, null);

        assertThat(result.email().getStatus()).isEqualTo("failed");
        assertThat(result.email().getSource()).isEqualTo("mcp");
        assertThat(result.email().getInitiatedBy()).isEqualTo("operator@example.test");
        assertThat(result.email().getErrorMessage()).isEqualTo("Provider unavailable");
    }

    @Test
    void omitsOversightCopyWhenOwnerIsThePrimaryRecipient() {
        Client owner = new Client();
        owner.setId(7L);
        owner.setName("Owner");
        owner.setEmail("shloimy@dynamiq.dev");
        when(clients.findById(7L)).thenReturn(Optional.of(owner));
        var preview = service.preview("operator@example.test", 7L,
                "Owner note", "Hello", null, false, true, null, null);

        AgentEmailService.SendResult result = service.send("operator@example.test", 7L,
                "Owner note", "Hello", null, false, true, null, preview.previewToken(), true, null);

        assertThat(preview.ccAddress()).isNull();
        assertThat(result.email().getCc()).isNull();
        verify(emailService).sendMessage(eq(sender(null)), eq("shloimy@dynamiq.dev"),
                eq(List.of()), eq(List.of()), eq("Owner note"), eq("Hello"), isNull(),
                eq(List.of()), eq(Map.of()));
    }

    @Test
    void omitsOversightCopyWhenOwnerIsTheSender() {
        var preview = service.preview("operator@example.test", 7L,
                "Owner note", "Hello", null, false, true, "shloimy@dynamiq.dev", null);

        service.send("operator@example.test", 7L,
                "Owner note", "Hello", null, false, true, "shloimy@dynamiq.dev",
                preview.previewToken(), true, null);

        assertThat(preview.ccAddress()).isNull();
        verify(emailService).sendMessage(eq(sender("shloimy@dynamiq.dev")), eq("billing@acme.test"),
                eq(List.of()), eq(List.of()), eq("Owner note"), eq("Hello"), isNull(),
                eq(List.of()), eq(Map.of()));
    }

    @Test
    void sendsTemplatedEmailWithoutSignature() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Notice", "Hello", "minimal", false, false, null, null);

        service.send("finance@dynamiq.dev", 7L, "Notice", "Hello",
                "minimal", false, false, null, preview.previewToken(), true, null);

        verify(emailService).sendMessage(eq(sender(null)), eq("billing@acme.test"),
                eq(List.of()), eq(List.of()), eq("Notice"), eq("Hello"),
                html.capture(), eq(List.of()), eq(Map.of()));
        assertThat(html.getValue()).contains("Hello")
                .doesNotContain("Dynamiq Solutions", "info@dynamiq.dev", "data-signature");
    }

    @Test
    void sendsSignedEmailWithoutTemplate() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Signed note", "Hello", null, true, false, null, null);

        service.send("finance@dynamiq.dev", 7L, "Signed note", "Hello",
                null, true, false, null, preview.previewToken(), true, null);

        verify(emailService).sendMessage(eq(sender(null)), eq("billing@acme.test"),
                eq(List.of()), eq(List.of()), eq("Signed note"), eq("Hello"),
                html.capture(), eq(List.of()), eq(Map.of()));
        assertThat(html.getValue()).contains("Hello", "info@dynamiq.dev", "data-signature=\"1\"")
                .doesNotContain("<!doctype html>");
    }

    @Test
    void refusesToSendWithoutExplicitApproval() {
        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Unapproved", "Hello", null, false, false, null, "preview", false, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Explicit approval");

        verify(clients, never()).findById(any());
        verify(emailService, never()).sendMessage(any(), anyString(), anyList(), anyList(), anyString(),
                anyString(), any(), anyList(), any());
    }

    @Test
    void usesSignatureMatchingSelectedSender() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Signed", "Hello", null, true, false, "finance@dynamiq.dev", null);

        service.send("finance@dynamiq.dev", 7L,
                "Signed", "Hello", null, true, false, "finance@dynamiq.dev",
                preview.previewToken(), true, null);

        verify(emailService).sendMessage(eq(sender("finance@dynamiq.dev")), eq("billing@acme.test"),
                eq(List.of()), eq(List.of()), eq("Signed"), eq("Hello"),
                html.capture(), eq(List.of()), eq(Map.of()));
        assertThat(html.getValue()).contains("<strong>Dynamiq Finance</strong>", "finance@dynamiq.dev",
                "data-signature=\"1\"");
    }

    @Test
    void refusesChangedContentAfterPreview() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false, false, null, null);

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Changed subject", "Approved body", null, false, false, null, preview.previewToken(), true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
        verify(emailService, never()).sendMessage(any(), anyString(), anyList(), anyList(), anyString(),
                anyString(), any(), anyList(), any());
    }

    @Test
    void refusesChangedOversightChoiceAfterPreview() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false, false, null, null);

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false, true, null,
                preview.previewToken(), true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
        verify(emailService, never()).sendMessage(any(), anyString(), anyList(), anyList(), anyString(),
                anyString(), any(), anyList(), any());
    }

    @Test
    void refusesChangedSenderAfterPreview() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false, false, "billing@dynamiq.dev", null);

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false, false,
                "finance@dynamiq.dev", preview.previewToken(), true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");

        verify(emailService, never()).sendMessage(any(), anyString(), anyList(), anyList(), anyString(),
                anyString(), any(), anyList(), any());
    }

    @Test
    void refusesPreviewWhenSelectedProfileSignatureChanges() {
        EmailSender initial = new EmailSender("info@dynamiq.dev", "Dynamiq Solutions", "<p>Initial signature</p>");
        EmailSender changed = new EmailSender("info@dynamiq.dev", "Dynamiq Solutions", "<p>Changed signature</p>");
        when(senders.resolve(any())).thenReturn(initial, changed);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, true, false, null, null);

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, true, false, null,
                preview.previewToken(), true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
        verify(emailService, never()).sendMessage(any(), anyString(), anyList(), anyList(), anyString(),
                anyString(), any(), anyList(), any());
    }

    @Test
    void sendsThreadedPreviewWithExactReplyHeaders() {
        EmailThreadService.ReplyContext reply = new EmailThreadService.ReplyContext(22L, 1L,
                "<parent@dynamiq.dev>", "<root@dynamiq.dev> <parent@dynamiq.dev>",
                "Re: Invoice update", "billing@acme.test", "support@dynamiq.dev");
        Email target = new Email();
        Client client = new Client();
        client.setId(7L);
        target.setClient(client);
        when(threads.replyContext(22L)).thenReturn(reply);
        when(emails.findById(22L)).thenReturn(Optional.of(target));
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Ignored when replying", "Reply body", null, false, true, "billing@dynamiq.dev", 22L);

        AgentEmailService.SendResult result = service.send("finance@dynamiq.dev", 7L,
                "Ignored when replying", "Reply body", null, false, true, "billing@dynamiq.dev",
                preview.previewToken(), true, 22L);

        verify(threads).prepareOutgoing(any(Email.class), eq(22L), isNull());
        verify(emailService).sendMessage(eq(sender("billing@dynamiq.dev")), eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()), eq("Re: Invoice update"),
                eq("Reply body"), isNull(), eq(List.of()),
                eq(Map.of("In-Reply-To", "<parent@dynamiq.dev>",
                        "References", "<root@dynamiq.dev> <parent@dynamiq.dev>")));
        assertThat(result.email().getMessageId()).isEqualTo("<message@dynamiq.dev>");
    }

    @Test
    void threadedReplyPreservesSavedRecipientsWithoutDuplicatingOversightCopy() {
        EmailThreadService.ReplyContext reply = new EmailThreadService.ReplyContext(22L, 1L,
                "<parent@dynamiq.dev>", "<parent@dynamiq.dev>", "Re: Invoice update",
                "billing@acme.test", "support@dynamiq.dev",
                "shloimy@dynamiq.dev, teammate@example.test", "archive@example.test");
        Email target = new Email();
        Client client = new Client();
        client.setId(7L);
        target.setClient(client);
        when(threads.replyContext(22L)).thenReturn(reply);
        when(emails.findById(22L)).thenReturn(Optional.of(target));
        var preview = service.preview("operator@example.test", 7L,
                "Ignored", "Reply body", null, false, true, "support@dynamiq.dev", 22L);

        AgentEmailService.SendResult result = service.send("operator@example.test", 7L,
                "Ignored", "Reply body", null, false, true, "support@dynamiq.dev",
                preview.previewToken(), true, 22L);

        assertThat(preview.ccAddress()).isEqualTo("shloimy@dynamiq.dev, teammate@example.test");
        assertThat(preview.bccAddress()).isEqualTo("archive@example.test");
        assertThat(result.email().getCc()).isEqualTo(preview.ccAddress());
        assertThat(result.email().getBcc()).isEqualTo(preview.bccAddress());
        verify(emailService).sendMessage(eq(sender("support@dynamiq.dev")), eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev", "teammate@example.test")),
                eq(List.of("archive@example.test")), eq("Re: Invoice update"), eq("Reply body"),
                isNull(), eq(List.of()), eq(Map.of("In-Reply-To", "<parent@dynamiq.dev>",
                        "References", "<parent@dynamiq.dev>")));
    }

    @Test
    void threadedReplyOptOutRemovesOversightAddressButKeepsOtherSavedRecipients() {
        EmailThreadService.ReplyContext reply = new EmailThreadService.ReplyContext(22L, 1L,
                "<parent@dynamiq.dev>", "<parent@dynamiq.dev>", "Re: Invoice update",
                "billing@acme.test", "support@dynamiq.dev",
                "shloimy@dynamiq.dev, teammate@example.test",
                "SHLOIMY@dynamiq.dev, archive@example.test");
        Email target = new Email();
        Client client = new Client();
        client.setId(7L);
        target.setClient(client);
        when(threads.replyContext(22L)).thenReturn(reply);
        when(emails.findById(22L)).thenReturn(Optional.of(target));
        var preview = service.preview("operator@example.test", 7L,
                "Ignored", "Reply body", null, false, false, "support@dynamiq.dev", 22L);

        AgentEmailService.SendResult result = service.send("operator@example.test", 7L,
                "Ignored", "Reply body", null, false, false, "support@dynamiq.dev",
                preview.previewToken(), true, 22L);

        assertThat(preview.ccAddress()).isEqualTo("teammate@example.test");
        assertThat(preview.bccAddress()).isEqualTo("archive@example.test");
        assertThat(result.email().getCc()).isEqualTo("teammate@example.test");
        assertThat(result.email().getBcc()).isEqualTo("archive@example.test");
        verify(emailService).sendMessage(eq(sender("support@dynamiq.dev")), eq("billing@acme.test"),
                eq(List.of("teammate@example.test")), eq(List.of("archive@example.test")),
                eq("Re: Invoice update"), eq("Reply body"), isNull(), eq(List.of()),
                eq(Map.of("In-Reply-To", "<parent@dynamiq.dev>",
                        "References", "<parent@dynamiq.dev>")));
    }

    @Test
    void legacyReplyUsesEditedSubjectAndOmitsUnavailableProviderHeaders() {
        EmailThreadService.ReplyContext reply = new EmailThreadService.ReplyContext(22L, 1L,
                null, null, "Re: Old invoice", "billing@acme.test", "support@dynamiq.dev");
        Email target = new Email();
        Client client = new Client();
        client.setId(7L);
        target.setClient(client);
        when(threads.replyContext(22L)).thenReturn(reply);
        when(emails.findById(22L)).thenReturn(Optional.of(target));

        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Edited subject", "Reply body", null, true, true, "billing@dynamiq.dev", 22L);
        service.send("finance@dynamiq.dev", 7L, "Edited subject", "Reply body", null,
                true, true, "billing@dynamiq.dev", preview.previewToken(), true, 22L);

        assertThat(preview.subject()).isEqualTo("Edited subject");
        assertThat(preview.inReplyTo()).isNull();
        assertThat(preview.referencesHeader()).isNull();
        verify(threads).prepareOutgoing(any(Email.class), eq(22L), isNull());
        verify(emailService).sendMessage(eq(sender("billing@dynamiq.dev")), eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()), eq("Edited subject"),
                eq("Reply body"), any(), eq(List.of()), eq(Map.of()));
    }

    @Test
    void refusesReplyForAnotherClientRecipient() {
        EmailThreadService.ReplyContext reply = new EmailThreadService.ReplyContext(22L, 22L,
                "<parent@dynamiq.dev>", "<root@dynamiq.dev> <parent@dynamiq.dev>",
                "Re: Invoice update", "other@example.test", "support@dynamiq.dev");
        when(threads.replyContext(22L)).thenReturn(reply);

        assertThatThrownBy(() -> service.preview("finance@dynamiq.dev", 7L,
                "Invoice update", "Hello", null, true, false, null, 22L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different recipient");
    }

    @Test
    void refusesReplyLinkedToAnotherClientEvenWhenAddressesMatch() {
        EmailThreadService.ReplyContext reply = new EmailThreadService.ReplyContext(22L, 22L,
                "<parent@dynamiq.dev>", "<parent@dynamiq.dev>", "Re: Invoice update",
                "billing@acme.test", "support@dynamiq.dev");
        Email target = new Email();
        Client anotherClient = new Client();
        anotherClient.setId(8L);
        target.setClient(anotherClient);
        when(threads.replyContext(22L)).thenReturn(reply);
        when(emails.findById(22L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.preview("finance@dynamiq.dev", 7L,
                "Invoice update", "Hello", null, true, false, null, 22L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different client");
    }

    private static EmailSender sender(String address) {
        String value = address == null ? "info@dynamiq.dev" : address;
        String name = switch (value) {
            case "billing@dynamiq.dev" -> "Dynamiq Billing";
            case "finance@dynamiq.dev" -> "Dynamiq Finance";
            case "support@dynamiq.dev" -> "Dynamiq Support";
            case "sales@dynamiq.dev" -> "Dynamiq Sales";
            default -> "Dynamiq Solutions";
        };
        return new EmailSender(value, name, EmailSender.basicSignatureHtml(value, name));
    }
}
