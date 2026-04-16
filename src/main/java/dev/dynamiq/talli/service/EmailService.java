package dev.dynamiq.talli.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    // Plain text email
    public void sendPlain(String to, String subject, String body)
            throws MessagingException, UnsupportedEncodingException {
        sendPlain(to, java.util.List.of(), subject, body);
    }

    public void sendPlain(String to, java.util.List<String> bcc, String subject, String body)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        if (bcc != null && !bcc.isEmpty()) {
            helper.setBcc(bcc.toArray(new String[0]));
        }
        helper.setSubject(subject);
        helper.setText(body, false);
        mailSender.send(message);
    }

    // HTML email rendered from a Thymeleaf template.
    // Template path: src/main/resources/templates/emails/{templateName}.html
    public void sendTemplate(String to, String subject, String templateName, Map<String, Object> variables)
            throws MessagingException, UnsupportedEncodingException {
        sendTemplate(to, java.util.List.of(), subject, templateName, variables);
    }

    /** HTML template email with optional BCC. Returns the rendered body for logging. */
    public String sendTemplate(String to, java.util.List<String> bcc,
                               String subject, String templateName, Map<String, Object> variables)
            throws MessagingException, UnsupportedEncodingException {
        Context context = new Context();
        context.setVariables(variables);
        context.setVariable("fromAddress", fromAddress);
        context.setVariable("fromName", fromName);
        String html = templateEngine.process("emails/" + templateName, context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        if (bcc != null && !bcc.isEmpty()) {
            helper.setBcc(bcc.toArray(new String[0]));
        }
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
        return html;
    }

    /**
     * HTML email with a single attachment. Returns the rendered body so callers
     * can persist it to the Email log row.
     */
    public String sendTemplateWithAttachment(String to, String subject, String templateName,
                                             Map<String, Object> variables,
                                             byte[] attachmentBytes, String attachmentFilename,
                                             String attachmentMime)
            throws MessagingException, UnsupportedEncodingException {
        return sendTemplateWithAttachment(to, java.util.List.of(), subject, templateName,
                variables, attachmentBytes, attachmentFilename, attachmentMime);
    }

    /**
     * HTML email with a single attachment and optional BCC list. Returns the
     * rendered body so callers can persist it to the Email log row.
     */
    public String sendTemplateWithAttachment(String to, java.util.List<String> bcc,
                                             String subject, String templateName,
                                             Map<String, Object> variables,
                                             byte[] attachmentBytes, String attachmentFilename,
                                             String attachmentMime)
            throws MessagingException, UnsupportedEncodingException {
        Context context = new Context();
        context.setVariables(variables);
        context.setVariable("fromAddress", fromAddress);
        context.setVariable("fromName", fromName);
        String html = templateEngine.process("emails/" + templateName, context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        if (bcc != null && !bcc.isEmpty()) {
            helper.setBcc(bcc.toArray(new String[0]));
        }
        helper.setSubject(subject);
        helper.setText(html, true);
        helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes), attachmentMime);
        mailSender.send(message);
        return html;
    }
}
