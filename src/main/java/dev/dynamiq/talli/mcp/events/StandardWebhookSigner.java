package dev.dynamiq.talli.mcp.events;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class StandardWebhookSigner {
    private StandardWebhookSigner() { }
    static byte[] key(String secret) {
        try {
            if (secret == null || !secret.startsWith("whsec_")) throw new IllegalArgumentException();
            byte[] key = Base64.getDecoder().decode(secret.substring(6));
            if (key.length < 24 || key.length > 64) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException e) {
            throw new McpEventException(-32602, "invalid_secret", "Signing secret must be whsec_ followed by 24-64 base64-encoded bytes");
        }
    }
    static String sign(String secret, String id, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(secret), "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
