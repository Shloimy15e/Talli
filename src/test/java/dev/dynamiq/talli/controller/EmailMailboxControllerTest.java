package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailAttachmentPolicy;
import dev.dynamiq.talli.service.EmailSenderProfileService;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.service.EmailTemplateCatalog;
import dev.dynamiq.talli.service.EmailThreadService;
import dev.dynamiq.talli.service.MailboxService;
import dev.dynamiq.talli.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ConcurrentModel;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailMailboxControllerTest {

    private EmailThreadService threads;
    private MediaService media;
    private MailboxService mailbox;
    private EmailController controller;
    private User user;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        threads = mock(EmailThreadService.class);
        media = mock(MediaService.class);
        mailbox = mock(MailboxService.class);
        var senders = mock(EmailSenderProfileService.class);
        controller = new EmailController(
                mock(EmailRepository.class), mock(ClientRepository.class), mock(EmailService.class),
                mock(UserRepository.class), media, new EmailAttachmentPolicy("20MB", "25MB"),
                new EmailTemplateCatalog(senders), senders, threads, mailbox);
        user = new User();
        user.setId(7L);
        user.setEmail("admin@example.test");
        authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null);
        when(mailbox.currentUser(authentication)).thenReturn(user);
    }

    @Test
    void conversationUsesTheSharedMailboxShellAndMapsAttachmentsPerMessage() {
        Email root = email(10L, "<root@example.test>");
        Email latest = email(12L, "<reply@example.test>");
        latest.setThreadRootId(10L);
        Media attachment = new Media();
        attachment.setId(3L);
        var view = new MailboxService.MailboxView(Page.empty(), counts(), "all", "", "");
        var conversation = new MailboxService.ConversationView(root, List.of(root, latest), 10L, true, false);
        when(mailbox.mailbox(user, "all", null, 0, null, null, null)).thenReturn(view);
        when(mailbox.conversation(user, 10L, true)).thenReturn(conversation);
        when(media.forOwner(root, "attachments")).thenReturn(List.of());
        when(media.forOwner(latest, "attachments")).thenReturn(List.of(attachment));
        when(threads.replyContext(10L)).thenThrow(new IllegalStateException("Unavailable"));
        when(threads.replyContext(12L)).thenReturn(new EmailThreadService.ReplyContext(
                12L, 10L, "<reply@example.test>", "<root@example.test> <reply@example.test>",
                "Re: Subject", "recipient@example.test", "sender@example.test"));
        var model = new ConcurrentModel();

        String template = controller.show(10L, 0, "all", null, null, null, null, authentication, model);

        assertThat(template).isEqualTo("emails/index");
        assertThat(model.getAttribute("selectedRootId")).isEqualTo(10L);
        assertThat(model.getAttribute("replyId")).isEqualTo(12L);
        Map<?, ?> attachments = (Map<?, ?>) model.getAttribute("attachmentsByEmailId");
        Map<?, ?> replyAvailability = (Map<?, ?>) model.getAttribute("canReplyByEmailId");
        assertThat(attachments.get(12L)).isEqualTo(List.of(attachment));
        assertThat(replyAvailability.get(10L)).isEqualTo(false);
        assertThat(replyAvailability.get(12L)).isEqualTo(true);
    }

    @Test
    void mailboxActionReturnsOnlyToAStaticLocalRouteWithEncodedState() {
        String redirect = controller.updateMailbox(12L, "star", "inbox", "customer + vip", null, 3,
                true, authentication);

        verify(mailbox).update(user, 12L, "star");
        assertThat(redirect).isEqualTo(
                "redirect:/emails/12?folder=inbox&page=3&search=customer%20%2B%20vip");
    }

    private static Email email(Long id, String messageId) {
        Email email = new Email();
        email.setId(id);
        email.setThreadRootId(id);
        email.setFromAddress("sender@example.test");
        email.setToAddress("recipient@example.test");
        email.setSubject("Subject");
        email.setBody("Body");
        email.setDirection("out");
        email.setStatus("sent");
        email.setMessageId(messageId);
        email.setCreatedAt(LocalDateTime.of(2026, 9, 15, 9, 0).plusMinutes(id));
        return email;
    }

    private static Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", 0L);
        counts.put("inbox", 0L);
        counts.put("sent", 0L);
        counts.put("starred", 0L);
        counts.put("archive", 0L);
        return counts;
    }
}
