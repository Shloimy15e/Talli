package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.EmailSenderProfile;
import dev.dynamiq.talli.service.EmailSenderProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailSenderProfileControllerTest {

    @Test
    void indexShowsAllProfilesAndCountsActiveProfiles() {
        EmailSenderProfileService service = mock(EmailSenderProfileService.class);
        EmailSenderProfile active = profile(true);
        EmailSenderProfile disabled = profile(false);
        when(service.allProfiles()).thenReturn(List.of(active, disabled));
        var controller = new EmailSenderProfileController(service);
        var model = new ConcurrentModel();

        assertThat(controller.index(model)).isEqualTo("emails/senders/index");
        assertThat(model.getAttribute("profiles")).isEqualTo(List.of(active, disabled));
        assertThat(model.getAttribute("activeProfileCount")).isEqualTo(1L);
    }

    @Test
    void createConvertsPlainTextSignatureToSafeHtml() {
        EmailSenderProfileService service = mock(EmailSenderProfileService.class);
        var controller = new EmailSenderProfileController(service);

        String view = controller.create(
                "billing@dynamiq.dev", "Dynamiq Billing", "Thanks & regards\nBilling",
                "text", false, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/emails/senders");
        verify(service).create("billing@dynamiq.dev", "Dynamiq Billing",
                "Thanks &amp; regards<br>\nBilling", false);
    }

    @Test
    void updatePreservesHtmlSignature() {
        EmailSenderProfileService service = mock(EmailSenderProfileService.class);
        var controller = new EmailSenderProfileController(service);
        String signature = "<p><strong>Dynamiq Billing</strong></p>";

        String view = controller.update(
                12L, "billing@dynamiq.dev", "Dynamiq Billing", signature,
                "html", true, true, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/emails/senders");
        verify(service).update(12L, "billing@dynamiq.dev", "Dynamiq Billing",
                signature, true, true);
    }

    @Test
    void failedCreateKeepsTheSubmittedSignatureDraft() {
        EmailSenderProfileService service = mock(EmailSenderProfileService.class);
        doThrow(new IllegalArgumentException("A sender profile already uses this email address."))
                .when(service).create("billing@dynamiq.dev", "Dynamiq Billing",
                        "<p>Billing</p>", false);
        var controller = new EmailSenderProfileController(service);
        var flash = new RedirectAttributesModelMap();

        controller.create("billing@dynamiq.dev", "Dynamiq Billing", "<p>Billing</p>",
                "html", false, flash);

        assertThat(flash.getFlashAttributes().get("draft"))
                .isEqualTo(new EmailSenderProfileController.Draft(
                        "billing@dynamiq.dev", "Dynamiq Billing", "<p>Billing</p>",
                        "html", false, true));
    }

    private static EmailSenderProfile profile(boolean active) {
        EmailSenderProfile profile = new EmailSenderProfile();
        profile.setActive(active);
        return profile;
    }
}
