package dev.dynamiq.talli.service.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubAppTokenServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void appJwtSignsGithubAppClaims() throws Exception {
        String pem = privateKeyPem();
        GithubAppTokenService service = new GithubAppTokenService(objectMapper, "12345", pem, "2022-11-28");

        String jwt = service.appJwt();
        String[] parts = jwt.split("\\.");

        assertThat(parts).hasSize(3);
        JsonNode header = decode(parts[0]);
        JsonNode payload = decode(parts[1]);

        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        assertThat(payload.path("iss").asText()).isEqualTo("12345");
        assertThat(payload.path("exp").asLong()).isGreaterThan(payload.path("iat").asLong());
    }

    @Test
    void appJwtRequiresConfiguredCredentials() {
        GithubAppTokenService service = new GithubAppTokenService(objectMapper, "", "", "2022-11-28");

        assertThatThrownBy(service::appJwt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub App credentials are not configured");
    }

    private JsonNode decode(String part) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(part);
        return objectMapper.readTree(bytes);
    }

    private String privateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        RSAPrivateKey privateKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
    }
}
