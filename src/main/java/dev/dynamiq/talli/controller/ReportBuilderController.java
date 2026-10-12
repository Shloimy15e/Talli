package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.reporting.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAuthority('view-reports')")
public class ReportBuilderController {
    private final ReportQueryService reports;
    private final ReportAccessService access;
    private final ReportExportService exports;

    public ReportBuilderController(ReportQueryService reports, ReportAccessService access, ReportExportService exports) {
        this.reports = reports;
        this.access = access;
        this.exports = exports;
    }

    @GetMapping({"", "/builder"})
    public String builder(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("reportCatalogJson", access.json(reports.catalog()));
        model.addAttribute("initialReportQueryJson", access.json(access.parse(q)));
        return "reports/builder";
    }

    @GetMapping("/query")
    @ResponseBody
    public ResponseEntity<ReportAccessService.LinkedResult> query(@RequestParam(required = false) String q) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(access.run(access.parse(q)));
    }

    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> export(@RequestParam String q,
                                         @RequestParam(defaultValue = "xlsx") String format) throws IOException {
        var query = access.parse(q);
        byte[] data = exports.export(query, format);
        var type = "csv".equals(format) ? "text/csv;charset=UTF-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"talli-" + query.dataset() + "." + format + "\"")
                .body(data);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> invalidQuery(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
