package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEmailSenderCatalogTest {

    @Test
    void listsTheFixedDynamiqSendersWithInfoAsDefault() {
        var catalog = new AgentEmailSenderCatalog();

        assertThat(catalog.options())
                .extracting(AgentEmailSenderCatalog.Option::address)
                .containsExactly("info@dynamiq.dev", "billing@dynamiq.dev",
                        "finance@dynamiq.dev", "support@dynamiq.dev", "sales@dynamiq.dev");
        assertThat(catalog.options()).first()
                .extracting(AgentEmailSenderCatalog.Option::defaultSender)
                .isEqualTo(true);
        assertThat(catalog.options())
                .allSatisfy(option -> assertThat(option.defaultSignatureHtml())
                        .contains("Dynamiq", option.address()));
    }

    @Test
    void resolvesConfiguredSenderCaseInsensitivelyAndDefaultsWhenOmitted() {
        var catalog = new AgentEmailSenderCatalog();

        assertThat(catalog.resolve("BILLING@DYNAMIQ.DEV"))
                .isEqualTo(new EmailSender("billing@dynamiq.dev", "Dynamiq Billing"));
        assertThat(catalog.resolve(null))
                .isEqualTo(new EmailSender("info@dynamiq.dev", "Dynamiq Solutions"));
    }

    @Test
    void rejectsSenderOutsideAllowList() {
        var catalog = new AgentEmailSenderCatalog();

        assertThatThrownBy(() -> catalog.resolve("attacker@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sender_email must be one of", "info@dynamiq.dev",
                        "billing@dynamiq.dev");
    }
}
