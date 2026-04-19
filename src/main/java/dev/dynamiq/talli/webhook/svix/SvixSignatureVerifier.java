package dev.dynamiq.talli.webhook.svix;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies webhook signatures using the Svix scheme.
 * Providers that use Svix (Resend, Clerk, etc.) all produce signatures this way,
 * so this helper is provider-agnostic — just pass the secret from the right source.
 */
public class SvixSignatureVerifier {

    /** Default timestamp tolerance — reject events older than this to prevent replay. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private SvixSignatureVerifier() {}

    /**
     * @param secret    webhook signing secret (with or without "whsec_" prefix)
     * @param id        value of the svix-id header
     * @param timestamp value of the svix-timestamp header (unix seconds as string)
     * @param signature value of the svix-signature header (space-separated "v1,base64" entries)
     * @param body      raw request body
     * @return true if any provided signature matches
     */
    public static boolean verify(String secret, String id, String timestamp, String signature, String body) {
        return verify(secret, id, timestamp, signature, body, DEFAULT_TOLERANCE);
    }

    public static boolean verify(String secret, String id, String timestamp, String signature,
                                 String body, Duration tolerance) {
        if (secret == null || secret.isBlank() || id == null || timestamp == null
                || signature == null || body == null) {
            return false;
        }

        long ts;
        try { ts = Long.parseLong(timestamp.trim()); }
        catch (NumberFormatException e) { return false; }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > tolerance.toSeconds()) return false;

        byte[] key;
        try {
            String raw = secret.startsWith("whsec_") ? secret.substring(6) : secret;
            key = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] hash = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            expected = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }

        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        for (String part : signature.split(" ")) {
            int comma = part.indexOf(',');
            if (comma < 0) continue;
            if (!"v1".equals(part.substring(0, comma))) continue;
            byte[] candidate = part.substring(comma + 1).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expectedBytes, candidate)) return true;
        }
        return false;
    }
}
