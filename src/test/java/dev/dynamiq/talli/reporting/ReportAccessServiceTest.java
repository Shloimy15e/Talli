package dev.dynamiq.talli.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReportAccessServiceTest {
    private final ReportQueryService reports = new ReportQueryService(mock(JdbcTemplate.class));
    private final ReportAccessService access = new ReportAccessService(reports, new ObjectMapper(), "https://talli.example/");

    @Test
    void linkRoundTripsNestedFiltersColumnsAndGroupingWithoutLosingState() {
        var query = access.parse("""
                {"dataset":"expenses","from":"2026-01-01","to":"2026-10-13","groupBy":["clientName"],
                 "metrics":[{"field":"amount","function":"sum"}],
                 "where":{"match":"all","filters":[{"field":"currency","operator":"eq","values":["USD"]}],
                  "groups":[{"match":"any","filters":[{"field":"vendor","operator":"contains","values":["A & B"]},
                  {"field":"category","operator":"eq","values":["software"]}]}]}}
                """);
        var links = access.links(query);
        assertThat(links.reportUrl()).startsWith("https://talli.example/reports/builder?q=");
        String encoded = links.reportUrl().split("\\?q=", 2)[1];
        assertThat(access.parse(URLDecoder.decode(encoded, StandardCharsets.UTF_8))).isEqualTo(query);
        assertThat(query.groupBy()).contains("currency");
        assertThat(links.csvUrl()).contains("/reports/export?format=csv&q=");
    }

    @Test
    void rejectsUnknownFieldsAndTrailingJson() {
        for (String input : List.of("{\"dataset\":\"invoices\",\"rawSql\":\"DROP TABLE invoices\"}",
                "{\"dataset\":\"invoices\"} {}")) {
            assertThatThrownBy(() -> access.parse(input)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
