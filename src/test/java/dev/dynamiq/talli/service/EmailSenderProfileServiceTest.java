package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.EmailSenderProfile;
import dev.dynamiq.talli.repository.EmailSenderProfileRepository;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RefreshDatabaseTest
class EmailSenderProfileServiceTest {

    @Autowired
    private EmailSenderProfileService service;

    @Autowired
    private EmailSenderProfileRepository repository;

    @Test
    void firstProfileBecomesDefaultAndKeepsItsOwnSignature() {
        EmailSenderProfile created = service.create(
                "INFO@DYNAMIQ.DEV", "Dynamiq Solutions", "<p>Custom signature</p>", false);

        assertThat(created.getAddress()).isEqualTo("info@dynamiq.dev");
        assertThat(created.isDefaultSender()).isTrue();
        assertThat(service.resolve(null)).isEqualTo(new EmailSender(
                "info@dynamiq.dev", "Dynamiq Solutions", "<p>Custom signature</p>"));
    }

    @Test
    void blankSignatureGetsAnEscapedBasicSignature() {
        EmailSenderProfile created = service.create(
                "owner@dynamiq.dev", "Dynamiq & Partners", "  ", false);

        assertThat(created.getSignatureHtml())
                .isEqualTo("<strong>Dynamiq &amp; Partners</strong><br>"
                        + "<a href=\"mailto:owner@dynamiq.dev\">owner@dynamiq.dev</a>");
    }

    @Test
    void switchingDefaultLeavesExactlyOneActiveDefault() {
        EmailSenderProfile info = service.create(
                "info@dynamiq.dev", "Dynamiq Solutions", null, false);
        EmailSenderProfile billing = service.create(
                "billing@dynamiq.dev", "Dynamiq Billing", "<p>Billing</p>", false);

        service.update(billing.getId(), billing.getAddress(), billing.getName(),
                billing.getSignatureHtml(), true, true);

        assertThat(repository.findAll().stream()
                .filter(profile -> profile.isActive() && profile.isDefaultSender()))
                .singleElement()
                .extracting(EmailSenderProfile::getId)
                .isEqualTo(billing.getId());
        assertThat(repository.findById(info.getId()).orElseThrow().isDefaultSender()).isFalse();
    }

    @Test
    void rejectsCaseInsensitiveDuplicateAddressesEvenWhenOriginalIsDisabled() {
        EmailSenderProfile original = service.create(
                "billing@dynamiq.dev", "Dynamiq Billing", null, false);
        EmailSenderProfile replacementDefault = service.create(
                "info@dynamiq.dev", "Dynamiq Solutions", null, true);
        service.disable(original.getId());

        assertThatThrownBy(() -> service.create(
                "BILLING@DYNAMIQ.DEV", "Other Billing", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already uses");
        assertThat(replacementDefault.isDefaultSender()).isTrue();
    }

    @Test
    void preventsDisablingTheCurrentDefault() {
        EmailSenderProfile profile = service.create(
                "info@dynamiq.dev", "Dynamiq Solutions", null, false);

        assertThatThrownBy(() -> service.disable(profile.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Choose another default");
    }

    @Test
    void rejectsHeaderInjectionAndUnknownOrInactiveSenders() {
        assertThatThrownBy(() -> service.create(
                "safe@dynamiq.dev", "Safe\r\nBcc: victim@example.com", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");

        service.create("info@dynamiq.dev", "Dynamiq Solutions", null, false);
        EmailSenderProfile disabled = service.create(
                "support@dynamiq.dev", "Dynamiq Support", null, false);
        service.disable(disabled.getId());

        assertThatThrownBy(() -> service.resolve("support@dynamiq.dev"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active configured addresses", "info@dynamiq.dev");
    }
}
