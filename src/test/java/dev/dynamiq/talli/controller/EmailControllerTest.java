package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailAttachmentPolicy;
import dev.dynamiq.talli.service.EmailService;
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

    @BeforeEach
    void setUp() {
        emailRepository = mock(EmailRepository.class);
        emailService = mock(EmailService.class);
        mediaService = mock(MediaService.class);
        controller = new EmailController(
                emailRepository,
                mock(ClientRepository.class),
                emailService,
                mock(UserRepository.class),
                mediaService,
                new EmailAttachmentPolicy("20MB", "25MB"));

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
        when(emailService.sendPlain(any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.Result("", "msg_123"));

        String view = controller.send(
                null, null, "to@example.com", "Files", "See attached",
                null, null, null, List.of(upload), new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/emails");

        ArgumentCaptor<Email> owner = ArgumentCaptor.forClass(Email.class);
        verify(mediaService).attach(owner.capture(), eq(upload), eq("attachments"));
        assertThat(owner.getValue().getId()).isEqualTo(99L);

        ArgumentCaptor<List> attachments = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendPlain(
                eq("to@example.com"), eq(List.of()), eq("Files"), eq("See attached"), attachments.capture());
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
                null, null, null, List.of(upload), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/emails");
        assertThat(redirectAttributes.getFlashAttributes().get("error").toString())
                .contains("large.pdf", "20 MB");
        verify(emailRepository, org.mockito.Mockito.never()).save(any());
        verify(emailService, org.mockito.Mockito.never())
                .sendPlain(any(), any(), any(), any(), any());
    }
}
