package dev.dynamiq.talli.webhook.resend.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.mcp.events.McpEventService;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.service.EmailThreadService;
import dev.dynamiq.talli.webhook.resend.ResendEventHandler;
import dev.dynamiq.talli.webhook.resend.RetryableResendEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persists inbound (received) emails from Resend's inbound webhook as new Email
 * rows with direction='in'. If the sender matches a known Client by email, the
 * row is auto-linked to that client.
 */
@Component
public class InboundEmailHandler implements ResendEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailHandler.class);

    private final EmailRepository emailRepository;
    private final ClientRepository clientRepository;
    private final EmailService emailService;
    private final EmailThreadService threads;
    private final McpEventService events;

    public InboundEmailHandler(EmailRepository emailRepository,
                               ClientRepository clientRepository,
                               EmailService emailService,
                               EmailThreadService threads,
                               McpEventService events) {
        this.emailRepository = emailRepository;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
        this.threads = threads;
        this.events = events;
    }

    @Override
    public boolean supports(String type) {
        return "email.received".equals(type);
    }

    @Override
    public boolean retryOnFailure() { return true; }

    @Override
    @Transactional
    public void handle(String type, JsonNode data) {
        String resendId = data.path("email_id").asText(null);

        // Repair the durable event queue on retries without storing another email.
        if (resendId != null) {
            var existing = emailRepository.findByResendId(resendId);
            if (existing.isPresent()) {
                enqueueEvent(existing.get());
                log.debug("Skipping duplicate inbound email_id={}", resendId);
                return;
            }
        }

        // Metadata from the webhook payload.
        String from = extractAddress(data.path("from"));
        String to = extractAddresses(data.path("to"));
        String subject = data.path("subject").asText("");
        String messageId = text(data, "message_id");
        String inReplyTo = firstPresent(text(data, "in_reply_to"), header(data.path("headers"), "In-Reply-To"));
        String references = firstPresent(text(data, "references"), header(data.path("headers"), "References"));
        if (subject.isBlank()) subject = "(no subject)";

        if (to == null || to.isBlank()) {
            log.warn("Inbound webhook missing 'to' field; skipping (resend_id={})", resendId);
            return;
        }

        // Webhook doesn't carry the body — fetch from Resend's receiving API.
        String text = "";
        String html = null;
        try {
            EmailService.ReceivedEmail body = emailService.fetchReceivedEmail(resendId);
            if (body != null) {
                if (body.text() != null) text = body.text();
                if (body.html() != null && !body.html().isBlank()) html = body.html();
                messageId = firstPresent(messageId, body.messageId());
                inReplyTo = firstPresent(inReplyTo, body.inReplyTo());
                references = firstPresent(references, body.references());
            }
        } catch (Exception e) {
            log.warn("Could not fetch body for inbound email {}: {}", resendId, e.getMessage());
        }

        String normalizedMessageId = EmailThreadService.normalizeMessageId(messageId);
        if (normalizedMessageId != null && emailRepository
                .findFirstByMessageIdAndDirectionOrderByIdAsc(normalizedMessageId, "out").isPresent()) {
            log.info("Skipping returning outbound copy resend_id={} message_id={}", resendId, normalizedMessageId);
            return;
        }

        Email email = new Email();
        email.setDirection("in");
        email.setFromAddress(from);
        email.setToAddress(to);
        email.setCc(extractAddresses(data.path("cc")));
        email.setBcc(extractAddresses(data.path("bcc")));
        email.setSubject(subject);
        email.setBody(text);
        email.setBodyHtml(html);
        email.setStatus("received");
        email.setResendId(resendId);
        email.setReceivedAt(LocalDateTime.now());
        threads.prepareInbound(email, normalizedMessageId, inReplyTo, references);

        // Auto-link to a Client when the sender address matches.
        if (from != null && !from.isBlank()) {
            try {
                clientRepository.findByEmailIgnoreCase(from).ifPresent(email::setClient);
            } catch (Exception e) {
                log.warn("Client lookup failed for inbound sender {}: {}", from, e.getMessage());
            }
        }

        // Persist the message; unread notifications are sent by the daily job.
        Email saved;
        try {
            saved = emailRepository.save(email);
            threads.ensureThreadRoot(saved);
            saved = emailRepository.save(saved);
        } catch (Exception e) {
            log.error("Failed to save inbound email from={}: {}", from, e.getMessage(), e);
            throw new RetryableResendEventException("Inbound email persistence failed", e);
        }
        enqueueEvent(saved);
        log.info("Saved inbound email id={} from={} to={} subject='{}'", saved.getId(), from, to, subject);

    }

    private void enqueueEvent(Email email) {
        try {
            events.enqueue(email);
        } catch (RuntimeException e) {
            throw new RetryableResendEventException("Inbound event persistence failed", e);
        }
    }

    /**
     * Resend may send the address as a plain string, an object with `email`/`address`/`value`,
     * or an array of either. We pull the first usable email address.
     */
    private static String extractAddresses(JsonNode node) {
        if (node != null && node.isArray()) {
            java.util.LinkedHashSet<String> addresses = new java.util.LinkedHashSet<>();
            for (JsonNode child : node) {
                String address = extractAddress(child);
                if (address != null && !address.isBlank()) addresses.add(address);
            }
            return addresses.isEmpty() ? null : String.join(",", addresses);
        }
        return extractAddress(node);
    }

    private static String extractAddress(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isTextual()) return mailbox(node.asText());
        if (node.isArray()) {
            for (JsonNode child : node) {
                String v = extractAddress(child);
                if (v != null && !v.isBlank()) return v;
            }
            return null;
        }
        if (node.isObject()) {
            for (String field : new String[] { "email", "address", "value" }) {
                JsonNode v = node.get(field);
                if (v != null && v.isTextual() && !v.asText().isBlank()) return mailbox(v.asText());
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String header(JsonNode headers, String name) {
        if (headers == null || !headers.isObject()) return null;
        var fields = headers.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue().isTextual()) {
                return entry.getValue().asText();
            }
        }
        return null;
    }

    private static String firstPresent(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private static String mailbox(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        int start = trimmed.lastIndexOf('<');
        int end = trimmed.lastIndexOf('>');
        return start >= 0 && end > start ? trimmed.substring(start + 1, end).trim() : trimmed;
    }
}
