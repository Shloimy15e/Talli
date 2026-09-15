package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UnreadMailNotificationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final EmailRepository emails = mock(EmailRepository.class);
    private final EmailService delivery = mock(EmailService.class);
    private final UnreadMailNotificationService notifications =
            new UnreadMailNotificationService(users, emails, delivery, "https://talli.example.test/");

    @Test
    void sendsOneCountAndInboxLinkPerExternalAdminWithUnreadMail() {
        when(users.findEnabledByRoleName("admin")).thenReturn(List.of(
                admin(1L, "owner@example.test"), admin(2L, "second@example.test"),
                admin(3L, "empty@example.test"), admin(4L, "Shloimy@DYNAMIQ.DEV"),
                admin(5L, "admin@mail.dynamiq.dev")));
        when(emails.countUnreadInboxConversations(1L)).thenReturn(2L);
        when(emails.countUnreadInboxConversations(2L)).thenReturn(1L);

        notifications.notifyUnreadAdmins();

        verify(delivery).sendPlain("owner@example.test", List.of(), "Talli: 2 unread conversations",
                "You have 2 unread conversations in your Talli inbox.\n\n"
                        + "Open your inbox: https://talli.example.test/emails?folder=inbox");
        verify(delivery).sendPlain("second@example.test", List.of(), "Talli: 1 unread conversation",
                "You have 1 unread conversation in your Talli inbox.\n\n"
                        + "Open your inbox: https://talli.example.test/emails?folder=inbox");
        verifyNoMoreInteractions(delivery);
        verify(emails, never()).countUnreadInboxConversations(4L);
        verify(emails, never()).countUnreadInboxConversations(5L);
    }

    @Test
    void failureForOneAdminDoesNotPreventTheNextNotification() {
        when(users.findEnabledByRoleName("admin")).thenReturn(List.of(
                admin(1L, "first@example.test"), admin(2L, "second@example.test")));
        when(emails.countUnreadInboxConversations(anyLong())).thenReturn(1L);
        when(delivery.sendPlain(eq("first@example.test"), anyList(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Provider unavailable"));

        notifications.notifyUnreadAdmins();

        verify(delivery).sendPlain(eq("second@example.test"), eq(List.of()), anyString(), anyString());
    }

    @Test
    void jobUsesFiveAmUtcEveryDay() throws Exception {
        Scheduled schedule = ScheduledJobs.class.getMethod("notifyUnreadMail").getAnnotation(Scheduled.class);
        assertThat(schedule.cron()).isEqualTo("0 0 5 * * *");
        assertThat(schedule.zone()).isEqualTo("UTC");
    }

    private static User admin(Long id, String address) {
        User user = new User();
        user.setId(id);
        user.setEmail(address);
        return user;
    }
}
