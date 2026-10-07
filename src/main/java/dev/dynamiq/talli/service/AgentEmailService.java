package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.EmailSenderProfile;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Composes, sends, and audits email initiated through an authenticated agent. */
@Service
public class AgentEmailService {

    private final ClientRepository clients;
    private final EmailRepository emails;
    private final EmailService emailService;
    private final EmailTemplateCatalog templates;
    private final EmailSenderProfileService senders;
    private final EmailThreadService threads;
    private final String oversightCc;

    public AgentEmailService(ClientRepository clients, EmailRepository emails,
                             EmailService emailService,
                             EmailTemplateCatalog templates, EmailSenderProfileService senders,
                             EmailThreadService threads,
                             @Value("${app.mcp.email.cc:shloimy@dynamiq.dev}") String oversightCc) {
        this.clients = clients;
        this.emails = emails;
        this.emailService = emailService;
        this.templates = templates;
        this.senders = senders;
        this.threads = threads;
        this.oversightCc = validEmail(oversightCc, "app.mcp.email.cc");
    }

    public List<EmailSenderProfile> availableSenders() {
        return senders.options();
    }

    @Transactional
    public Preview preview(String actorEmail, Long clientId, String subject, String body,
                           String templateId, boolean includeSignature, boolean includeOversightCc,
                           String senderEmail, Long replyToEmailId, String toAddress) {
        Client client = clientId == null ? null : clients.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        EmailSender sender = senders.resolve(senderEmail);
        String recipient = client == null ? validEmail(toAddress, "toAddress") : clientEmail(client);
        if (client != null && toAddress != null
                && !recipient.equalsIgnoreCase(validEmail(toAddress, "toAddress"))) {
            throw new IllegalArgumentException("toAddress must match the client's saved email address");
        }
        EmailThreadService.ReplyContext reply = replyToEmailId == null ? null : threads.replyContext(replyToEmailId);
        if (reply != null && !recipient.equalsIgnoreCase(required(reply.recipientAddress(), "reply recipient"))) {
            throw new IllegalArgumentException("replyToEmailId belongs to a different recipient than the requested email.");
        }
        if (reply != null) {
            Email replyTarget = emails.findById(replyToEmailId)
                    .orElseThrow(() -> new IllegalArgumentException("Email not found: " + replyToEmailId));
            if (clientId != null && replyTarget.getClient() != null && !clientId.equals(replyTarget.getClient().getId())) {
                throw new IllegalArgumentException("replyToEmailId belongs to a different client.");
            }
        }
        String emailSubject = reply == null || !reply.providerThreaded()
                ? required(subject, "subject") : reply.subject();
        String emailBody = required(body, "body");
        if (emailSubject.length() > 255) throw new IllegalArgumentException("subject is too long");

        String signature = includeSignature ? sender.signatureHtml() : null;
        String selectedTemplate = templateId == null || templateId.isBlank()
                ? null : templateId.trim().toLowerCase(Locale.ROOT);
        String html = composeHtml(emailBody, selectedTemplate, signature, sender);
        List<String> cc = replyRecipients(reply == null ? null : reply.ccAddresses(),
                includeOversightCc ? oversightCc : null, sender.address(), recipient, List.of(),
                includeOversightCc ? null : oversightCc);
        List<String> bcc = replyRecipients(reply == null ? null : reply.bccAddresses(),
                null, sender.address(), recipient, cc, includeOversightCc ? null : oversightCc);
        String ccAddress = joinAddresses(cc);
        String bccAddress = joinAddresses(bcc);
        String token = previewToken(actorEmail, clientId, sender, recipient, ccAddress, bccAddress,
                emailSubject, emailBody, html, selectedTemplate, includeSignature, reply);
        return new Preview(clientId, sender.address(), sender.name(), recipient, ccAddress, bccAddress,
                emailSubject, emailBody, html,
                selectedTemplate, includeSignature, sender.signatureHtml(), replyToEmailId,
                reply == null ? null : reply.inReplyTo(), reply == null ? null : reply.referencesHeader(), token);
    }

    @Transactional
    public SendResult send(String actorEmail, Long clientId, String subject, String body,
                           String templateId, boolean includeSignature, boolean includeOversightCc,
                           String senderEmail, String previewToken, boolean confirmed, Long replyToEmailId,
                           String toAddress) {
        if (!confirmed) {
            throw new IllegalStateException("Explicit approval is required before sending email.");
        }
        Preview preview = preview(actorEmail, clientId, subject, body, templateId,
                includeSignature, includeOversightCc, senderEmail, replyToEmailId, toAddress);
        if (previewToken == null || !preview.previewToken().equals(previewToken.trim())) {
            throw new IllegalStateException(
                    "previewToken does not match this email. Preview the exact email before sending.");
        }

        Email email = new Email();
        email.setClient(clientId == null ? null : clients.findById(clientId).orElseThrow());
        email.setFromAddress(preview.fromAddress());
        email.setToAddress(preview.toAddress());
        email.setCc(preview.ccAddress());
        email.setBcc(preview.bccAddress());
        email.setSource("mcp");
        email.setInitiatedBy(required(actorEmail, "authenticated user"));
        email.setSubject(preview.subject());
        email.setBody(preview.body());
        if (preview.bodyHtml() != null) email.setBodyHtml(preview.bodyHtml());
        email = emails.save(email);
        threads.prepareOutgoing(email, replyToEmailId, null);
        email = emails.save(email);

        try {
            EmailSender sender = new EmailSender(preview.fromAddress(), preview.fromName(), preview.senderSignatureHtml());
            Map<String, String> headers = preview.inReplyTo() == null ? Map.of()
                    : Map.of("In-Reply-To", preview.inReplyTo(), "References", preview.referencesHeader());
            EmailService.Result result = emailService.sendMessage(sender, preview.toAddress(),
                    addressList(preview.ccAddress()), addressList(preview.bccAddress()), preview.subject(), preview.body(),
                    preview.bodyHtml(), List.of(), headers);
            email.setResendId(result.resendId());
            String messageId = EmailThreadService.normalizeMessageId(result.messageId());
            if (messageId != null) email.setMessageId(messageId);
            email.setStatus("sent");
            email.setSentAt(LocalDateTime.now());
        } catch (Exception exception) {
            email.setStatus("failed");
            email.setErrorMessage(exception.getMessage());
        }

        return new SendResult(emails.save(email), preview.fromName(), preview.templateId(),
                preview.signatureIncluded());
    }

    private String composeHtml(String body, String templateId, String signature,
                               EmailSender sender) {
        if (templateId == null && signature == null) return null;

        String content = "<div>" + EmailService.plainToHtml(body) + "</div>";
        if (signature != null) {
            content += "<br><div data-signature=\"1\">" + signature + "</div>";
        }
        return templateId == null ? templates.wrapBare(content) : templates.wrap(templateId, content, sender);
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

    private static List<String> replyRecipients(String savedAddresses, String oversightAddress,
                                                 String sender, String recipient,
                                                 List<String> alreadyIncluded, String excludedAddress) {
        List<String> addresses = new ArrayList<>();
        addRecipient(addresses, oversightAddress, sender, recipient, alreadyIncluded, excludedAddress);
        for (String address : addressList(savedAddresses)) {
            addRecipient(addresses, address, sender, recipient, alreadyIncluded, excludedAddress);
        }
        return List.copyOf(addresses);
    }

    private static void addRecipient(List<String> addresses, String address, String sender,
                                     String recipient, List<String> alreadyIncluded,
                                     String excludedAddress) {
        if (address == null || address.isBlank()
                || address.equalsIgnoreCase(sender) || address.equalsIgnoreCase(recipient)
                || (excludedAddress != null && address.equalsIgnoreCase(excludedAddress))
                || containsIgnoreCase(addresses, address) || containsIgnoreCase(alreadyIncluded, address)) {
            return;
        }
        addresses.add(validEmail(address, "reply recipient"));
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private static List<String> addressList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(address -> !address.isBlank())
                .toList();
    }

    private static String joinAddresses(List<String> addresses) {
        return addresses.isEmpty() ? null : String.join(", ", addresses);
    }

    private static String previewToken(String actorEmail, Long clientId, EmailSender sender,
                                        String recipient, String cc, String bcc,
                                        String subject, String body, String bodyHtml, String templateId,
                                        boolean includeSignature, EmailThreadService.ReplyContext reply) {
        String value = String.join("\u001f", required(actorEmail, "authenticated user"),
                clientId == null ? "" : clientId.toString(), sender.address(), sender.name(), recipient,
                cc == null ? "" : cc, bcc == null ? "" : bcc, subject, body,
                bodyHtml == null ? "" : bodyHtml,
                templateId == null ? "" : templateId, Boolean.toString(includeSignature),
                reply == null ? "" : reply.replyToEmailId().toString(),
                reply == null || reply.inReplyTo() == null ? "" : reply.inReplyTo(),
                reply == null || reply.referencesHeader() == null ? "" : reply.referencesHeader());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record Preview(Long clientId, String fromAddress, String fromName,
                           String toAddress, String ccAddress, String bccAddress,
                           String subject, String body, String bodyHtml,
                           String templateId, boolean signatureIncluded, String senderSignatureHtml,
                          Long replyToEmailId, String inReplyTo, String referencesHeader,
                           String previewToken) {}

    public record SendResult(Email email, String fromName, String templateId,
                              boolean signatureIncluded) {}

}
