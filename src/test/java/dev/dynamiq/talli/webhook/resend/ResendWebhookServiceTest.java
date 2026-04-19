package dev.dynamiq.talli.webhook.resend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResendWebhookServiceTest {

    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(
            "test-secret-long-enough-for-hmac-32b!!".getBytes(StandardCharsets.UTF_8));

    private ResendEventHandler matching;
    private ResendEventHandler notMatching;
    private ResendEventHandler throwing;
    private ResendWebhookService service;

    @BeforeEach
    void setUp() throws Exception {
        matching = mock(ResendEventHandler.class);
        when(matching.supports("email.delivered")).thenReturn(true);

        notMatching = mock(ResendEventHandler.class);
        when(notMatching.supports(anyString())).thenReturn(false);

        throwing = mock(ResendEventHandler.class);
        when(throwing.supports("email.delivered")).thenReturn(true);
        doThrow(new RuntimeException("handler boom")).when(throwing).handle(anyString(), any());

        service = new ResendWebhookService(List.of(matching, notMatching, throwing));
        setField(service, "webhookSecret", SECRET);
    }

    @Test
    void process_dispatchesToSupportingHandlersOnly() {
        String body = "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"msg_1\"}}";
        String id = "evt_1";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(SECRET, id, ts, body);

        service.process(body, id, ts, sig);

        verify(matching).handle(eq("email.delivered"), any(JsonNode.class));
        verify(notMatching, never()).handle(anyString(), any());
    }

    @Test
    void process_isolatesHandlerFailures() {
        // A handler throwing must not break sibling handlers — Resend retries would cause duplicate work.
        String body = "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"msg_1\"}}";
        String id = "evt_1";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(SECRET, id, ts, body);

        service.process(body, id, ts, sig);

        verify(matching).handle(anyString(), any());
        verify(throwing).handle(anyString(), any());
    }

    @Test
    void process_rejectsInvalidSignature() {
        String body = "{\"type\":\"email.delivered\",\"data\":{}}";
        String id = "evt_1";
        String ts = String.valueOf(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> service.process(body, id, ts, "v1,totallyWrongSig"))
                .isInstanceOf(ResendWebhookService.InvalidSignatureException.class);
        verifyNoInteractions(matching);
    }

    @Test
    void process_rejectsMalformedJson() {
        String body = "not-json-at-all";
        String id = "evt_1";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(SECRET, id, ts, body);

        assertThatThrownBy(() -> service.process(body, id, ts, sig))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(matching);
    }

    @Test
    void process_rejectsPayloadMissingTypeOrData() {
        String body = "{\"data\":{}}"; // no "type"
        String id = "evt_1";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(SECRET, id, ts, body);

        assertThatThrownBy(() -> service.process(body, id, ts, sig))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("type");
        verifyNoInteractions(matching);
    }

    @Test
    void process_failsWhenSecretNotConfigured() throws Exception {
        setField(service, "webhookSecret", "");
        assertThatThrownBy(() -> service.process("{}", "id", "0", "v1,x"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String sign(String secret, String id, String timestamp, String body) {
        try {
            String raw = secret.startsWith("whsec_") ? secret.substring(6) : secret;
            byte[] key = Base64.getDecoder().decode(raw);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] hash = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
