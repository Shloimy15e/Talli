package dev.dynamiq.talli.webhook.resend.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.webhook.resend.ResendEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Pings the admin on notable delivery events so they notice problems without
 * having to check the email log. Self-messages are skipped to avoid infinite
 * loops (admin alert → delivery event → another admin alert).
 */
@Component
public class AdminAlertHandler implements ResendEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminAlertHandler.class);

    private static final Set<String> NOTIFIED_EVENTS = Set.of(
            "email.delivered",
            "email.bounced",
            "email.complained",
            "email.failed"
    );

    private final EmailService emailService;

    @Value("${app.admin.notify-email:${app.seed.admin-email:}}")
    private String adminEmail;

    public AdminAlertHandler(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public boolean supports(String type) {
        return NOTIFIED_EVENTS.contains(type);
    }

    @Override
    public void handle(String type, JsonNode data) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.debug("Admin notify email not configured — skipping alert for {}", type);
            return;
        }

        String recipient = firstRecipient(data);
        if (recipient != null && recipient.equalsIgnoreCase(adminEmail)) {
            // Alert about alert would loop forever.
            return;
        }

        String subject = data.path("subject").asText("(no subject)");
        String label = switch (type) {
            case "email.delivered" -> "delivered";
            case "email.bounced" -> "bounced";
            case "email.complained" -> "marked as spam";
            case "email.failed" -> "failed to send";
            default -> type;
        };

        StringBuilder body = new StringBuilder()
                .append("Email ").append(label).append(".\n\n")
                .append("To: ").append(recipient != null ? recipient : "(unknown)").append("\n")
                .append("Subject: ").append(subject).append("\n")
                .append("Message ID: ").append(data.path("email_id").asText("(unknown)")).append("\n");

        if ("email.bounced".equals(type)) {
            String reason = data.path("bounce").path("message").asText(null);
            if (reason == null) reason = data.path("reason").asText(null);
            if (reason != null) body.append("Reason: ").append(reason).append("\n");
        }
        if ("email.failed".equals(type)) {
            String reason = data.path("reason").asText(null);
            if (reason != null) body.append("Reason: ").append(reason).append("\n");
        }

        try {
            emailService.sendPlain(adminEmail, "[Talli] Email " + label + ": " + subject, body.toString());
        } catch (Exception e) {
            log.warn("Failed to deliver admin alert for {}: {}", type, e.getMessage());
        }
    }

    private static String firstRecipient(JsonNode data) {
        JsonNode to = data.path("to");
        if (to.isArray() && to.size() > 0) return to.get(0).asText(null);
        if (to.isTextual()) return to.asText(null);
        return null;
    }
}
