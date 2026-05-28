package dev.dynamiq.talli.service.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class GithubAppTokenService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String appId;
    private final String privateKeyPem;
    private final String apiVersion;
    private final Map<Long, CachedToken> tokenCache = new HashMap<>();

    public GithubAppTokenService(ObjectMapper objectMapper,
                                 @Value("${app.github.app-id:}") String appId,
                                 @Value("${app.github.private-key:}") String privateKeyPem,
                                 @Value("${app.github.api-version:2022-11-28}") String apiVersion) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.appId = appId;
        this.privateKeyPem = privateKeyPem;
        this.apiVersion = apiVersion;
    }

    public String appJwt() {
        ensureConfigured();
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
            Map<String, Object> payload = Map.of(
                    "iat", now.minusSeconds(60).getEpochSecond(),
                    "exp", now.plusSeconds(540).getEpochSecond(),
                    "iss", appId
            );

            String unsigned = base64Url(objectMapper.writeValueAsBytes(header))
                    + "."
                    + base64Url(objectMapper.writeValueAsBytes(payload));

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(privateKeyPem));
            signature.update(unsigned.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return unsigned + "." + base64Url(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Could not create GitHub App JWT: " + e.getMessage(), e);
        }
    }

    public synchronized String installationToken(Long installationId) {
        ensureConfigured();
        if (installationId == null) {
            throw new IllegalStateException("GitHub installation id is missing.");
        }

        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(120))) {
            return cached.token();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/app/installations/" + installationId + "/access_tokens"))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + appJwt())
                    .header("X-GitHub-Api-Version", apiVersion)
                    .header("User-Agent", "Talli")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GithubApiException(response.statusCode(), errorMessage(response.body()));
            }

            JsonNode json = objectMapper.readTree(response.body());
            String token = json.path("token").asText();
            Instant expiresAt = Instant.parse(json.path("expires_at").asText());
            tokenCache.put(installationId, new CachedToken(token, expiresAt));
            return token;
        } catch (GithubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create GitHub installation token: " + e.getMessage(), e);
        }
    }

    private void ensureConfigured() {
        if (appId == null || appId.isBlank() || privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalStateException("GitHub App credentials are not configured.");
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json.hasNonNull("message")) {
                return json.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n").trim();
        String base64 = normalized
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] der = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        if (normalized.contains("BEGIN PRIVATE KEY")) {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        }

        RSAPrivateCrtKeySpec spec = parsePkcs1PrivateKey(der);
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) keyFactory.generatePrivate(spec);
        return key;
    }

    private static RSAPrivateCrtKeySpec parsePkcs1PrivateKey(byte[] der) {
        DerReader reader = new DerReader(der);
        DerReader sequence = reader.sequence();
        sequence.integer(); // version
        BigInteger modulus = sequence.integer();
        BigInteger publicExponent = sequence.integer();
        BigInteger privateExponent = sequence.integer();
        BigInteger primeP = sequence.integer();
        BigInteger primeQ = sequence.integer();
        BigInteger primeExponentP = sequence.integer();
        BigInteger primeExponentQ = sequence.integer();
        BigInteger crtCoefficient = sequence.integer();
        return new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent,
                primeP, primeQ, primeExponentP, primeExponentQ, crtCoefficient);
    }

    private record CachedToken(String token, Instant expiresAt) {
    }

    private static class DerReader {
        private final byte[] bytes;
        private int pos;

        private DerReader(byte[] bytes) {
            this.bytes = bytes;
        }

        DerReader sequence() {
            expect(0x30);
            int length = length();
            byte[] value = java.util.Arrays.copyOfRange(bytes, pos, pos + length);
            pos += length;
            return new DerReader(value);
        }

        BigInteger integer() {
            expect(0x02);
            int length = length();
            byte[] value = java.util.Arrays.copyOfRange(bytes, pos, pos + length);
            pos += length;
            return new BigInteger(1, value);
        }

        private void expect(int expected) {
            int actual = bytes[pos++] & 0xff;
            if (actual != expected) {
                throw new IllegalArgumentException("Invalid DER private key.");
            }
        }

        private int length() {
            int first = bytes[pos++] & 0xff;
            if (first < 128) {
                return first;
            }
            int count = first & 0x7f;
            int length = 0;
            for (int i = 0; i < count; i++) {
                length = (length << 8) | (bytes[pos++] & 0xff);
            }
            return length;
        }
    }
}
