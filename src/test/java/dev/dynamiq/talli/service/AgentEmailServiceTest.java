package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEmailServiceTest {

    private ClientRepository clients;
    private EmailRepository emails;
    private EmailService emailService;
    private AgentEmailService service;

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        emails = mock(EmailRepository.class);
        emailService = mock(EmailService.class);
        service = new AgentEmailService(clients, emails, emailService,
                new EmailTemplateCatalog("Talli Finance", "finance@dynamiq.dev"),
                new AgentEmailSenderCatalog(),
                "shloimy@dynamiq.dev");

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
        when(emailService.sendPlain(any(EmailSender.class), anyString(), anyList(), anyList(),
                anyString(), anyString()))
                .thenReturn(new EmailService.Result("", "msg-plain"));
        when(emailService.sendHtml(any(EmailSender.class), anyString(), anyList(), anyList(),
                anyString(), anyString(), anyString()))
                .thenReturn(new EmailService.Result("<html></html>", "msg-html"));
    }

    @Test
    void sendsTemplatedEmailWithSelectedSenderSignature() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Invoice update", "Amount < $100", "branded", true,
                "billing@dynamiq.dev");

        AgentEmailService.SendResult result = service.send("finance@dynamiq.dev", 7L,
                "Invoice update", "Amount < $100", "branded", true,
                "billing@dynamiq.dev", preview.previewToken(), true);

        verify(emailService).sendHtml(eq(new EmailSender("billing@dynamiq.dev", "Dynamiq Billing")),
                eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()),
                eq("Invoice update"), eq("Amount < $100"), html.capture());
        assertThat(html.getValue())
                .contains("billing@dynamiq.dev", "Amount &lt; $100")
                .contains("data-signature=\"1\"");
        assertThat(result.email().getStatus()).isEqualTo("sent");
        assertThat(result.templateId()).isEqualTo("branded");
        assertThat(result.signatureIncluded()).isTrue();
        assertThat(result.email().getFromAddress()).isEqualTo("billing@dynamiq.dev");
        assertThat(preview.fromName()).isEqualTo("Dynamiq Billing");
        assertThat(preview.ccAddress()).isEqualTo("shloimy@dynamiq.dev");
    }

    @Test
    void sendsPlainEmailWithoutTemplateOrSignature() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Quick note", "Hello", null, false, null);
        AgentEmailService.SendResult result = service.send("finance@dynamiq.dev", 7L,
                "Quick note", "Hello", null, false, null, preview.previewToken(), true);

        verify(emailService).sendPlain(new EmailSender("info@dynamiq.dev", "Dynamiq Solutions"),
                "billing@acme.test", List.of("shloimy@dynamiq.dev"), List.of(),
                "Quick note", "Hello");
        assertThat(result.email().getBodyHtml()).isNull();
        assertThat(result.templateId()).isNull();
        assertThat(result.signatureIncluded()).isFalse();
        assertThat(result.email().getFromAddress()).isEqualTo("info@dynamiq.dev");
    }

    @Test
    void sendsTemplatedEmailWithoutSignature() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Notice", "Hello", "minimal", false, null);

        service.send("finance@dynamiq.dev", 7L, "Notice", "Hello",
                "minimal", false, null, preview.previewToken(), true);

        verify(emailService).sendHtml(eq(new EmailSender("info@dynamiq.dev", "Dynamiq Solutions")),
                eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()),
                eq("Notice"), eq("Hello"), html.capture());
        assertThat(html.getValue()).contains("Talli Finance", "Hello")
                .doesNotContain("data-signature", "Automated finance assistant");
    }

    @Test
    void sendsSignedEmailWithoutTemplate() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Signed note", "Hello", null, true, null);

        service.send("finance@dynamiq.dev", 7L, "Signed note", "Hello",
                null, true, null, preview.previewToken(), true);

        verify(emailService).sendHtml(eq(new EmailSender("info@dynamiq.dev", "Dynamiq Solutions")),
                eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()),
                eq("Signed note"), eq("Hello"), html.capture());
        assertThat(html.getValue()).contains("Hello", "info@dynamiq.dev")
                .doesNotContain("<!doctype html>");
    }

    @Test
    void refusesToSendWithoutExplicitApproval() {
        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Unapproved", "Hello", null, false, null, "preview", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Explicit approval");

        verify(clients, never()).findById(any());
        verify(emailService, never()).sendPlain(any(EmailSender.class), anyString(), anyList(), anyList(),
                anyString(), anyString());
    }

    @Test
    void usesSignatureMatchingSelectedSender() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);

        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Signed", "Hello", null, true, "finance@dynamiq.dev");
        service.send("finance@dynamiq.dev", 7L,
                "Signed", "Hello", null, true, "finance@dynamiq.dev",
                preview.previewToken(), true);

        verify(emailService).sendHtml(eq(new EmailSender("finance@dynamiq.dev", "Dynamiq Finance")),
                eq("billing@acme.test"), eq(List.of("shloimy@dynamiq.dev")), eq(List.of()),
                eq("Signed"), eq("Hello"), html.capture());
        assertThat(html.getValue())
                .contains("<strong>Dynamiq</strong>", "finance@dynamiq.dev", "data-signature=\"1\"");
    }

    @Test
    void refusesChangedContentAfterPreview() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false, null);

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Changed subject", "Approved body", null, false,
                null, preview.previewToken(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");

        verify(emailService, never()).sendPlain(any(EmailSender.class), anyString(), anyList(), anyList(),
                anyString(), anyString());
    }

    @Test
    void refusesChangedSenderAfterPreview() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false,
                "billing@dynamiq.dev");

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false,
                "finance@dynamiq.dev", preview.previewToken(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");

        verify(emailService, never()).sendPlain(any(EmailSender.class), anyString(), anyList(), anyList(),
                anyString(), anyString());
    }

}
