package dev.dynamiq.talli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final String RESEND_RECEIVING_URL = "https://api.resend.com/emails/receiving/";

    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.mail.resend.api-key:}")
    private String apiKey;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.business.name:Dynamiq Solutions Inc}")
    private String businessName;

    @Value("${app.business.email:info@dynamiq.dev}")
    private String businessEmail;

    @Value("${app.business.address:100 Cherry Ln, Airmont, NY 10952}")
    private String businessAddress;

    public EmailService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /** Result of a send — html is the rendered body (empty for plain), resendId is the Resend message id. */
    public record Result(String html, String resendId) {}

    /** File content and metadata to include with an outbound email. */
    public record Attachment(String filename, byte[] content, String contentType) {
        public Attachment {
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("Attachment filename is required.");
            }
            Objects.requireNonNull(content, "Attachment content is required.");
        }
    }

    /** Body of a received email fetched from the Resend receiving API. */
    public record ReceivedEmail(String text, String html) {}

    /**
     * Fetches the body of an inbound email by id. Resend's email.received
     * webhook only carries metadata — the body must be pulled separately.
     * Returns null if the API isn't configured or the fetch fails.
     */
    public ReceivedEmail fetchReceivedEmail(String emailId) {
        if (apiKey == null || apiKey.isBlank() || emailId == null || emailId.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_RECEIVING_URL + emailId))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Resend receiving API " + response.statusCode() + ": " + response.body());
            }
            var node = mapper.readTree(response.body());
            String text = node.path("text").asText(null);
            String html = node.path("html").asText(null);
            return new ReceivedEmail(text, html);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to fetch received email: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Fetch received email interrupted", e);
        }
    }

    public Result sendPlain(String to, String subject, String body) {
        return sendPlain(to, List.of(), subject, body);
    }

    public Result sendPlain(String to, List<String> bcc, String subject, String body) {
        return sendPlain(to, bcc, subject, body, List.of());
    }

    public Result sendPlain(String to, List<String> bcc, String subject, String body,
                            List<Attachment> attachments) {
        return sendPlain(to, List.of(), bcc, subject, body, attachments);
    }

    public Result sendPlain(String to, List<String> cc, List<String> bcc,
                            String subject, String body) {
        return sendPlain(to, cc, bcc, subject, body, List.of());
    }

    public Result sendPlain(String to, List<String> cc, List<String> bcc,
                            String subject, String body, List<Attachment> attachments) {
        String id = send(to, cc, bcc, subject, null, body, attachments);
        return new Result("", id);
    }

    /**
     * Send both an HTML and a plain-text version. Use when the composed body
     * should be rendered with formatting (e.g. with a user signature appended).
     */
    public Result sendHtml(String to, List<String> bcc, String subject, String text, String html) {
        return sendHtml(to, bcc, subject, text, html, List.of());
    }

    public Result sendHtml(String to, List<String> bcc, String subject, String text, String html,
                           List<Attachment> attachments) {
        return sendHtml(to, List.of(), bcc, subject, text, html, attachments);
    }

    public Result sendHtml(String to, List<String> cc, List<String> bcc,
                           String subject, String text, String html) {
        return sendHtml(to, cc, bcc, subject, text, html, List.of());
    }

    public Result sendHtml(String to, List<String> cc, List<String> bcc,
                           String subject, String text, String html,
                           List<Attachment> attachments) {
        String id = send(to, cc, bcc, subject, html, text, attachments);
        return new Result(html, id);
    }

    /**
     * Render a plain-text message body as HTML: escape entities and convert
     * newlines to <br>. Used to wrap user-composed plain text so a signature
     * can be appended as HTML.
     */
    public static String plainToHtml(String text) {
        if (text == null) return "";
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        return escaped.replace("\r\n", "\n").replace("\n", "<br>\n");
    }

    public Result sendTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        return sendTemplate(to, List.of(), subject, templateName, variables);
    }

    public Result sendTemplate(String to, List<String> bcc, String subject, String templateName,
                               Map<String, Object> variables) {
        return sendTemplate(to, List.of(), bcc, subject, templateName, variables, List.of());
    }

    public Result sendTemplate(String to, List<String> bcc, String subject, String templateName,
                               Map<String, Object> variables, List<Attachment> attachments) {
        return sendTemplate(to, List.of(), bcc, subject, templateName, variables, attachments);
    }

    public Result sendTemplate(String to, List<String> cc, List<String> bcc,
                               String subject, String templateName,
                               Map<String, Object> variables, List<Attachment> attachments) {
        String html = render(templateName, variables);
        String id = send(to, cc, bcc, subject, html, null, attachments);
        return new Result(html, id);
    }

    public Result sendTemplateWithAttachment(String to, String subject, String templateName,
                                             Map<String, Object> variables,
                                             byte[] attachmentBytes, String attachmentFilename,
                                             String attachmentMime) {
        return sendTemplateWithAttachment(to, List.of(), subject, templateName,
                variables, attachmentBytes, attachmentFilename, attachmentMime);
    }

    public Result sendTemplateWithAttachment(String to, List<String> bcc,
                                             String subject, String templateName,
                                             Map<String, Object> variables,
                                             byte[] attachmentBytes, String attachmentFilename,
                                             String attachmentMime) {
        return sendTemplateWithAttachment(to, List.of(), bcc, subject, templateName,
                variables, attachmentBytes, attachmentFilename, attachmentMime);
    }

    public Result sendTemplateWithAttachment(String to, List<String> cc, List<String> bcc,
                                             String subject, String templateName,
                                             Map<String, Object> variables,
                                             byte[] attachmentBytes, String attachmentFilename,
                                             String attachmentMime) {
        Attachment attachment = new Attachment(attachmentFilename, attachmentBytes, attachmentMime);
        return sendTemplate(to, cc, bcc, subject, templateName, variables, List.of(attachment));
    }

    private String render(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        context.setVariable("fromAddress", fromAddress);
        context.setVariable("fromName", fromName);
        context.setVariable("businessName", businessName);
        context.setVariable("businessEmail", businessEmail);
        context.setVariable("businessAddress", businessAddress);
        return templateEngine.process("emails/" + templateName, context);
    }

    private String send(String to, List<String> cc, List<String> bcc, String subject,
                        String html, String text,
                        List<Attachment> attachments) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromName + " <" + fromAddress + ">");
        payload.put("to", List.of(to));
        payload.put("subject", subject);
        if (html != null) payload.put("html", html);
        if (text != null) payload.put("text", text);
        if (cc != null && !cc.isEmpty()) payload.put("cc", cc);
        if (bcc != null && !bcc.isEmpty()) payload.put("bcc", bcc);
        if (attachments != null && !attachments.isEmpty()) {
            payload.put("attachments", attachments.stream()
                    .map(EmailService::attachmentPayload)
                    .toList());
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Resend API error " + response.statusCode() + ": " + response.body());
            }
            var node = mapper.readTree(response.body());
            var idNode = node.get("id");
            return idNode != null ? idNode.asText() : null;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email send interrupted", e);
        }
    }

    private static Map<String, Object> attachmentPayload(Attachment attachment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filename", attachment.filename());
        payload.put("content", Base64.getEncoder().encodeToString(attachment.content()));
        if (attachment.contentType() != null && !attachment.contentType().isBlank()) {
            payload.put("content_type", attachment.contentType());
        }
        return payload;
    }
}
