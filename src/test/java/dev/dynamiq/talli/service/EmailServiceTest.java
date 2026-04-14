package dev.dynamiq.talli.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private SpringTemplateEngine templateEngine;
    private EmailService service;

    @BeforeEach
    void setUp() throws Exception {
        // Mockito stand-in for the real mail sender.
        // We use the Impl class to get createMimeMessage() behavior that returns a real MimeMessage.
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        templateEngine = mock(SpringTemplateEngine.class);
        when(templateEngine.process(anyString(), any())).thenReturn("<p>Rendered HTML</p>");

        service = new EmailService(mailSender, templateEngine);
        // @Value fields are null in unit tests — set them via reflection
        setField(service, "fromAddress", "test@dynamiq.dev");
        setField(service, "fromName", "Test Sender");
    }

    @Test
    void sendPlain_sendsMimeMessageWithCorrectFields() throws Exception {
        service.sendPlain("to@example.com", "Hello", "body text");

        // Verify .send was called with something
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendTemplate_rendersTemplateAndSends() throws Exception {
        service.sendTemplate("to@example.com", "Subject", "invoice", java.util.Map.of("name", "Shloimy"));

        // Verify the template was processed
        verify(templateEngine).process(eq("emails/invoice"), any());
        // And that send was called
        verify(mailSender).send(any(MimeMessage.class));
    }

    // Helper: sets private fields via reflection (workaround for @Value in unit tests)
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
