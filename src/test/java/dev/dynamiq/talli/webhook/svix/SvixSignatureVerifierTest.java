package dev.dynamiq.talli.webhook.svix;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SvixSignatureVerifierTest {

    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(
            "super-secret-key-at-least-32-bytes-long!".getBytes(StandardCharsets.UTF_8));

    @Test
    void verify_acceptsValidSignature() {
        String id = "msg_abc";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"type\":\"email.delivered\"}";
        String sig = sign(SECRET, id, ts, body);

        assertThat(SvixSignatureVerifier.verify(SECRET, id, ts, sig, body)).isTrue();
    }

    @Test
    void verify_acceptsSignatureAmongMultiple() {
        // Svix supports multiple active signatures during key rotation.
        String id = "msg_abc";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body = "{}";
        String validSig = sign(SECRET, id, ts, body);
        String header = "v1,someOtherBase64Sig " + validSig + " v1,yetAnother";

        assertThat(SvixSignatureVerifier.verify(SECRET, id, ts, header, body)).isTrue();
    }

    @Test
    void verify_rejectsTamperedBody() {
        String id = "msg_abc";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(SECRET, id, ts, "{\"amount\":10}");

        assertThat(SvixSignatureVerifier.verify(SECRET, id, ts, sig, "{\"amount\":1000}")).isFalse();
    }

    @Test
    void verify_rejectsWrongSecret() {
        String id = "msg_abc";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body = "{}";
        String sig = sign(SECRET, id, ts, body);

        String otherSecret = "whsec_" + Base64.getEncoder().encodeToString(
                "different-secret-at-least-32-bytes-long!".getBytes(StandardCharsets.UTF_8));
        assertThat(SvixSignatureVerifier.verify(otherSecret, id, ts, sig, body)).isFalse();
    }

    @Test
    void verify_rejectsExpiredTimestamp() {
        long old = Instant.now().minus(Duration.ofMinutes(10)).getEpochSecond();
        String id = "msg_abc";
        String ts = String.valueOf(old);
        String body = "{}";
        String sig = sign(SECRET, id, ts, body);

        assertThat(SvixSignatureVerifier.verify(SECRET, id, ts, sig, body)).isFalse();
    }

    @Test
    void verify_rejectsFutureTimestamp() {
        long future = Instant.now().plus(Duration.ofMinutes(10)).getEpochSecond();
        String id = "msg_abc";
        String ts = String.valueOf(future);
        String body = "{}";
        String sig = sign(SECRET, id, ts, body);

        assertThat(SvixSignatureVerifier.verify(SECRET, id, ts, sig, body)).isFalse();
    }

    @Test
    void verify_rejectsNonV1SignatureOnly() {
        String id = "msg_abc";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body = "{}";
        String validV1 = sign(SECRET, id, ts, body);
        // Replace the "v1," prefix so the verifier must reject it.
        String header = validV1.replace("v1,", "v2,");

        assertThat(SvixSignatureVerifier.verify(SECRET, id, ts, header, body)).isFalse();
    }

    @Test
    void verify_rejectsMalformedSignatureHeader() {
        String id = "msg_abc";
        String ts = String.valueOf(Instant.now().getEpochSecond());

        assertThat(SvixSignatureVerifier.verify(SECRET, id, ts, "not-a-signature", "{}")).isFalse();
    }

    @Test
    void verify_rejectsMalformedTimestamp() {
        assertThat(SvixSignatureVerifier.verify(SECRET, "msg_abc", "not-a-number", "v1,xxx", "{}")).isFalse();
    }

    @Test
    void verify_rejectsNullOrBlankInputs() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        assertThat(SvixSignatureVerifier.verify(null, "id", ts, "v1,x", "{}")).isFalse();
        assertThat(SvixSignatureVerifier.verify("", "id", ts, "v1,x", "{}")).isFalse();
        assertThat(SvixSignatureVerifier.verify(SECRET, null, ts, "v1,x", "{}")).isFalse();
        assertThat(SvixSignatureVerifier.verify(SECRET, "id", null, "v1,x", "{}")).isFalse();
        assertThat(SvixSignatureVerifier.verify(SECRET, "id", ts, null, "{}")).isFalse();
        assertThat(SvixSignatureVerifier.verify(SECRET, "id", ts, "v1,x", null)).isFalse();
    }

    @Test
    void verify_handlesSecretWithoutWhsecPrefix() {
        String secretWithoutPrefix = SECRET.substring(6);
        String id = "msg_abc";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body = "{}";
        String sig = sign(SECRET, id, ts, body);

        // Verifier should accept the secret either way.
        assertThat(SvixSignatureVerifier.verify(secretWithoutPrefix, id, ts, sig, body)).isTrue();
    }

    /** Mirrors the Svix signing algorithm so tests can produce valid signatures. */
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
}
