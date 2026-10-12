package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.reporting.*;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@PreAuthorize("hasAuthority('view-reports')")
public class TalliMcpReportTools {
    private final ReportQueryService reports;
    private final ReportAccessService access;

    public TalliMcpReportTools(ReportQueryService reports, ReportAccessService access) {
        this.reports = reports;
        this.access = access;
    }

    @McpTool(name = "get_report_catalog", title = "Explore report fields",
            description = "Discover reporting datasets, fields, types, default columns and accounting meanings before building a report. Use build_report for nested filters, grouping, selected columns, full filtered totals, pagination and authenticated browser/Excel/CSV links. Invoice/payment project fields are filters only to avoid attributing a whole invoice to one project.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public Catalog catalog() {
        return new Catalog(reports.catalog(), List.of("eq", "ne", "in", "contains", "gte", "lte", "is_null", "is_not_null"),
                List.of("sum", "count", "avg", "min", "max"),
                "Dates from/to are inclusive ISO dates; defaults are year to date. where has match all/any, filters and nested groups (maximum depth 3, 50 filters). Filter values are arrays of strings. groupBy selects dimensions; metrics use field/function and return function_field columns. Sort direction is asc/desc. limit is 1-500, default 50. Totals cover all filtered data, not only the page. Money stays separated by currency; amountUsd uses captured exchange rates. Links are live queries with fixed date ranges, require login, and grant no additional access. Exports include all matching rows up to 50,000; narrow larger queries. Use payments source=direct for cash receipts; source=credit is credit applied, not new cash. Existing run_report provides combined operational summaries.");
    }

    @McpTool(name = "build_report", title = "Build a detailed Talli report",
            description = "Run a structured report query discovered with get_report_catalog. Returns selected columns, paginated rows, all-filter totals, notes, nextOffset and reportUrl/csvUrl/xlsxUrl. Share reportUrl to let a bookkeeper inspect and adjust the same report. Supports nested AND/OR filters, client/project/status/category/date/currency filters, groupings and numeric metrics. Use totals instead of reading every row for summaries. Query has dataset, from, to, columns, where {match,filters [{field,operator,values}],groups}, groupBy, metrics [{field,function}], sort [{field,direction}], offset, limit. Monetary groupings retain currency. Run_report remains available for combined financial summaries.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ReportAccessService.LinkedResult build(
            @McpToolParam(description = "Structured query using the discovered dataset and field keys. Omit optional fields for defaults.", required = true) ReportQuery query) {
        return access.run(query);
    }

    public record Catalog(List<ReportCatalog.Dataset> datasets, List<String> operators,
                          List<String> functions, String instructions) {}
}
