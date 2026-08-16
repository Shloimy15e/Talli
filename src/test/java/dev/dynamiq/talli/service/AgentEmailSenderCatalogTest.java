package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEmailSenderCatalogTest {

    @Test
    void listsDefaultAndConfiguredSendersWithoutDuplicates() {
        var catalog = new AgentEmailSenderCatalog("info@dynamiq.dev", "Dynamiq",
                "billing@dynamiq.dev, info@dynamiq.dev,finance@dynamiq.dev");

        assertThat(catalog.options())
                .extracting(AgentEmailSenderCatalog.Option::address)
                .containsExactly("info@dynamiq.dev", "billing@dynamiq.dev", "finance@dynamiq.dev");
        assertThat(catalog.options()).first()
                .extracting(AgentEmailSenderCatalog.Option::defaultSender)
                .isEqualTo(true);
    }

    @Test
    void resolvesConfiguredSenderCaseInsensitivelyAndDefaultsWhenOmitted() {
        var catalog = new AgentEmailSenderCatalog("info@dynamiq.dev", "Dynamiq",
                "billing@dynamiq.dev");

        assertThat(catalog.resolve("BILLING@DYNAMIQ.DEV"))
                .isEqualTo(new EmailSender("billing@dynamiq.dev", "Dynamiq"));
        assertThat(catalog.resolve(null))
                .isEqualTo(new EmailSender("info@dynamiq.dev", "Dynamiq"));
    }

    @Test
    void rejectsSenderOutsideAllowList() {
        var catalog = new AgentEmailSenderCatalog("info@dynamiq.dev", "Dynamiq",
                "billing@dynamiq.dev");

        assertThatThrownBy(() -> catalog.resolve("attacker@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sender_email must be one of", "info@dynamiq.dev",
                        "billing@dynamiq.dev");
    }
}
