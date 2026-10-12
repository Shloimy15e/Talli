package dev.dynamiq.talli.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.controller.ReportController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ReportOverviewTest {
    private ReportQueryServiceTest fixture;
    private ReportController controller;
    private ReportAccessService access;

    @BeforeEach void setUp() {
        fixture = new ReportQueryServiceTest();
        fixture.setUp();
        access = new ReportAccessService(fixture.service, new ObjectMapper(), "https://talli.test");
        controller = new ReportController(fixture.service, access);
    }

    @Test void currentBalancesAndCompletedWorkRemainVisibleOutsideSelectedPeriod() {
        var model = new ExtendedModelMap();
        assertThat(controller.index("2026-02-01", "2026-02-28", null, model)).isEqualTo("reports/index");
        assertThat(amount(rows(model, "outstanding"), "USD", "sum_balance")).isEqualByComparingTo("115");
        assertThat(amount(rows(model, "overdue"), "USD", "sum_balance")).isEqualByComparingTo("65");
        assertThat(amount(rows(model, "receipts"), "ILS", "sum_amount")).isEqualByComparingTo("360");
        assertThat(rows(model, "receipts")).hasSize(1);
        assertThat(amount(rows(model, "expenses"), "ILS", "sum_amount")).isEqualByComparingTo("36");
        assertThat(amount(rows(model, "unbilled"), "USD", "sum_value")).isEqualByComparingTo("80");
        assertThat(amount(rows(model, "unbilled"), "USD", "sum_hours")).isEqualByComparingTo("1");
        assertThat(rows(model, "overdueInvoices")).hasSize(1);
        assertThat(rows(model, "unbilledProjects")).hasSize(1);
    }

    @Test void detailLinksExplainEachMetricAndDirectReceiptsExcludeCreditApplications() {
        var model = new ExtendedModelMap();
        controller.index("2026-01-01", "2026-12-31", 12, model);
        assertThat(amount(rows(model, "receipts"), "USD", "sum_amount")).isEqualByComparingTo("20");
        assertThat(rows(model, "receipts")).hasSize(2);
        var receipts = linkedQuery(model, "receiptsUrl");
        assertThat(receipts.from()).isEqualTo("2026-01-01");
        assertThat(receipts.to()).isEqualTo("2026-12-31");
        assertThat(receipts.where().filters()).contains(new ReportQuery.Filter("source", "eq", List.of("direct")));
        assertThat(receipts.metrics()).isEmpty();
        var work = linkedQuery(model, "unbilledUrl");
        assertThat(work.from()).isEqualTo("1900-01-01");
        assertThat(work.where().filters()).contains(new ReportQuery.Filter("rateType", "eq", List.of("hourly")),
                new ReportQuery.Filter("running", "eq", List.of("false")), new ReportQuery.Filter("billable", "eq", List.of("true")),
                new ReportQuery.Filter("billed", "eq", List.of("false")));
        var outstanding = linkedQuery(model, "outstandingUrl");
        assertThat(outstanding.from()).isEqualTo("1900-01-01");
        assertThat(outstanding.where().filters()).contains(new ReportQuery.Filter("outstanding", "eq", List.of("true")));
        assertThat(linkedQuery(model, "overdueUrl").where().filters()).anyMatch(f -> f.field().equals("dueAt") && f.operator().equals("lte"));
    }

    @Test void invalidPeriodsAndLegacyMonthsAreRejected() {
        assertThatThrownBy(() -> controller.index(null, null, 0, new ExtendedModelMap())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.index("2026-03-01", "2026-02-01", null, new ExtendedModelMap())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.index("bad", null, null, new ExtendedModelMap())).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(ExtendedModelMap model, String name) {
        return (List<Map<String, Object>>) model.get(name);
    }
    private BigDecimal amount(List<Map<String, Object>> rows, String currency, String key) {
        return new BigDecimal(rows.stream().filter(r -> currency.equals(r.get("currency"))).findFirst().orElseThrow().get(key).toString());
    }
    private ReportQuery linkedQuery(ExtendedModelMap model, String name) {
        String url = model.get(name).toString();
        return access.parse(URLDecoder.decode(url.substring(url.indexOf("?q=") + 3), StandardCharsets.UTF_8));
    }
}
