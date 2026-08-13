package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
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
    private UserRepository users;
    private EmailRepository emails;
    private EmailService emailService;
    private AgentEmailService service;

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        users = mock(UserRepository.class);
        emails = mock(EmailRepository.class);
        emailService = mock(EmailService.class);
        service = new AgentEmailService(clients, users, emails, emailService,
                new EmailTemplateCatalog("Talli Finance", "finance@dynamiq.dev"),
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
        when(emailService.sendPlain(anyString(), anyList(), anyList(), anyString(), anyString()))
                .thenReturn(new EmailService.Result("", "msg-plain"));
        when(emailService.sendHtml(anyString(), anyList(), anyList(),
                anyString(), anyString(), anyString()))
                .thenReturn(new EmailService.Result("<html></html>", "msg-html"));
    }

    @Test
    void sendsTemplatedEmailWithAgentSignature() {
        configuredSignature();
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Invoice update", "Amount < $100", "branded", true);

        AgentEmailService.SendResult result = service.send("finance@dynamiq.dev", 7L,
                "Invoice update", "Amount < $100", "branded", true,
                preview.previewToken(), true);

        verify(emailService).sendHtml(eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()),
                eq("Invoice update"), eq("Amount < $100"), html.capture());
        assertThat(html.getValue())
                .contains("Talli Finance", "Amount &lt; $100", "Automated finance assistant")
                .contains("data-signature=\"1\"");
        assertThat(result.email().getStatus()).isEqualTo("sent");
        assertThat(result.templateId()).isEqualTo("branded");
        assertThat(result.signatureIncluded()).isTrue();
        assertThat(preview.ccAddress()).isEqualTo("shloimy@dynamiq.dev");
    }

    @Test
    void sendsPlainEmailWithoutTemplateOrSignature() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Quick note", "Hello", null, false);
        AgentEmailService.SendResult result = service.send("finance@dynamiq.dev", 7L,
                "Quick note", "Hello", null, false, preview.previewToken(), true);

        verify(emailService).sendPlain("billing@acme.test", List.of("shloimy@dynamiq.dev"),
                List.of(), "Quick note", "Hello");
        verify(users, never()).findByEmail(any());
        assertThat(result.email().getBodyHtml()).isNull();
        assertThat(result.templateId()).isNull();
        assertThat(result.signatureIncluded()).isFalse();
    }

    @Test
    void sendsTemplatedEmailWithoutSignature() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Notice", "Hello", "minimal", false);

        service.send("finance@dynamiq.dev", 7L, "Notice", "Hello",
                "minimal", false, preview.previewToken(), true);

        verify(emailService).sendHtml(eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()),
                eq("Notice"), eq("Hello"), html.capture());
        assertThat(html.getValue()).contains("Talli Finance", "Hello")
                .doesNotContain("data-signature", "Automated finance assistant");
    }

    @Test
    void sendsSignedEmailWithoutTemplate() {
        configuredSignature();
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Signed note", "Hello", null, true);

        service.send("finance@dynamiq.dev", 7L, "Signed note", "Hello",
                null, true, preview.previewToken(), true);

        verify(emailService).sendHtml(eq("billing@acme.test"),
                eq(List.of("shloimy@dynamiq.dev")), eq(List.of()),
                eq("Signed note"), eq("Hello"), html.capture());
        assertThat(html.getValue()).contains("Hello", "Automated finance assistant")
                .doesNotContain("<!doctype html>");
    }

    @Test
    void refusesToSendWithoutExplicitApproval() {
        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Unapproved", "Hello", null, false, "preview", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Explicit approval");

        verify(clients, never()).findById(any());
        verify(emailService, never()).sendPlain(anyString(), anyList(), anyList(),
                anyString(), anyString());
    }

    @Test
    void refusesSignedEmailWhenAgentSignatureIsNotConfigured() {
        User agent = new User();
        agent.setEmail("finance@dynamiq.dev");
        when(users.findByEmail("finance@dynamiq.dev")).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Signed", "Hello", null, true, "preview", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signature is not configured");

        verify(emailService, never()).sendHtml(anyString(), anyList(), anyList(),
                anyString(), anyString(), anyString());
    }

    @Test
    void refusesChangedContentAfterPreview() {
        var preview = service.preview("finance@dynamiq.dev", 7L,
                "Approved subject", "Approved body", null, false);

        assertThatThrownBy(() -> service.send("finance@dynamiq.dev", 7L,
                "Changed subject", "Approved body", null, false,
                preview.previewToken(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");

        verify(emailService, never()).sendPlain(anyString(), anyList(), anyList(),
                anyString(), anyString());
    }

    private void configuredSignature() {
        User agent = new User();
        agent.setEmail("finance@dynamiq.dev");
        agent.setSignature("<strong>Talli Finance</strong><br>Automated finance assistant by Dynamiq");
        when(users.findByEmail("finance@dynamiq.dev")).thenReturn(Optional.of(agent));
    }
}
