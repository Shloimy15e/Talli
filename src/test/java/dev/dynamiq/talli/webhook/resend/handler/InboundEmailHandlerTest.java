package dev.dynamiq.talli.webhook.resend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.EmailMailboxStateRepository;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.service.EmailThreadService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InboundEmailHandlerTest {

    @Test
    void retainsAllBusinessRecipientsAndCopiedMailboxes() throws Exception {
        EmailRepository emails = mock(EmailRepository.class);
        InboundEmailHandler handler = new InboundEmailHandler(emails, mock(ClientRepository.class),
                mock(EmailService.class), new EmailThreadService(emails, mock(EmailMailboxStateRepository.class)));
        when(emails.save(any(Email.class))).thenAnswer(invocation -> {
            Email email = invocation.getArgument(0);
            email.setId(101L);
            return email;
        });
        handler.handle("email.received", new ObjectMapper().readTree("""
                {"email_id":"multiple-inboxes","from":"Customer <customer@example.test>",
                 "to":["Info <info@dynamiq.dev>", {"email":"Billing <billing@dynamiq.dev>"}],
                 "cc":["sales@dynamiq.dev"], "subject":"For the team"}
                """));
        ArgumentCaptor<Email> saved = ArgumentCaptor.forClass(Email.class);
        verify(emails, times(2)).save(saved.capture());
        Email inbound = saved.getAllValues().getLast();
        assertThat(inbound.getFromAddress()).isEqualTo("customer@example.test");
        assertThat(inbound.getToAddress()).isEqualTo("info@dynamiq.dev,billing@dynamiq.dev");
        assertThat(inbound.getCc()).isEqualTo("sales@dynamiq.dev");
    }

    @Test
    void skipsReturningCcCopyWhenFetchedMetadataMatchesAnOutgoingMessageId() throws Exception {
        EmailRepository emails = mock(EmailRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        EmailService emailService = mock(EmailService.class);
        InboundEmailHandler handler = new InboundEmailHandler(emails, clients, emailService,
                new EmailThreadService(emails, mock(EmailMailboxStateRepository.class)));

        Email outgoing = new Email();
        outgoing.setDirection("out");
        outgoing.setResendId("outbound-send-1");
        outgoing.setMessageId("<outbound-copy@dynamiq.dev>");
        when(emails.findByResendId("received-copy-2")).thenReturn(Optional.empty());
        when(emailService.fetchReceivedEmail("received-copy-2")).thenReturn(new EmailService.ReceivedEmail(
                "Owner CC", null, " <outbound-copy@dynamiq.dev> ", null, null));
        when(emails.findFirstByMessageIdAndDirectionOrderByIdAsc("<outbound-copy@dynamiq.dev>", "out"))
                .thenReturn(Optional.of(outgoing));

        handler.handle("email.received", new ObjectMapper().readTree("""
                {"email_id":"received-copy-2","from":"owner@example.test",
                 "to":"billing@dynamiq.dev","subject":"Invoice"}
                """));

        verify(emailService).fetchReceivedEmail("received-copy-2");
        verifyNoMoreInteractions(emailService);
        verify(emails, never()).save(any(Email.class));
        verify(clients, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void persistsProviderBodyAndHeadersAndLinksFormattedInboundReplyToOutgoingRoot() throws Exception {
        EmailRepository emails = mock(EmailRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        EmailService emailService = mock(EmailService.class);
        EmailThreadService threads = new EmailThreadService(emails, mock(EmailMailboxStateRepository.class));
        InboundEmailHandler handler = new InboundEmailHandler(emails, clients, emailService, threads);

        Email parent = new Email();
        parent.setId(44L);
        parent.setMessageId("<outgoing@dynamiq.dev>");
        parent.setThreadRootId(40L);
        when(emails.findByResendId("inbound-1")).thenReturn(Optional.empty());
        when(emails.findFirstByMessageIdAndCopyOfEmailIdIsNullOrderByIdAsc("<outgoing@dynamiq.dev>"))
                .thenReturn(Optional.of(parent));
        when(emailService.fetchReceivedEmail("inbound-1")).thenReturn(new EmailService.ReceivedEmail(
                "Plain reply", "<p>HTML reply</p>", "<inbound@client.test>",
                "<outgoing@dynamiq.dev>", "<root@dynamiq.dev> <outgoing@dynamiq.dev>"));
        when(emails.save(any(Email.class))).thenAnswer(invocation -> {
            Email email = invocation.getArgument(0);
            if (email.getId() == null) email.setId(101L);
            return email;
        });

        handler.handle("email.received", new ObjectMapper().readTree("""
                {"email_id":"inbound-1","from":"Acme <client@example.test>",
                 "to":["support@dynamiq.dev"],"subject":"Re: Invoice"}
                """));

        ArgumentCaptor<Email> saved = ArgumentCaptor.forClass(Email.class);
        verify(emails, times(2)).save(saved.capture());
        Email inbound = saved.getAllValues().getLast();
        assertThat(inbound.getDirection()).isEqualTo("in");
        assertThat(inbound.getFromAddress()).isEqualTo("client@example.test");
        assertThat(inbound.getToAddress()).isEqualTo("support@dynamiq.dev");
        assertThat(inbound.getBody()).isEqualTo("Plain reply");
        assertThat(inbound.getBodyHtml()).isEqualTo("<p>HTML reply</p>");
        assertThat(inbound.getResendId()).isEqualTo("inbound-1");
        assertThat(inbound.getMessageId()).isEqualTo("<inbound@client.test>");
        assertThat(inbound.getInReplyTo()).isEqualTo("<outgoing@dynamiq.dev>");
        assertThat(inbound.getReferencesHeader()).isEqualTo("<root@dynamiq.dev> <outgoing@dynamiq.dev>");
        assertThat(inbound.getThreadRootId()).isEqualTo(40L);
        verify(emailService).fetchReceivedEmail("inbound-1");
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void persistsInboundMailWhenFetchedMetadataDoesNotMatchAnOutgoingMessage() throws Exception {
        EmailRepository emails = mock(EmailRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        EmailService emailService = mock(EmailService.class);
        InboundEmailHandler handler = new InboundEmailHandler(emails, clients, emailService,
                new EmailThreadService(emails, mock(EmailMailboxStateRepository.class)));
        when(emails.findByResendId("inbound-unknown")).thenReturn(Optional.empty());
        when(emailService.fetchReceivedEmail("inbound-unknown")).thenReturn(new EmailService.ReceivedEmail(
                "Unknown metadata", null, null, null, null));
        when(emails.save(any(Email.class))).thenAnswer(invocation -> {
            Email email = invocation.getArgument(0);
            if (email.getId() == null) email.setId(102L);
            return email;
        });

        handler.handle("email.received", new ObjectMapper().readTree("""
                {"email_id":"inbound-unknown","from":"client@example.test",
                 "to":"support@dynamiq.dev","subject":"A new question"}
                """));

        ArgumentCaptor<Email> saved = ArgumentCaptor.forClass(Email.class);
        verify(emails, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getLast().getMessageId()).isNull();
        verify(emailService).fetchReceivedEmail("inbound-unknown");
        verifyNoMoreInteractions(emailService);
    }
}
