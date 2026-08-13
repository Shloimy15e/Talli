package dev.dynamiq.talli.integration.mercury;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class MercurySignatureVerifier {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private MercurySignatureVerifier() {
    }

    public static boolean verify(String secret, String signatureHeader, String body) {
        return verify(secret, signatureHeader, body, Clock.systemUTC());
    }

    static boolean verify(String secret, String signatureHeader, String body, Clock clock) {
        if (secret == null || secret.isBlank() || signatureHeader == null || body == null) return false;

        try {
            String timestamp = null;
            for (String part : signatureHeader.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals("t")) timestamp = pair[1];
            }
            if (timestamp == null) return false;

            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            if (Duration.between(signedAt, clock.instant()).abs().compareTo(TOLERANCE) > 0) return false;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));

            for (String part : signatureHeader.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2 || !pair[0].equals("v1")) continue;
                byte[] candidate = HexFormat.of().parseHex(pair[1]);
                if (MessageDigest.isEqual(expected, candidate)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
