package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSenderTest {

    @Test
    void quotesAndEscapesDisplayNamesThatContainHeaderDelimiters() {
        assertThat(new EmailSender("john@dynamiq.dev", "Smith, John", "").formatted())
                .isEqualTo("\"Smith, John\" <john@dynamiq.dev>");
        assertThat(new EmailSender("jane@dynamiq.dev", "Jane \"JJ\" Doe", "").formatted())
                .isEqualTo("\"Jane \\\"JJ\\\" Doe\" <jane@dynamiq.dev>");
        assertThat(new EmailSender("ops@dynamiq.dev", "Ops\\Billing", "").formatted())
                .isEqualTo("\"Ops\\\\Billing\" <ops@dynamiq.dev>");
        assertThat(new EmailSender("support@dynamiq.dev", "Support (US)", "").formatted())
                .isEqualTo("\"Support (US)\" <support@dynamiq.dev>");
        assertThat(new EmailSender("billing@dynamiq.dev", "Billing.Team", "").formatted())
                .isEqualTo("\"Billing.Team\" <billing@dynamiq.dev>");
    }
}
