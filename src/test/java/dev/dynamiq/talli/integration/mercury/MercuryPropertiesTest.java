package dev.dynamiq.talli.integration.mercury;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MercuryPropertiesTest {

    private final MercuryProperties properties = new MercuryProperties(
            true,
            "api-key",
            "https://api.mercury.com/api/v1/",
            "account-id",
            false,
            true,
            false,
            false,
            "https://app.mercury.com/pay/");

    @Test
    void buildsPaymentLinkOnlyForPayableMercuryInvoices() {
        assertThat(properties.paymentUrl("pay-123", "Unpaid"))
                .isEqualTo("https://app.mercury.com/pay/pay-123");
        assertThat(properties.paymentUrl("pay-123", "Processing"))
                .isEqualTo("https://app.mercury.com/pay/pay-123");
        assertThat(properties.paymentUrl("pay-123", "Paid")).isNull();
        assertThat(properties.paymentUrl("pay-123", "Cancelled")).isNull();
    }
}
