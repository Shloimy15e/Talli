package dev.dynamiq.talli.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PortalWebsiteTemplateTest {

    @Test
    void multipartPublishFormIncludesCsrfTokenInActionUrl() throws Exception {
        String template = new String(getClass()
                .getResourceAsStream("/templates/portal/website.html")
                .readAllBytes(), StandardCharsets.UTF_8);

        assertThat(template).contains("enctype=\"multipart/form-data\"");
        assertThat(template).contains("_csrf=${_csrf.token}");
    }
}
