package dev.dynamiq.talli.reporting;

import java.util.List;

public record ReportQuery(String dataset, String from, String to, List<String> columns,
        FilterGroup where, List<String> groupBy, List<Metric> metrics, List<Sort> sort,
        Integer offset, Integer limit) {
    public record FilterGroup(String match, List<Filter> filters, List<FilterGroup> groups) {}
    public record Filter(String field, String operator, List<String> values) {}
    public record Metric(String field, String function) {}
    public record Sort(String field, String direction) {}
}
