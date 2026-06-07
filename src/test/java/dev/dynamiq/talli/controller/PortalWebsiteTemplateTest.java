package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.service.website.WebsiteEditorBlock;
import dev.dynamiq.talli.service.website.WebsiteEditorField;
import dev.dynamiq.talli.service.website.WebsiteEditorForm;
import dev.dynamiq.talli.service.website.WebsiteEditorRepeat;
import dev.dynamiq.talli.service.website.WebsiteEditorRepeatItem;
import dev.dynamiq.talli.service.website.WebsiteEditorSection;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPart;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    void websiteEditorTemplateRendersWithMinimalContent() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        engine.setTemplateResolver(resolver);

        Project project = new Project();
        project.setId(1L);
        project.setName("Northlight");
        project.setWebsitePublicUrl("https://example.com");

        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/portal/projects/1/website");
        MockHttpServletResponse response = new MockHttpServletResponse();
        WebContext context = new WebContext(
                JakartaServletWebApplication.buildApplication(servletContext).buildExchange(request, response),
                Locale.US,
                Map.of("project", project, "form", emptyWebsiteForm())
        );

        String html = engine.process("portal/website", context);

        assertThat(html).contains("Northlight website editor");
        assertThat(html).contains("data-section-target=\"home\"");
        assertThat(html).contains("data-repeat-list");
        assertThat(html).contains("/js/website-editor.js");
        assertThat(html).contains("Publish changes");
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

    private WebsiteEditorForm emptyWebsiteForm() {
        return new WebsiteEditorForm(List.of(
                new WebsiteEditorSection("home", "Home page", "First impression", "home", List.of(
                        WebsiteEditorBlock.fields("two-column", List.of(
                                WebsiteEditorField.text("homeHeroHeadline", "Main headline", "Old headline", "", "half")
                                        .required("Home page main headline is empty."),
                                WebsiteEditorField.email("contactEmail", "Email address", "info@example.com", "", "half")
                                        .required("Contact email is empty.")
                        )),
                        WebsiteEditorBlock.repeat("Services", "", new WebsiteEditorRepeat(
                                "Service",
                                "Add service",
                                List.of(WebsiteEditorField.text("serviceTitle", "Title", "", "", "full")
                                        .required("Service {number} needs a title.")),
                                List.of(new WebsiteEditorRepeatItem(List.of(
                                        WebsiteEditorField.text("serviceTitle", "Title", "Advisory", "", "full")
                                                .required("Service {number} needs a title.")
                                )))
                        ))
                ))
        ));
    }
}
