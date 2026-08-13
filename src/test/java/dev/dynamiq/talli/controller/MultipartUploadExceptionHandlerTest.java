package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.service.EmailAttachmentPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartUploadExceptionHandlerTest {

    @Test
    void redirectsOversizedEmailUploadWithFriendlyMessage() {
        var handler = new MultipartUploadExceptionHandler(new EmailAttachmentPolicy("20MB", "25MB"));
        var request = new MockHttpServletRequest("POST", "/emails");
        var redirectAttributes = new RedirectAttributesModelMap();

        String view = handler.handleOversizedUpload(request, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/emails");
        assertThat(redirectAttributes.getFlashAttributes().get("error").toString())
                .contains("20 MB", "25 MB", "try again");
    }
}
