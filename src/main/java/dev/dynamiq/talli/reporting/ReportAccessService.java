package dev.dynamiq.talli.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** The browser, MCP and downloads all use the same canonical query. */
@Service
public class ReportAccessService {
    private final ReportQueryService reports;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public ReportAccessService(ReportQueryService reports, ObjectMapper mapper,
                               @Value("${app.base-url}") String baseUrl) {
        this.reports = reports;
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public ReportQuery parse(String json) {
        if (json == null || json.isBlank()) {
            return reports.normalize(new ReportQuery("invoices", null, null, null, null,
                    null, null, null, null, null));
        }
        if (json.length() > 16000) throw new IllegalArgumentException("Report query is too long.");
        try {
            return reports.normalize(mapper.readerFor(ReportQuery.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid report query. Check its fields and values.");
        }
    }

    public String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Could not encode report query.", ex);
        }
    }

    public LinkedResult run(ReportQuery query) {
        var normalized = reports.normalize(query);
        // Check that this query has a usable link before doing database work.
        var links = links(normalized);
        var result = reports.run(normalized);
        return new LinkedResult(result.query(), result.columns(), result.rows(), result.totalRows(),
                result.nextOffset(), result.totals(), result.notes(), links.reportUrl(), links.csvUrl(), links.xlsxUrl());
    }

    public Links links(ReportQuery query) {
        var canonical = reports.normalize(query);
        String encoded = URLEncoder.encode(json(canonical), StandardCharsets.UTF_8);
        if (encoded.length() > 6000) {
            throw new IllegalArgumentException("This report is too large for a shareable link. Reduce the number or length of filter values.");
        }
        return new Links(baseUrl + "/reports/builder?q=" + encoded,
                baseUrl + "/reports/export?format=csv&q=" + encoded,
                baseUrl + "/reports/export?format=xlsx&q=" + encoded);
    }

    public record Links(String reportUrl, String csvUrl, String xlsxUrl) {}
    public record LinkedResult(ReportQuery query, List<ReportQueryService.Column> columns,
                               List<Map<String, Object>> rows, long totalRows, Integer nextOffset,
                               Map<String, Object> totals, List<String> notes,
                               String reportUrl, String csvUrl, String xlsxUrl) {}
}
