package dev.dynamiq.talli.webhook.resend.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.service.EmailThreadService;
import dev.dynamiq.talli.webhook.resend.ResendEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** Updates the matching Email row with delivery-state timestamps from Resend events. */
@Component
public class EmailStatusHandler implements ResendEventHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailStatusHandler.class);

    private final EmailRepository emailRepository;
    private final EmailThreadService threads;

    public EmailStatusHandler(EmailRepository emailRepository, EmailThreadService threads) {
        this.emailRepository = emailRepository;
        this.threads = threads;
    }

    @Override
    public boolean supports(String type) {
        // Only outbound delivery-state events. `email.received` is handled by
        // InboundEmailHandler and has no bearing on outbound tracking fields.
        return type != null && type.startsWith("email.") && !"email.received".equals(type);
    }

    @Override
    @Transactional
    public void handle(String type, JsonNode data) {
        String emailId = data.path("email_id").asText(null);
        if (emailId == null) return;

        Optional<Email> maybe = emailRepository.findByResendId(emailId);
        if (maybe.isEmpty()) {
            log.debug("No matching Email row for resend_id={} (event={})", emailId, type);
            return;
        }
        Email email = maybe.get();
        String messageId = EmailThreadService.normalizeMessageId(data.path("message_id").asText(null));
        if (email.getMessageId() == null && messageId != null) {
            email.setMessageId(messageId);
        }
        if (EmailThreadService.normalizeMessageId(email.getMessageId()) != null) threads.reconcileAfterMessageId(email);
        LocalDateTime now = LocalDateTime.now();

        switch (type) {
            case "email.delivered" -> {
                if (email.getDeliveredAt() == null) email.setDeliveredAt(now);
            }
            case "email.bounced" -> {
                if (email.getBouncedAt() == null) email.setBouncedAt(now);
                String reason = data.path("bounce").path("message").asText(null);
                if (reason == null) reason = data.path("reason").asText(null);
                if (reason != null) email.setBounceReason(reason);
            }
            case "email.complained" -> {
                if (email.getComplainedAt() == null) email.setComplainedAt(now);
            }
            case "email.opened" -> {
                if (email.getOpenedAt() == null) email.setOpenedAt(now);
            }
            case "email.clicked" -> {
                if (email.getClickedAt() == null) email.setClickedAt(now);
            }
            case "email.failed" -> {
                email.setStatus("failed");
                String reason = data.path("reason").asText(null);
                if (reason != null) email.setErrorMessage(reason);
            }
            default -> { /* email.sent records Message-ID above; no delivery timestamp yet. */ }
        }
    }
}
