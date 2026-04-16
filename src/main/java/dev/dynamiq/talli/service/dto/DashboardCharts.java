package dev.dynamiq.talli.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DashboardCharts {

    public record DayMinutes(LocalDate day, int minutes) {}

    public record ProjectMinutes(String project, int minutes) {}

    public record MonthFinancials(String month, BigDecimal invoiced, BigDecimal received, BigDecimal expenses, BigDecimal net) {}

    public record BillableBreakdown(int billable, int nonBillable) {}

    private DashboardCharts() {}
}
