package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailAttachmentPolicy;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.service.EmailSender;
import dev.dynamiq.talli.service.EmailSenderProfileService;

import dev.dynamiq.talli.service.EmailThreadService;
import dev.dynamiq.talli.service.EmailTemplateCatalog;
import dev.dynamiq.talli.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailControllerTest {

    private EmailRepository emailRepository;
    private EmailService emailService;
    private MediaService mediaService;
    private EmailController controller;
    private EmailSenderProfileService senders;
    private EmailThreadService threads;
    private final EmailSender sender = new EmailSender("billing@dynamiq.dev", "Billing", "");

    @BeforeEach
    void setUp() {
        emailRepository = mock(EmailRepository.class);
        emailService = mock(EmailService.class);
        mediaService = mock(MediaService.class);
        senders = mock(EmailSenderProfileService.class);
        threads = mock(EmailThreadService.class);
        when(senders.resolve(null)).thenReturn(sender);
        controller = new EmailController(
                emailRepository,
                mock(ClientRepository.class),
                emailService,
                mock(UserRepository.class),
                mediaService,
                new EmailAttachmentPolicy("20MB", "25MB"),
                new EmailTemplateCatalog(senders), senders, threads, mock(dev.dynamiq.talli.service.MailboxService.class));

        when(emailRepository.save(any(Email.class))).thenAnswer(invocation -> {
            Email email = invocation.getArgument(0);
            if (email.getId() == null) email.setId(99L);
            return email;
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void storesAndSendsSelectedAttachments() {
        byte[] content = "attached content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile(
                "attachments", "notes.txt", "text/plain", content);
        Media stored = new Media();
        stored.setFilename("notes.txt");
        stored.setMimeType("text/plain");

        when(mediaService.attach(any(Email.class), eq(upload), eq("attachments"))).thenReturn(stored);
        when(mediaService.loadBytes(stored)).thenReturn(content);
        when(emailService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.Result("", "msg_123", null, sender.address()));

        String view = controller.send(
                null, null, "to@example.com", "Files", "See attached",
                null, null, null, null, null, List.of(upload), null, null, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/emails");

        ArgumentCaptor<Email> owner = ArgumentCaptor.forClass(Email.class);
        verify(mediaService).attach(owner.capture(), eq(upload), eq("attachments"));
        assertThat(owner.getValue().getId()).isEqualTo(99L);

        ArgumentCaptor<List> attachments = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendMessage(
                eq(sender),
                eq("to@example.com"), eq(List.of()), eq(List.of()),
                eq("Files"), eq("See attached"), eq(null), attachments.capture(), eq(java.util.Map.of()));
        EmailService.Attachment sent = (EmailService.Attachment) attachments.getValue().getFirst();
        assertThat(sent.filename()).isEqualTo("notes.txt");
        assertThat(sent.contentType()).isEqualTo("text/plain");
        assertThat(sent.content()).isEqualTo(content);

        ArgumentCaptor<Email> saved = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("sent");
        assertThat(saved.getValue().getResendId()).isEqualTo("msg_123");
    }

    @Test
    void rejectsOversizedAttachmentBeforeSavingOrSendingEmail() {
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getOriginalFilename()).thenReturn("large.pdf");
        when(upload.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.send(
                null, null, "to@example.com", "Files", "See attached",
                null, null, null, null, null, List.of(upload), null, null, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/emails");
        assertThat(redirectAttributes.getFlashAttributes().get("error").toString())
                .contains("large.pdf", "20 MB");
        verify(emailRepository, org.mockito.Mockito.never()).save(any());
        verify(emailService, org.mockito.Mockito.never())
                .sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void storesCcAndKeepsCcRecipientsOutOfBcc() {
        when(emailService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.Result("", "msg_123", null, sender.address()));

        controller.send(null, null, "to@example.com", "Hello", "Body", null,
                null, "cc@example.com, to@example.com",
                null, "bcc@example.com, cc@example.com",
                null, null, null, new RedirectAttributesModelMap());

        verify(emailService).sendMessage(
                eq(sender), eq("to@example.com"), eq(List.of("cc@example.com")),
                eq(List.of("bcc@example.com")), eq("Hello"), eq("Body"), eq(null), eq(List.of()), eq(java.util.Map.of()));
        ArgumentCaptor<Email> saved = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getCc()).isEqualTo("cc@example.com");
        assertThat(saved.getValue().getBcc()).isEqualTo("bcc@example.com");
    }

    @Test
    void repliesKeepConversationRecipientAndProviderHeaders() {
        Email parent = new Email();
        parent.setId(10L);
        when(emailRepository.findById(10L)).thenReturn(java.util.Optional.of(parent));
        when(threads.replyContext(10L)).thenReturn(new EmailThreadService.ReplyContext(
                10L, 1L, "<parent@example.com>", "<root@example.com> <parent@example.com>",
                "Re: Existing subject", "to@example.com", "billing@dynamiq.dev"));
        when(emailService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.Result("", "provider-123", "<reply@provider.test>", sender.address()));
        String view = controller.send(null, null, "to@example.com", "Changed subject", "Reply", null,
                null, null, null, null, null, null, 10L, new RedirectAttributesModelMap());
        assertThat(view).isEqualTo("redirect:/emails/99");
        verify(emailService).sendMessage(sender, "to@example.com", List.of(), List.of(),
                "Re: Existing subject", "Reply", null, List.of(),
                java.util.Map.of("In-Reply-To", "<parent@example.com>", "References", "<root@example.com> <parent@example.com>"));
        ArgumentCaptor<Email> saved = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getThreadRootId()).isEqualTo(1L);
        assertThat(saved.getValue().getMessageId()).isEqualTo("<reply@provider.test>");
    }

    @Test
    void rejectsChangedReplyRecipientBeforeSavingOrSending() {
        when(threads.replyContext(10L)).thenReturn(new EmailThreadService.ReplyContext(
                10L, 1L, "<parent@example.com>", "<parent@example.com>",
                "Re: Subject", "original@example.com", "billing@dynamiq.dev"));
        var flash = new RedirectAttributesModelMap();
        controller.send(null, null, "other@example.com", "Subject", "Reply", null,
                null, null, null, null, null, null, 10L, flash);
        assertThat(flash.getFlashAttributes().get("error")).isEqualTo("Reply recipient must match the original conversation.");
        verify(emailRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(emailService);
    }

    @Test
    void legacyReplyAcceptsEditedEnvelopeAndStaysInTheLocalConversation() {
        Email parent = new Email();
        parent.setId(10L);
        when(emailRepository.findById(10L)).thenReturn(java.util.Optional.of(parent));
        when(threads.replyContext(10L)).thenReturn(new EmailThreadService.ReplyContext(
                10L, 1L, null, null, "Re: Existing subject", "old@example.com",
                "billing@dynamiq.dev", "saved-cc@example.com", "saved-bcc@example.com"));
        when(emailService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.Result("", "provider-123", "<new@provider.test>", sender.address()));

        String view = controller.send(null, null, "updated@example.com", "Edited subject", "Reply", null,
                null, "edited-cc@example.com", null, "edited-bcc@example.com",
                null, null, 10L, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/emails/99");
        verify(emailService).sendMessage(sender, "updated@example.com", List.of("edited-cc@example.com"),
                List.of("edited-bcc@example.com"), "Edited subject", "Reply", null, List.of(), java.util.Map.of());
        ArgumentCaptor<Email> saved = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getThreadRootId()).isEqualTo(1L);
        assertThat(saved.getValue().getInReplyTo()).isNull();
        assertThat(saved.getValue().getReferencesHeader()).isNull();
    }

    @Test
    void selectedProfileSuppliesFallbackHtmlSignature() {
        EmailSender selected = new EmailSender("sales@dynamiq.dev", "Sales", "<p><b>Sales team</b><br>Call us</p>");
        when(senders.resolve("sales@dynamiq.dev")).thenReturn(selected);
        when(emailService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.Result("", "provider-123", null, "sales@dynamiq.dev"));
        controller.send(null, null, "to@example.com", "Hello", "Body", null,
                null, null, null, null, null, "sales@dynamiq.dev", null, new RedirectAttributesModelMap());
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendMessage(eq(selected), eq("to@example.com"), eq(List.of()), eq(List.of()),
                eq("Hello"), eq("Body"),
                html.capture(), eq(List.of()), eq(java.util.Map.of()));
        assertThat(html.getValue())
                .contains("font-family:Arial,Helvetica,sans-serif", "<div>Body</div>",
                        "<p><b>Sales team</b><br>Call us</p>");
    }
}
