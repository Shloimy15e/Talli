package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.EmailMailboxState;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.EmailMailboxStateRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailboxServiceTest {

    private EmailRepository emails;
    private EmailMailboxStateRepository states;
    private EmailThreadService threads;
    private MailboxService mailbox;
    private User user;

    @BeforeEach
    void setUp() {
        emails = mock(EmailRepository.class);
        states = mock(EmailMailboxStateRepository.class);
        threads = mock(EmailThreadService.class);
        mailbox = new MailboxService(emails, states, mock(UserRepository.class), threads);
        user = new User();
        user.setId(7L);
    }

    @Test
    void listsOneConversationRowWithUnreadStateFromTheLatestInboundMessage() {
        Email latest = email(12L, 10L, "in", "A useful subject", "The newest message");
        EmailMailboxState state = state(10L, 11L, 11L, true);
        var counts = mock(EmailRepository.MailboxCountsProjection.class);

        when(emails.findMailboxConversations(eq(7L), eq("all"), eq(""), anyList(), eq(true),
                eq("useful"), any())).thenReturn(new PageImpl<>(List.of(latest)));
        when(emails.summarizeConversations(List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 3L, 12L}));
        when(states.findByUserIdAndThreadRootIdIn(7L, List.of(10L))).thenReturn(List.of(state));
        when(emails.countMailboxFolders(eq(7L), eq(""), anyList(), eq(true), eq("useful")))
                .thenReturn(counts);
        when(counts.getAllCount()).thenReturn(4L);
        when(counts.getInboxCount()).thenReturn(2L);
        when(counts.getSentCount()).thenReturn(3L);
        when(counts.getStarredCount()).thenReturn(1L);
        when(counts.getArchiveCount()).thenReturn(1L);

        var result = mailbox.mailbox(user, "all", " useful ", 0, null, null);

        assertThat(result.folderCounts()).containsExactly(
                org.assertj.core.api.Assertions.entry("all", 4L),
                org.assertj.core.api.Assertions.entry("inbox", 2L),
                org.assertj.core.api.Assertions.entry("sent", 3L),
                org.assertj.core.api.Assertions.entry("starred", 1L),
                org.assertj.core.api.Assertions.entry("archive", 1L));
        assertThat(result.mailRows().getContent()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(12L);
            assertThat(row.rootId()).isEqualTo(10L);
            assertThat(row.messageCount()).isEqualTo(3);
            assertThat(row.unread()).isTrue();
            assertThat(row.starred()).isTrue();
        });
    }

    @Test
    void openingConversationReadsThroughLatestAndNewInboundMailResurfacesArchive() {
        Email root = email(10L, 10L, "out", "Subject", "Original");
        Email inbound = email(12L, 10L, "in", "Re: Subject", "New reply");
        EmailMailboxState state = state(10L, 10L, 10L, false);
        when(emails.findById(10L)).thenReturn(Optional.of(root));
        when(threads.conversation(root)).thenReturn(List.of(root, inbound));
        when(states.findByUserIdAndThreadRootId(7L, 10L)).thenReturn(Optional.of(state));
        when(states.save(state)).thenReturn(state);

        var result = mailbox.conversation(user, 10L, true);

        assertThat(state.getLastReadEmailId()).isEqualTo(12L);
        assertThat(result.archived()).isFalse();
        assertThat(result.rootId()).isEqualTo(10L);
        verify(states).save(state);
    }

    @Test
    void archiveActionStoresTheLatestConversationIdForOnlyThatUserAndRoot() {
        Email root = email(10L, 10L, "out", "Subject", "Original");
        Email latest = email(15L, 10L, "in", "Re: Subject", "Reply");
        when(emails.findById(10L)).thenReturn(Optional.of(root));
        when(threads.conversation(root)).thenReturn(List.of(root, latest));
        when(states.findByUserIdAndThreadRootId(7L, 10L)).thenReturn(Optional.empty());
        when(states.save(any(EmailMailboxState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mailbox.update(user, 10L, "archive");

        var saved = org.mockito.ArgumentCaptor.forClass(EmailMailboxState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getThreadRootId()).isEqualTo(10L);
        assertThat(saved.getValue().getArchivedThroughEmailId()).isEqualTo(15L);
    }

    @Test
    void outboundOnlyConversationCanBeMarkedUnreadAndOpeningItClearsTheMarker() {
        Email outbound = email(10L, 10L, "out", "Subject", "Original");
        when(emails.findById(10L)).thenReturn(Optional.of(outbound));
        when(threads.conversation(outbound)).thenReturn(List.of(outbound));
        when(states.findByUserIdAndThreadRootId(7L, 10L))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(markedUnreadState(10L)));
        when(states.save(any(EmailMailboxState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mailbox.update(user, 10L, "unread");

        var saved = org.mockito.ArgumentCaptor.forClass(EmailMailboxState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().isMarkedUnread()).isTrue();

        EmailMailboxState markedUnread = markedUnreadState(10L);
        when(states.findByUserIdAndThreadRootId(7L, 10L)).thenReturn(Optional.of(markedUnread));
        mailbox.conversation(user, 10L, true);
        assertThat(markedUnread.isMarkedUnread()).isFalse();
        assertThat(markedUnread.getLastReadEmailId()).isEqualTo(10L);
    }

    @Test
    void openingAHiddenReturningCopyUsesItsCanonicalConversation() {
        Email outgoing = email(10L, 10L, "out", "Subject", "Original");
        Email returningCopy = email(20L, 10L, "in", "Subject", "Returning copy");
        returningCopy.setCopyOfEmailId(outgoing.getId());
        when(emails.findById(20L)).thenReturn(Optional.of(returningCopy));
        when(emails.findById(10L)).thenReturn(Optional.of(outgoing));
        when(threads.conversation(outgoing)).thenReturn(List.of(outgoing));
        when(states.findByUserIdAndThreadRootId(7L, 10L)).thenReturn(Optional.empty());

        var conversation = mailbox.conversation(user, 20L, false);

        assertThat(conversation.selectedEmail()).isSameAs(outgoing);
        assertThat(conversation.conversation()).containsExactly(outgoing);
    }

    private EmailMailboxState markedUnreadState(Long rootId) {
        EmailMailboxState state = state(rootId, null, null, false);
        state.setMarkedUnread(true);
        return state;
    }

    private EmailMailboxState state(Long rootId, Long readThrough, Long archivedThrough, boolean starred) {
        EmailMailboxState state = new EmailMailboxState();
        state.setUser(user);
        state.setThreadRootId(rootId);
        state.setLastReadEmailId(readThrough);
        state.setArchivedThroughEmailId(archivedThrough);
        state.setStarred(starred);
        return state;
    }

    private static Email email(Long id, Long rootId, String direction, String subject, String body) {
        Email email = new Email();
        email.setId(id);
        email.setThreadRootId(rootId);
        email.setDirection(direction);
        email.setFromAddress("sender@example.test");
        email.setToAddress("recipient@example.test");
        email.setSubject(subject);
        email.setBody(body);
        email.setStatus("in".equals(direction) ? "received" : "sent");
        email.setCreatedAt(LocalDateTime.of(2026, 9, 15, 9, 0).plusMinutes(id));
        return email;
    }
}
