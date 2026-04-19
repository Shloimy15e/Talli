package dev.dynamiq.talli.webhook.resend.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmailStatusHandlerTest {

    private EmailRepository repo;
    private EmailStatusHandler handler;
    private Email email;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repo = mock(EmailRepository.class);
        handler = new EmailStatusHandler(repo);

        email = new Email();
        email.setId(1L);
        email.setResendId("msg_abc");
        email.setStatus("sent");
        when(repo.findByResendId("msg_abc")).thenReturn(Optional.of(email));
    }

    @Test
    void supports_matchesEmailEventsOnly() {
        assertThat(handler.supports("email.delivered")).isTrue();
        assertThat(handler.supports("email.bounced")).isTrue();
        assertThat(handler.supports("email.opened")).isTrue();
        assertThat(handler.supports("contact.created")).isFalse();
        assertThat(handler.supports(null)).isFalse();
    }

    @Test
    void delivered_setsDeliveredTimestamp() throws Exception {
        handler.handle("email.delivered", data("{\"email_id\":\"msg_abc\"}"));
        assertThat(email.getDeliveredAt()).isNotNull();
    }

    @Test
    void delivered_isIdempotent() throws Exception {
        LocalDateTime original = LocalDateTime.now().minusHours(1);
        email.setDeliveredAt(original);

        handler.handle("email.delivered", data("{\"email_id\":\"msg_abc\"}"));

        // Re-delivery from a Resend retry must not overwrite the original timestamp.
        assertThat(email.getDeliveredAt()).isEqualTo(original);
    }

    @Test
    void bounced_recordsTimestampAndReason() throws Exception {
        String json = "{\"email_id\":\"msg_abc\",\"bounce\":{\"message\":\"Mailbox full\"}}";
        handler.handle("email.bounced", data(json));

        assertThat(email.getBouncedAt()).isNotNull();
        assertThat(email.getBounceReason()).isEqualTo("Mailbox full");
    }

    @Test
    void bounced_fallsBackToFlatReasonField() throws Exception {
        String json = "{\"email_id\":\"msg_abc\",\"reason\":\"DNS error\"}";
        handler.handle("email.bounced", data(json));

        assertThat(email.getBounceReason()).isEqualTo("DNS error");
    }

    @Test
    void complained_setsComplaintTimestamp() throws Exception {
        handler.handle("email.complained", data("{\"email_id\":\"msg_abc\"}"));
        assertThat(email.getComplainedAt()).isNotNull();
    }

    @Test
    void opened_and_clicked_setRespectiveTimestamps() throws Exception {
        handler.handle("email.opened", data("{\"email_id\":\"msg_abc\"}"));
        handler.handle("email.clicked", data("{\"email_id\":\"msg_abc\"}"));
        assertThat(email.getOpenedAt()).isNotNull();
        assertThat(email.getClickedAt()).isNotNull();
    }

    @Test
    void failed_marksStatusFailedAndRecordsReason() throws Exception {
        String json = "{\"email_id\":\"msg_abc\",\"reason\":\"Invalid recipient\"}";
        handler.handle("email.failed", data(json));

        assertThat(email.getStatus()).isEqualTo("failed");
        assertThat(email.getErrorMessage()).isEqualTo("Invalid recipient");
    }

    @Test
    void unknownResendId_skipsSilently() throws Exception {
        when(repo.findByResendId("msg_unknown")).thenReturn(Optional.empty());
        handler.handle("email.delivered", data("{\"email_id\":\"msg_unknown\"}"));
        // No throw, no write — just drops the event (could be for an email sent outside our system).
        assertThat(email.getDeliveredAt()).isNull();
    }

    @Test
    void missingEmailId_skipsSilently() throws Exception {
        handler.handle("email.delivered", data("{}"));
        verifyNoInteractions(repo);
    }

    @Test
    void unknownEventType_doesNothing() throws Exception {
        handler.handle("email.sent", data("{\"email_id\":\"msg_abc\"}"));
        assertThat(email.getDeliveredAt()).isNull();
        assertThat(email.getBouncedAt()).isNull();
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json);
    }
}
