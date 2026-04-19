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

@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

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

    public EmailService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /** Result of a send — html is the rendered body (empty for plain), resendId is the Resend message id. */
    public record Result(String html, String resendId) {}

    public Result sendPlain(String to, String subject, String body) {
        return sendPlain(to, List.of(), subject, body);
    }

    public Result sendPlain(String to, List<String> bcc, String subject, String body) {
        String id = send(to, bcc, subject, null, body, null);
        return new Result("", id);
    }

    /**
     * Send both an HTML and a plain-text version. Use when the composed body
     * should be rendered with formatting (e.g. with a user signature appended).
     */
    public Result sendHtml(String to, List<String> bcc, String subject, String text, String html) {
        String id = send(to, bcc, subject, html, text, null);
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
        String html = render(templateName, variables);
        String id = send(to, bcc, subject, html, null, null);
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
        String html = render(templateName, variables);
        Map<String, Object> attachment = Map.of(
                "filename", attachmentFilename,
                "content", Base64.getEncoder().encodeToString(attachmentBytes),
                "contentType", attachmentMime);
        String id = send(to, bcc, subject, html, null, List.of(attachment));
        return new Result(html, id);
    }

    private String render(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        context.setVariable("fromAddress", fromAddress);
        context.setVariable("fromName", fromName);
        return templateEngine.process("emails/" + templateName, context);
    }

    private String send(String to, List<String> bcc, String subject,
                        String html, String text,
                        List<Map<String, Object>> attachments) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is not configured.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromName + " <" + fromAddress + ">");
        payload.put("to", List.of(to));
        payload.put("subject", subject);
        if (html != null) payload.put("html", html);
        if (text != null) payload.put("text", text);
        if (bcc != null && !bcc.isEmpty()) payload.put("bcc", bcc);
        if (attachments != null && !attachments.isEmpty()) payload.put("attachments", attachments);

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
}
