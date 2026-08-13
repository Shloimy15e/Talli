package dev.dynamiq.talli.integration.mercury;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MercuryPropertiesTest {

    private final MercuryProperties properties = new MercuryProperties(
            true,
            "api-key",
            "https://api.mercury.com/api/v1/",
            "webhook-secret");

    @Test
    void trimsConfigurationAndRecognizesExpenseSync() {
        assertThat(properties.baseUrl()).isEqualTo("https://api.mercury.com/api/v1");
        assertThat(properties.isApiConfigured()).isTrue();
        assertThat(properties.isExpenseSyncConfigured()).isTrue();
    }
}
