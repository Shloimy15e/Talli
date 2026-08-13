package dev.dynamiq.talli.integration.mercury;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MercurySignatureVerifierTest {

    private static final String SECRET = "webhook-secret";
    private static final String BODY = "{\"resourceType\":\"transaction\"}";
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void acceptsValidSignature() throws Exception {
        String signature = signature(NOW.getEpochSecond(), BODY);

        assertThat(MercurySignatureVerifier.verify(SECRET, signature, BODY, CLOCK)).isTrue();
    }

    @Test
    void rejectsTamperedOrStaleSignature() throws Exception {
        String valid = signature(NOW.getEpochSecond(), BODY);
        String stale = signature(NOW.minusSeconds(301).getEpochSecond(), BODY);

        assertThat(MercurySignatureVerifier.verify(SECRET, valid, BODY + " ", CLOCK)).isFalse();
        assertThat(MercurySignatureVerifier.verify(SECRET, stale, BODY, CLOCK)).isFalse();
    }

    private String signature(long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
