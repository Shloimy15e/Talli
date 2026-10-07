package dev.dynamiq.talli.webhook.resend;

import dev.dynamiq.talli.mcp.events.McpEventService;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.mail.resend.webhook-secret=" + InboundMcpEventFlowTest.SECRET)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class InboundMcpEventFlowTest {
    static final String SECRET = "whsec_bWNwLWV2ZW50cy1pbmJvdW5kLXRlc3Qtc2VjcmV0LTAx";
    private static final String PROVIDER_ID = "mcp-flow-inbound";
    private static final String BODY = """
            {"type":"email.received","data":{"email_id":"mcp-flow-inbound",
             "from":"customer@example.test","to":["Theo <theo@dynamiq.dev>"],"subject":"For Theo"}}
            """;

    @Autowired MockMvc http;
    @Autowired EmailRepository emails;
    @MockitoBean McpEventService events;
    @MockitoBean EmailService delivery;

    @AfterEach
    void cleanUp() {
        emails.findByResendId(PROVIDER_ID).ifPresent(emails::delete);
    }

    @Test
    void verifiedWebhookPersistsBeforeEnqueueAndDuplicateDoesNotSaveAgain() throws Exception {
        performSigned().andExpect(status().isOk());
        Email persisted = emails.findByResendId(PROVIDER_ID).orElseThrow();
        ArgumentCaptor<Email> event = ArgumentCaptor.forClass(Email.class);
        verify(events).enqueue(event.capture());
        assertThat(event.getValue().getId()).isEqualTo(persisted.getId());
        assertThat(event.getValue().getThreadRootId()).isEqualTo(persisted.getId());
        assertThat(event.getValue().getToAddress()).isEqualTo("theo@dynamiq.dev");

        performSigned().andExpect(status().isOk());
        assertThat(emails.findByResendId(PROVIDER_ID).orElseThrow().getId()).isEqualTo(persisted.getId());
        verify(events, times(2)).enqueue(any(Email.class));
    }

    @Test
    void outboxFailureRollsBackEmailAndReturnsRetryableStatus() throws Exception {
        doThrow(new IllegalStateException("outbox unavailable")).when(events).enqueue(any(Email.class));
        performSigned().andExpect(status().isInternalServerError());
        assertThat(emails.findByResendId(PROVIDER_ID)).isEmpty();

        doNothing().when(events).enqueue(any(Email.class));
        performSigned().andExpect(status().isOk());
        assertThat(emails.findByResendId(PROVIDER_ID)).isPresent();
    }

    @Test
    void invalidResendSignatureCannotEnqueueAnEvent() throws Exception {
        http.perform(post("/webhooks/resend").contentType("application/json").content(BODY)
                .header("svix-id", "evt-flow")
                .header("svix-timestamp", Instant.now().getEpochSecond())
                .header("svix-signature", "v1,invalid"))
                .andExpect(status().isUnauthorized());
        verify(events, never()).enqueue(any(Email.class));
        assertThat(emails.findByResendId(PROVIDER_ID)).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions performSigned() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring(6)), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(mac.doFinal(
                ("evt-flow." + timestamp + "." + BODY).getBytes(StandardCharsets.UTF_8)));
        return http.perform(post("/webhooks/resend").contentType("application/json").content(BODY)
                .header("svix-id", "evt-flow").header("svix-timestamp", timestamp)
                .header("svix-signature", signature));
    }
}
