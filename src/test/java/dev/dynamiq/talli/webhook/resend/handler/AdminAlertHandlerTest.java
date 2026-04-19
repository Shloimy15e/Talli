package dev.dynamiq.talli.webhook.resend.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminAlertHandlerTest {

    private EmailService emailService;
    private AdminAlertHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        emailService = mock(EmailService.class);
        when(emailService.sendPlain(anyString(), anyString(), anyString()))
                .thenReturn(new EmailService.Result("", "msg_alert"));
        handler = new AdminAlertHandler(emailService);
        setField(handler, "adminEmail", "admin@dynamiq.dev");
    }

    @Test
    void supports_onlyNotifiedEvents() {
        assertThat(handler.supports("email.delivered")).isTrue();
        assertThat(handler.supports("email.bounced")).isTrue();
        assertThat(handler.supports("email.complained")).isTrue();
        assertThat(handler.supports("email.failed")).isTrue();
        assertThat(handler.supports("email.opened")).isFalse();
        assertThat(handler.supports("email.sent")).isFalse();
    }

    @Test
    void delivered_sendsAlertWithCorrectFields() throws Exception {
        String json = "{\"email_id\":\"msg_1\",\"to\":[\"client@example.com\"],\"subject\":\"Invoice 123\"}";
        handler.handle("email.delivered", data(json));

        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPlain(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        assertThat(toCaptor.getValue()).isEqualTo("admin@dynamiq.dev");
        assertThat(subjectCaptor.getValue()).contains("delivered").contains("Invoice 123");
        assertThat(bodyCaptor.getValue())
                .contains("client@example.com")
                .contains("msg_1")
                .contains("Invoice 123");
    }

    @Test
    void bounced_includesBounceReason() throws Exception {
        String json = "{\"email_id\":\"msg_1\",\"to\":[\"bad@example.com\"],"
                + "\"subject\":\"X\",\"bounce\":{\"message\":\"No such mailbox\"}}";
        handler.handle("email.bounced", data(json));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPlain(anyString(), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("No such mailbox");
    }

    @Test
    void skipsWhenRecipientIsAdmin() throws Exception {
        // Prevents infinite alert loop: admin-notification email → delivered event → another alert → ...
        String json = "{\"email_id\":\"msg_1\",\"to\":[\"admin@dynamiq.dev\"],\"subject\":\"Alert\"}";
        handler.handle("email.delivered", data(json));

        verifyNoInteractions(emailService);
    }

    @Test
    void skipsWhenAdminEmailNotConfigured() throws Exception {
        setField(handler, "adminEmail", "");
        handler.handle("email.delivered",
                data("{\"email_id\":\"msg_1\",\"to\":[\"client@example.com\"],\"subject\":\"X\"}"));
        verifyNoInteractions(emailService);
    }

    @Test
    void swallowsEmailSendFailure() throws Exception {
        // A failing alert must not propagate — Resend would retry and invoke other handlers again.
        when(emailService.sendPlain(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("SMTP down"));

        handler.handle("email.delivered",
                data("{\"email_id\":\"msg_1\",\"to\":[\"client@example.com\"],\"subject\":\"X\"}"));
        // No exception thrown.
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
