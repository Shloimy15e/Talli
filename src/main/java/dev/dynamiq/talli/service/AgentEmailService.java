package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Composes, sends, and audits client email initiated through an authenticated agent. */
@Service
public class AgentEmailService {

    private final ClientRepository clients;
    private final UserRepository users;
    private final EmailRepository emails;
    private final EmailService emailService;
    private final EmailTemplateCatalog templates;
    private final String auditCc;

    public AgentEmailService(ClientRepository clients, UserRepository users,
                             EmailRepository emails, EmailService emailService,
                             EmailTemplateCatalog templates,
                             @Value("${app.mcp.email.cc:shloimy@dynamiq.dev}") String auditCc) {
        this.clients = clients;
        this.users = users;
        this.emails = emails;
        this.emailService = emailService;
        this.templates = templates;
        this.auditCc = validEmail(auditCc, "app.mcp.email.cc");
    }

    @Transactional
    public Preview preview(String actorEmail, Long clientId, String subject, String body,
                           String templateId, boolean includeSignature) {
        Client client = clients.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        String recipient = clientEmail(client);
        String emailSubject = required(subject, "subject");
        String emailBody = required(body, "body");
        if (emailSubject.length() > 255) throw new IllegalArgumentException("subject is too long");

        String signature = includeSignature ? signature(actorEmail) : null;
        String selectedTemplate = templateId == null || templateId.isBlank()
                ? null : templateId.trim().toLowerCase(Locale.ROOT);
        String html = composeHtml(emailBody, selectedTemplate, signature);
        String token = previewToken(actorEmail, clientId, recipient, auditCc, emailSubject, emailBody,
                html, selectedTemplate, includeSignature);
        return new Preview(clientId, recipient, auditCc, emailSubject, emailBody, html,
                selectedTemplate, includeSignature, token);
    }

    @Transactional
    public SendResult send(String actorEmail, Long clientId, String subject, String body,
                           String templateId, boolean includeSignature,
                           String previewToken, boolean confirmed) {
        if (!confirmed) {
            throw new IllegalStateException("Explicit approval is required before sending email.");
        }
        Preview preview = preview(actorEmail, clientId, subject, body, templateId, includeSignature);
        if (previewToken == null || !preview.previewToken().equals(previewToken.trim())) {
            throw new IllegalStateException(
                    "preview_token does not match this email. Preview the exact email before sending.");
        }

        Email email = new Email();
        email.setClient(clients.findById(clientId).orElseThrow());
        email.setToAddress(preview.toAddress());
        email.setCc(preview.ccAddress());
        email.setSubject(preview.subject());
        email.setBody(preview.body());
        if (preview.bodyHtml() != null) email.setBodyHtml(preview.bodyHtml());
        email = emails.save(email);

        try {
            EmailService.Result result = preview.bodyHtml() == null
                    ? emailService.sendPlain(preview.toAddress(), List.of(preview.ccAddress()), List.of(),
                            preview.subject(), preview.body())
                    : emailService.sendHtml(preview.toAddress(), List.of(preview.ccAddress()), List.of(),
                            preview.subject(), preview.body(), preview.bodyHtml());
            email.setResendId(result.resendId());
            email.setStatus("sent");
            email.setSentAt(LocalDateTime.now());
        } catch (Exception exception) {
            email.setStatus("failed");
            email.setErrorMessage(exception.getMessage());
        }

        return new SendResult(emails.save(email), preview.templateId(), preview.signatureIncluded());
    }

    private String composeHtml(String body, String templateId, String signature) {
        if (templateId == null && signature == null) return null;

        String content = "<div>" + EmailService.plainToHtml(body) + "</div>";
        if (signature != null) {
            content += "<br><div data-signature=\"1\">" + signature + "</div>";
        }
        return templateId == null ? content : templates.wrap(templateId, content);
    }

    private String signature(String actorEmail) {
        User actor = users.findByEmail(required(actorEmail, "authenticated user"))
                .orElseThrow(() -> new IllegalStateException("Authenticated MCP user was not found."));
        if (actor.getSignature() == null || actor.getSignature().isBlank()) {
            throw new IllegalStateException(
                    "The authenticated MCP user's email signature is not configured.");
        }
        return actor.getSignature();
    }

    private static String clientEmail(Client client) {
        return validEmail(client.getEmail(), "client email address");
    }

    private static String validEmail(String value, String name) {
        String email = required(value, name);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(name + " must be one valid email address");
        }
        return email;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String previewToken(String actorEmail, Long clientId, String recipient, String cc,
                                       String subject, String body, String bodyHtml, String templateId,
                                       boolean includeSignature) {
        String value = String.join("\u001f", required(actorEmail, "authenticated user"),
                clientId.toString(), recipient, cc, subject, body,
                bodyHtml == null ? "" : bodyHtml,
                templateId == null ? "" : templateId, Boolean.toString(includeSignature));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record Preview(Long clientId, String toAddress, String ccAddress,
                          String subject, String body, String bodyHtml,
                          String templateId, boolean signatureIncluded,
                          String previewToken) {}

    public record SendResult(Email email, String templateId, boolean signatureIncluded) {}
}
