package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.EmailRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailThreadServiceTest {

    @Test
    void replyContextUsesParentMessageIdAndKeepsRootAndDirectParentReferences() {
        EmailRepository emails = mock(EmailRepository.class);
        Email parent = email(9L, "in", "client@example.test", "support@dynamiq.dev",
                "Re: Billing question", "<parent@dynamiq.dev>");
        parent.setThreadRootId(1L);
        parent.setReferencesHeader("<root@dynamiq.dev>");
        when(emails.findById(9L)).thenReturn(Optional.of(parent));

        var context = new EmailThreadService(emails).replyContext(9L);

        assertThat(context.threadRootId()).isEqualTo(1L);
        assertThat(context.inReplyTo()).isEqualTo("<parent@dynamiq.dev>");
        assertThat(context.referencesHeader()).isEqualTo("<root@dynamiq.dev> <parent@dynamiq.dev>");
        assertThat(context.subject()).isEqualTo("Re: Billing question");
        assertThat(context.recipientAddress()).isEqualTo("client@example.test");
        assertThat(context.senderAddressHint()).isEqualTo("support@dynamiq.dev");
        assertThat(context.providerThreaded()).isTrue();
    }

    @Test
    void legacyInboundReplyKeepsLocalThreadAndSavedEnvelopeWithoutProviderHeaders() {
        EmailRepository emails = mock(EmailRepository.class);
        Email parent = email(9L, "in", "client@example.test", "support@dynamiq.dev",
                "Old question", null);
        parent.setThreadRootId(1L);
        parent.setCc("support@dynamiq.dev, teammate@example.test, client@example.test");
        parent.setBcc("archive@example.test");
        when(emails.findById(9L)).thenReturn(Optional.of(parent));

        EmailThreadService service = new EmailThreadService(emails);
        var context = service.replyContext(9L);

        assertThat(context.providerThreaded()).isFalse();
        assertThat(context.inReplyTo()).isNull();
        assertThat(context.referencesHeader()).isNull();
        assertThat(context.subject()).isEqualTo("Old question");
        assertThat(context.recipientAddress()).isEqualTo("client@example.test");
        assertThat(context.senderAddressHint()).isEqualTo("support@dynamiq.dev");
        assertThat(context.ccAddresses()).isEqualTo("teammate@example.test");
        assertThat(context.bccAddresses()).isEqualTo("archive@example.test");

        Email outgoing = email(10L, "out", "support@dynamiq.dev", "client@example.test",
                "Re: Old question", null);
        service.prepareOutgoing(outgoing, 9L, null);
        assertThat(outgoing.getThreadRootId()).isEqualTo(1L);
        assertThat(outgoing.getInReplyTo()).isNull();
        assertThat(outgoing.getReferencesHeader()).isNull();
    }

    @Test
    void replyContextCanonicalizesAHiddenReturningCopy() {
        EmailRepository emails = mock(EmailRepository.class);
        Email outgoing = email(10L, "out", "billing@dynamiq.dev", "client@example.test",
                "Invoice", "<invoice@dynamiq.dev>");
        outgoing.setThreadRootId(10L);
        Email returningCopy = email(11L, "in", "billing@dynamiq.dev", "owner@example.test",
                "Invoice", "<invoice@dynamiq.dev>");
        returningCopy.setThreadRootId(10L);
        returningCopy.setCopyOfEmailId(10L);
        when(emails.findById(11L)).thenReturn(Optional.of(returningCopy));
        when(emails.findById(10L)).thenReturn(Optional.of(outgoing));

        var context = new EmailThreadService(emails).replyContext(11L);

        assertThat(context.replyToEmailId()).isEqualTo(10L);
        assertThat(context.threadRootId()).isEqualTo(10L);
        assertThat(context.recipientAddress()).isEqualTo("client@example.test");
        assertThat(context.inReplyTo()).isEqualTo("<invoice@dynamiq.dev>");
    }

    @Test
    void inboundPrefersInReplyToBeforeReferencesWhenBothAreKnown() {
        EmailRepository emails = mock(EmailRepository.class);
        Email directParent = email(8L, "out", "support@dynamiq.dev", "client@example.test",
                "Question", "<parent@dynamiq.dev>");
        directParent.setThreadRootId(1L);
        Email olderReference = email(3L, "out", "support@dynamiq.dev", "client@example.test",
                "Question", "<old@dynamiq.dev>");
        olderReference.setThreadRootId(3L);
        when(emails.findFirstByMessageIdAndCopyOfEmailIdIsNullOrderByIdAsc("<parent@dynamiq.dev>"))
                .thenReturn(Optional.of(directParent));

        Email incoming = new Email();
        new EmailThreadService(emails).prepareInbound(incoming, "<reply@client.test>",
                "<parent@dynamiq.dev>", "<old@dynamiq.dev> <parent@dynamiq.dev>");

        assertThat(incoming.getThreadRootId()).isEqualTo(1L);
        assertThat(incoming.getMessageId()).isEqualTo("<reply@client.test>");
    }

    @Test
    void conversationReturnsOnlyLegacyEmailWhenNoThreadRootWasRecorded() {
        EmailRepository emails = mock(EmailRepository.class);
        Email legacy = email(1L, "out", "support@dynamiq.dev", "client@example.test", "Hello", null);
        when(emails.findConversation(1L)).thenReturn(List.of());

        assertThat(new EmailThreadService(emails).conversation(legacy)).containsExactly(legacy);
    }

    @Test
    void conversationIncludesHistoricalRootWhoseThreadRootWasNeverBackfilled() {
        EmailRepository emails = mock(EmailRepository.class);
        Email root = email(1L, "out", "support@dynamiq.dev", "client@example.test", "Original", "<root@dynamiq.dev>");
        Email reply = email(2L, "in", "client@example.test", "support@dynamiq.dev", "Re: Original", "<reply@client.test>");
        reply.setThreadRootId(1L);
        when(emails.findConversation(1L)).thenReturn(List.of(root, reply));

        assertThat(new EmailThreadService(emails).conversation(reply)).containsExactly(root, reply);
    }

    @Test
    void reconcilingAnOutgoingMessageHidesReturningCopyAndRelinksRealReplyBranch() {
        EmailRepository emails = mock(EmailRepository.class);
        Email outgoing = email(10L, "out", "billing@dynamiq.dev", "client@example.test",
                "Invoice", "<outgoing@dynamiq.dev>");
        outgoing.setThreadRootId(10L);
        Email returningCopy = email(11L, "in", "owner@example.test", "billing@dynamiq.dev",
                "Invoice", "<outgoing@dynamiq.dev>");
        returningCopy.setThreadRootId(11L);
        Email realReply = email(12L, "in", "client@example.test", "billing@dynamiq.dev",
                "Re: Invoice", "<reply@client.test>");
        realReply.setInReplyTo("<outgoing@dynamiq.dev>");
        realReply.setThreadRootId(12L);

        when(emails.findByMessageIdAndDirectionAndCopyOfEmailIdIsNull("<outgoing@dynamiq.dev>", "in"))
                .thenReturn(List.of(returningCopy));
        when(emails.findByInReplyToAndCopyOfEmailIdIsNull("<outgoing@dynamiq.dev>"))
                .thenReturn(List.of(realReply));
        when(emails.findConversation(12L)).thenReturn(List.of(realReply));

        new EmailThreadService(emails).reconcileAfterMessageId(outgoing);

        assertThat(returningCopy.getCopyOfEmailId()).isEqualTo(10L);
        assertThat(returningCopy.getThreadRootId()).isEqualTo(10L);
        assertThat(realReply.getThreadRootId()).isEqualTo(10L);
        verify(emails).saveAll(List.of(returningCopy));
        verify(emails).saveAll(List.of(realReply));
    }

    @Test
    void prepareOutgoingImmediatelyReconcilesWhenTheProviderMessageIdIsKnown() {
        EmailRepository emails = mock(EmailRepository.class);
        Email outgoing = email(10L, "out", "billing@dynamiq.dev", "client@example.test",
                "Invoice", null);
        when(emails.save(outgoing)).thenReturn(outgoing);
        when(emails.findByMessageIdAndDirectionAndCopyOfEmailIdIsNull("<outgoing@dynamiq.dev>", "in"))
                .thenReturn(List.of());
        when(emails.findByInReplyToAndCopyOfEmailIdIsNull("<outgoing@dynamiq.dev>"))
                .thenReturn(List.of());

        new EmailThreadService(emails).prepareOutgoing(outgoing, null, "<outgoing@dynamiq.dev>");

        assertThat(outgoing.getMessageId()).isEqualTo("<outgoing@dynamiq.dev>");
        verify(emails).save(outgoing);
    }

    @Test
    void prepareOutgoingAssignsAReplyRootBeforeSavingAndReconciling() {
        EmailRepository emails = mock(EmailRepository.class);
        Email parent = email(3L, "in", "client@example.test", "billing@dynamiq.dev",
                "Original", "<parent@dynamiq.dev>");
        parent.setThreadRootId(1L);
        Email outgoing = email(10L, "out", "billing@dynamiq.dev", "client@example.test",
                "Re: Original", null);
        when(emails.findById(3L)).thenReturn(Optional.of(parent));
        when(emails.save(outgoing)).thenAnswer(invocation -> {
            assertThat(outgoing.getThreadRootId()).isEqualTo(1L);
            assertThat(outgoing.getInReplyTo()).isEqualTo("<parent@dynamiq.dev>");
            return outgoing;
        });
        when(emails.findByMessageIdAndDirectionAndCopyOfEmailIdIsNull("<outgoing@dynamiq.dev>", "in"))
                .thenReturn(List.of());
        when(emails.findByInReplyToAndCopyOfEmailIdIsNull("<outgoing@dynamiq.dev>"))
                .thenReturn(List.of());

        new EmailThreadService(emails).prepareOutgoing(outgoing, 3L, "<outgoing@dynamiq.dev>");

        assertThat(outgoing.getThreadRootId()).isEqualTo(1L);
        verify(emails).save(outgoing);
    }

    private static Email email(Long id, String direction, String from, String to, String subject, String messageId) {
        Email email = new Email();
        email.setId(id);
        email.setDirection(direction);
        email.setFromAddress(from);
        email.setToAddress(to);
        email.setSubject(subject);
        email.setMessageId(messageId);
        return email;
    }
}
