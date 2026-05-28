package dev.dynamiq.talli.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatNoException;
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

    @Test
    void tomcatPartLimitAllowsLargeWebsiteEditorMultipartForm() throws Exception {
        int expectedMultipartParts = 500;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/portal/projects/1/website");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        List<MockPart> formParts = IntStream.range(0, expectedMultipartParts)
                .mapToObj(index -> new MockPart("field_" + index, "value".getBytes(StandardCharsets.UTF_8)))
                .toList();
        formParts.forEach(request::addPart);

        assertThat(formParts).hasSize(expectedMultipartParts);
        assertThat(configuredMaxPartCount()).isGreaterThanOrEqualTo(formParts.size());
        assertThatNoException().isThrownBy(() -> {
            MultipartHttpServletRequest multipartRequest = new StandardServletMultipartResolver()
                    .resolveMultipart(request);
            assertThat(multipartRequest.getParts()).hasSize(expectedMultipartParts);
        });
    }

    private int configuredMaxPartCount() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        return Integer.parseInt(properties.getProperty("server.tomcat.max-part-count"));
    }
}
