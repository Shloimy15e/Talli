package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.EmailMailboxState;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RefreshDatabaseTest
class EmailMailboxRepositoryTest {

    @Autowired
    private EmailRepository emails;

    @Autowired
    private EmailMailboxStateRepository states;

    @Autowired
    private UserRepository users;

    @Test
    void groupedQueryReturnsLatestMessageAndNewInboundMailResurfacesAnArchive() {
        User user = new User();
        user.setEmail("mailbox@example.test");
        user.setName("Mailbox User");
        user.setPassword("not-used-in-this-test");
        user = users.saveAndFlush(user);

        Email root = email("out", "First subject", "search needle in the original", "sent");
        root = emails.saveAndFlush(root);
        root.setThreadRootId(root.getId());
        root = emails.saveAndFlush(root);

        EmailMailboxState state = new EmailMailboxState();
        state.setUser(user);
        state.setThreadRootId(root.getId());
        state.setLastReadEmailId(root.getId());
        state.setArchivedThroughEmailId(root.getId());
        state.setStarred(true);
        states.saveAndFlush(state);

        Email reply = email("in", "Re: First subject", "A newer inbound reply", "received");
        reply.setThreadRootId(root.getId());
        reply = emails.saveAndFlush(reply);

        var inbox = emails.findMailboxConversations(user.getId(), "inbox", "",
                List.of("__none__"), true, "", PageRequest.of(0, 25));
        var search = emails.findMailboxConversations(user.getId(), "all", "",
                List.of("__none__"), true, "needle", PageRequest.of(0, 25));
        var counts = emails.countMailboxFolders(user.getId(), "", List.of("__none__"), true, "");

        assertThat(inbox.getContent()).extracting(Email::getId).containsExactly(reply.getId());
        assertThat(search.getContent()).extracting(Email::getId).containsExactly(reply.getId());
        assertThat(counts.getAllCount()).isEqualTo(1);
        assertThat(counts.getInboxCount()).isEqualTo(1);
        assertThat(counts.getSentCount()).isEqualTo(1);
        assertThat(counts.getStarredCount()).isEqualTo(1);
        assertThat(counts.getArchiveCount()).isZero();
    }

    @Test
    void unreadCountUsesLatestInboundReadStateAndMarkedUnreadOverride() {
        User user = user("read-state@example.test");
        Email root = threadRoot("in", "Read state");
        Email inbound = reply(root, "in", "Re: Read state");

        assertThat(emails.countUnreadInboxConversations(user.getId())).isEqualTo(1);

        EmailMailboxState state = state(user, root.getId());
        state.setLastReadEmailId(inbound.getId());
        states.saveAndFlush(state);
        assertThat(emails.countUnreadInboxConversations(user.getId())).isZero();

        state.setMarkedUnread(true);
        states.saveAndFlush(state);
        assertThat(emails.countUnreadInboxConversations(user.getId())).isEqualTo(1);
    }

    @Test
    void unreadCountIsPerUserAndCountsAThreadOnceAcrossMultipleInboundMessages() {
        User firstUser = user("first@example.test");
        User secondUser = user("second@example.test");
        Email root = threadRoot("in", "Shared thread");
        Email latestInbound = reply(root, "in", "Re: Shared thread");

        EmailMailboxState secondUserState = state(secondUser, root.getId());
        secondUserState.setLastReadEmailId(latestInbound.getId());
        states.saveAndFlush(secondUserState);

        assertThat(emails.countUnreadInboxConversations(firstUser.getId())).isEqualTo(1);
        assertThat(emails.countUnreadInboxConversations(secondUser.getId())).isZero();
    }

    @Test
    void unreadCountExcludesArchivedThreadsUntilNewInboundMailAndNeverCountsOutboundOnlyThreads() {
        User user = user("archive@example.test");
        Email root = threadRoot("in", "Archived thread");
        EmailMailboxState archived = state(user, root.getId());
        archived.setArchivedThroughEmailId(root.getId());
        archived.setMarkedUnread(true);
        states.saveAndFlush(archived);

        Email outboundOnly = threadRoot("out", "Outbound only");
        EmailMailboxState outboundState = state(user, outboundOnly.getId());
        outboundState.setMarkedUnread(true);
        states.saveAndFlush(outboundState);

        assertThat(emails.countUnreadInboxConversations(user.getId())).isZero();

        reply(root, "in", "Re: Archived thread");
        assertThat(emails.countUnreadInboxConversations(user.getId())).isEqualTo(1);
    }

    @Test
    void returningCopiesAreHiddenFromConversationsMailboxCountsAndUnread() {
        User user = user("returning-copy@example.test");
        Email outgoing = threadRoot("out", "Invoice sent");
        Email returningCopy = reply(outgoing, "in", "Invoice sent");
        returningCopy.setCopyOfEmailId(outgoing.getId());
        emails.saveAndFlush(returningCopy);

        var inbox = emails.findMailboxConversations(user.getId(), "inbox", "",
                List.of("__none__"), true, "", PageRequest.of(0, 25));
        var counts = emails.countMailboxFolders(user.getId(), "", List.of("__none__"), true, "");

        assertThat(emails.findConversation(outgoing.getId())).extracting(Email::getId)
                .containsExactly(outgoing.getId());
        assertThat(inbox.getContent()).isEmpty();
        assertThat(counts.getAllCount()).isEqualTo(1);
        assertThat(counts.getInboxCount()).isZero();
        assertThat(counts.getSentCount()).isEqualTo(1);
        assertThat(emails.countUnreadInboxConversations(user.getId())).isZero();
    }

    private User user(String address) {
        User user = new User();
        user.setEmail(address);
        user.setName(address);
        user.setPassword("not-used-in-this-test");
        return users.saveAndFlush(user);
    }

    private Email threadRoot(String direction, String subject) {
        Email root = email(direction, subject, "body", "in".equals(direction) ? "received" : "sent");
        root = emails.saveAndFlush(root);
        root.setThreadRootId(root.getId());
        return emails.saveAndFlush(root);
    }

    private Email reply(Email root, String direction, String subject) {
        Email reply = email(direction, subject, "body", "in".equals(direction) ? "received" : "sent");
        reply.setThreadRootId(root.getId());
        return emails.saveAndFlush(reply);
    }

    private EmailMailboxState state(User user, Long rootId) {
        EmailMailboxState state = new EmailMailboxState();
        state.setUser(user);
        state.setThreadRootId(rootId);
        return state;
    }

    private static Email email(String direction, String subject, String body, String status) {
        Email email = new Email();
        email.setDirection(direction);
        email.setFromAddress("sender@example.test");
        email.setToAddress("recipient@example.test");
        email.setSubject(subject);
        email.setBody(body);
        email.setStatus(status);
        return email;
    }
}
