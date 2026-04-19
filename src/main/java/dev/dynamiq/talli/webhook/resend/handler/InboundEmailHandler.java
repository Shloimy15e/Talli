package dev.dynamiq.talli.webhook.resend.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.webhook.resend.ResendEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
    private final UserRepository userRepository;
    private final EmailService emailService;

    public InboundEmailHandler(EmailRepository emailRepository,
                               ClientRepository clientRepository,
                               UserRepository userRepository,
                               EmailService emailService) {
        this.emailRepository = emailRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Override
    public boolean supports(String type) {
        return "email.received".equals(type);
    }

    @Override
    public void handle(String type, JsonNode data) {
        String resendId = data.path("email_id").asText(null);

        // Idempotency: if we already stored this inbound event, skip.
        if (resendId != null && emailRepository.findByResendId(resendId).isPresent()) {
            log.debug("Skipping duplicate inbound email_id={}", resendId);
            return;
        }

        // Metadata from the webhook payload.
        String from = extractAddress(data.path("from"));
        String to = extractAddress(data.path("to"));
        String subject = data.path("subject").asText("");
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
            }
        } catch (Exception e) {
            log.warn("Could not fetch body for inbound email {}: {}", resendId, e.getMessage());
        }

        Email email = new Email();
        email.setDirection("in");
        email.setFromAddress(from);
        email.setToAddress(to);
        email.setSubject(subject);
        email.setBody(text);
        email.setBodyHtml(html);
        email.setStatus("received");
        email.setResendId(resendId);
        email.setReceivedAt(LocalDateTime.now());

        // Auto-link to a Client when the sender address matches.
        if (from != null && !from.isBlank()) {
            try {
                clientRepository.findByEmailIgnoreCase(from).ifPresent(email::setClient);
            } catch (Exception e) {
                log.warn("Client lookup failed for inbound sender {}: {}", from, e.getMessage());
            }
        }

        // Save in its own transaction so forward-to-admins failures can't roll it back.
        Email saved;
        try {
            saved = emailRepository.save(email);
        } catch (Exception e) {
            log.error("Failed to save inbound email from={}: {}", from, e.getMessage(), e);
            return;
        }
        log.info("Saved inbound email id={} from={} to={} subject='{}'", saved.getId(), from, to, subject);

        try {
            forwardToAdmins(saved);
        } catch (Exception e) {
            log.error("Failed to forward inbound email {}: {}", saved.getId(), e.getMessage(), e);
        }
    }

    /**
     * Forward the received email to every enabled admin user so they see it in
     * their own inbox. We skip any admin whose address matches the original
     * sender or recipient to avoid re-delivery loops.
     */
    private void forwardToAdmins(Email email) {
        List<User> admins = userRepository.findEnabledByRoleName("admin");
        if (admins.isEmpty()) return;

        String from = email.getFromAddress() == null ? "(unknown)" : email.getFromAddress();
        String subject = "Fwd: " + (email.getSubject() == null ? "(no subject)" : email.getSubject());

        // Header block describing the forwarded email
        String headerText = "---------- Forwarded message ----------\n"
                + "From: " + from + "\n"
                + "To: " + email.getToAddress() + "\n"
                + "Subject: " + (email.getSubject() == null ? "(no subject)" : email.getSubject()) + "\n"
                + "Date: " + (email.getReceivedAt() == null ? "" : email.getReceivedAt()) + "\n\n";

        String plainBody = headerText + (email.getBody() == null ? "" : email.getBody());
        String htmlBody = null;
        if (email.getBodyHtml() != null) {
            htmlBody = "<div style=\"font:13px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;color:#475569;border-left:3px solid #e2e8f0;padding:6px 0 6px 12px;margin:0 0 16px;\">"
                    + "<div><strong>Forwarded message</strong></div>"
                    + "<div>From: " + escapeHtml(from) + "</div>"
                    + "<div>To: " + escapeHtml(email.getToAddress()) + "</div>"
                    + "<div>Subject: " + escapeHtml(email.getSubject() == null ? "(no subject)" : email.getSubject()) + "</div>"
                    + (email.getReceivedAt() == null ? "" : "<div>Date: " + email.getReceivedAt() + "</div>")
                    + "</div>"
                    + email.getBodyHtml();
        }

        for (User admin : admins) {
            String adminEmail = admin.getEmail();
            if (adminEmail == null || adminEmail.isBlank()) continue;
            // Avoid loops: skip if the admin is the original sender or the direct recipient.
            if (equalsIgnoreCase(adminEmail, from) || equalsIgnoreCase(adminEmail, email.getToAddress())) continue;

            try {
                if (htmlBody != null) {
                    emailService.sendHtml(adminEmail, List.of(), subject, plainBody, htmlBody);
                } else {
                    emailService.sendPlain(adminEmail, List.of(), subject, plainBody);
                }
            } catch (Exception e) {
                log.warn("Failed to forward inbound email to admin {}: {}", adminEmail, e.getMessage());
            }
        }
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Resend may send the address as a plain string, an object with `email`/`address`/`value`,
     * or an array of either. We pull the first usable email address.
     */
    private static String extractAddress(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
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
                if (v != null && v.isTextual() && !v.asText().isBlank()) return v.asText();
            }
        }
        return null;
    }
}
