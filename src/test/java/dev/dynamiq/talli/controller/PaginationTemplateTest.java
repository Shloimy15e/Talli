package dev.dynamiq.talli.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationTemplateTest {
    private final SpringTemplateEngine engine = templateEngine();

    @Test
    void firstMiddleAndLastPagesHaveABoundedWindowAndCorrectRanges() {
        String first = render(0, 792);
        assertThat(first).contains("Showing 1–30 of 792", "aria-disabled=\"true\">Prev",
                "aria-label=\"Page 1\"", "aria-label=\"Page 27\"", "page=1\"");
        assertThat(first).doesNotContain("page=-1", "aria-label=\"Page 14\"");

        String middle = render(13, 792);
        assertThat(middle).contains("Showing 391–420 of 792", "aria-label=\"Page 13\"",
                "aria-label=\"Page 14\"", "aria-label=\"Page 15\"", "page=12\"", "page=14\"");
        assertThat(middle).doesNotContain("aria-disabled");
        assertThat(count(middle, "class=\"app-page-ellipsis\"")).isEqualTo(2);

        String last = render(26, 792);
        assertThat(last).contains("Showing 781–792 of 792", "aria-disabled=\"true\">Next", "page=25\"");
        assertThat(last).doesNotContain("page=27\"");

        for (String html : new String[]{first, middle, last}) {
            assertThat(count(html, "aria-current=\"page\"")).isEqualTo(1);
            assertThat(count(html, "class=\"app-page-link")).isLessThanOrEqualTo(7);
            assertThat(count(html, "aria-label=\"Page ")).isLessThanOrEqualTo(5);
        }
    }

    @Test
    void emptyAndSinglePageResultsShowAnAccurateSummaryWithoutNavigation() {
        assertThat(render(0, 0)).contains("Showing 0–0 of 0").doesNotContain("<nav", "app-page-link");
        assertThat(render(0, 12)).contains("Showing 1–12 of 12").doesNotContain("<nav", "app-page-link");
    }

    @Test
    void twoPagesRenderEachEndpointOnceWithoutAnEllipsis() {
        String html = render(0, 31);
        assertThat(count(html, "aria-label=\"Page 1\"")).isEqualTo(1);
        assertThat(count(html, "aria-label=\"Page 2\"")).isEqualTo(1);
        assertThat(html).doesNotContain("app-page-ellipsis");
    }

    @Test
    void mobileKeepsEndpointsAndCurrentPageAndMarksOmittedGaps() {
        String middle = render(13, 792);
        assertThat(count(middle, "app-page-neighbor")).isEqualTo(2);
        assertThat(middle).contains("class=\"app-page-link\" aria-current=\"page\"");

        String nearFirst = render(2, 792);
        assertThat(nearFirst).contains("class=\"app-page-ellipsis app-page-ellipsis-mobile\"");
        String nearLast = render(24, 792);
        assertThat(nearLast).contains("class=\"app-page-ellipsis app-page-ellipsis-mobile\"");
    }

    @Test
    void everyConsumerPreservesItsFiltersWhenAddingThePageParameter() throws IOException {
        Map<String, Object> variables = new HashMap<>();
        var page = page(13, 792);
        variables.put("page", page);
        variables.put("view", Map.of("page", page));
        variables.put("filterStatuses", new String[]{"open", "paid"});
        variables.put("filterClientIds", new Long[]{1L, 2L});
        variables.put("filterProjectIds", new Long[]{3L, 4L});
        variables.put("filterSearch", "North & Co");
        variables.put("filter", Map.of("search", "North & Co", "clientId", 1, "projectId", 3,
                "category", "travel", "source", "manual", "billing", "unbilled", "from", "2026-10-01", "to", "2026-10-07"));

        for (String consumer : new String[]{"invoices", "projects", "time", "expenses"}) {
            String source = Files.readString(Path.of("src/main/resources/templates", consumer, "index.html"));
            var match = Pattern.compile("<div th:replace=\"~\\{fragments/pagination[^\"]+\"></div>").matcher(source);
            assertThat(match.find()).as("%s uses the shared pagination", consumer).isTrue();
            String html = engine.process(match.group(), context(variables));
            assertThat(html).contains("href=\"/" + consumer + "?", "&amp;page=12\"", "&amp;page=14\"");
            assertThat(html).doesNotContain("?page=", "&amp;amp;");
            if (!consumer.equals("expenses")) {
                assertThat(html).contains("status=open", "status=paid");
            }
            if (!consumer.equals("time")) {
                assertThat(html).contains("search=North%20%26%20Co");
            }
            if (consumer.equals("projects") || consumer.equals("time")) {
                assertThat(html).contains("clientId=1", "clientId=2");
            }
            if (consumer.equals("time")) {
                assertThat(html).contains("projectId=3", "projectId=4");
            }
            if (consumer.equals("expenses")) {
                assertThat(html).contains("clientId=1", "projectId=3", "category=travel", "source=manual",
                        "billing=unbilled", "from=2026-10-01", "to=2026-10-07");
            }
        }
    }

    private String render(int number, int total) {
        return engine.process("<div th:replace=\"~{fragments/pagination :: pagination(${page}, '/projects')}\"></div>",
                context(Map.of("page", page(number, total))));
    }

    private PageImpl<Integer> page(int number, int total) {
        int size = Math.min(30, Math.max(0, total - number * 30));
        return new PageImpl<>(Collections.nCopies(size, 1), PageRequest.of(number, 30), total);
    }

    private WebContext context(Map<String, Object> variables) {
        var servletContext = new MockServletContext();
        var request = new MockHttpServletRequest(servletContext, "GET", "/projects");
        return new WebContext(JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse()), Locale.US, variables);
    }

    private long count(String html, String value) {
        return Pattern.compile(Pattern.quote(value)).matcher(html).results().count();
    }

    private SpringTemplateEngine templateEngine() {
        var inline = new StringTemplateResolver();
        inline.setResolvablePatterns(Set.of("<*"));
        inline.setOrder(1);
        var files = new ClassLoaderTemplateResolver();
        files.setPrefix("templates/");
        files.setSuffix(".html");
        files.setOrder(2);
        var engine = new SpringTemplateEngine();
        engine.addTemplateResolver(inline);
        engine.addTemplateResolver(files);
        return engine;
    }
}
