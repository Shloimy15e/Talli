package dev.dynamiq.talli.integration.mercury;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MercuryWebhookServiceTest {

    private static final String SECRET = "webhook-secret";

    private MercuryExpenseSyncService expenseSyncService;
    private MercuryWebhookService service;

    @BeforeEach
    void setUp() {
        MercuryProperties properties = new MercuryProperties(
                true, "api-key", "https://api.mercury.com/api/v1", SECRET);
        expenseSyncService = mock(MercuryExpenseSyncService.class);
        service = new MercuryWebhookService(properties, new ObjectMapper(), expenseSyncService);
    }

    @Test
    void signedTransactionEventDelegatesToExpenseSync() throws Exception {
        String body = "{\"resourceType\":\"transaction\",\"resourceId\":\"txn-123\"}";
        when(expenseSyncService.importTransaction("txn-123"))
                .thenReturn(MercuryExpenseSyncService.ImportResult.imported(42L));

        MercuryExpenseSyncService.ImportResult result = service.process(body, signature(body));

        assertThat(result.status()).isEqualTo(MercuryExpenseSyncService.Status.IMPORTED);
        verify(expenseSyncService).importTransaction("txn-123");
    }

    @Test
    void rejectsInvalidSignatureBeforeProcessing() {
        String body = "{\"resourceType\":\"transaction\",\"resourceId\":\"txn-123\"}";

        assertThatThrownBy(() -> service.process(body, "invalid"))
                .isInstanceOf(MercuryWebhookService.InvalidSignatureException.class);
        verifyNoInteractions(expenseSyncService);
    }

    @Test
    void processingFailureIsRetryableRatherThanBadPayload() throws Exception {
        String body = "{\"resourceType\":\"transaction\",\"resourceId\":\"txn-123\"}";
        when(expenseSyncService.importTransaction("txn-123"))
                .thenThrow(new IllegalStateException("Mercury unavailable"));

        assertThatThrownBy(() -> service.process(body, signature(body)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(MercuryWebhookService.BadPayloadException.class);
    }

    private String signature(String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
