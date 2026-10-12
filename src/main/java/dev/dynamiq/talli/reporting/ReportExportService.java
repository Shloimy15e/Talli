package dev.dynamiq.talli.reporting;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReportExportService {
    private static final int MAX_ROWS = 50000;
    private final ReportQueryService reports;
    private final ReportAccessService access;

    public ReportExportService(ReportQueryService reports, ReportAccessService access) {
        this.reports = reports;
        this.access = access;
    }

    /** A single database snapshot keeps all exported pages and totals consistent. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] export(ReportQuery query, String format) throws IOException {
        if (!List.of("csv", "xlsx").contains(format)) {
            throw new IllegalArgumentException("Choose CSV or Excel export.");
        }
        var normalized = reports.normalize(query);
        var first = reports.run(page(normalized, 0));
        if (first.totalRows() > MAX_ROWS) {
            throw new IllegalArgumentException("Export supports up to 50,000 rows. Narrow the date range or filters and export again.");
        }
        List<Map<String, Object>> rows = new ArrayList<>(first.rows());
        while (rows.size() < first.totalRows()) {
            var batch = reports.rows(page(normalized, rows.size()));
            if (batch.isEmpty()) throw new IllegalStateException("Report pagination ended before all rows were exported.");
            rows.addAll(batch);
            if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("Export exceeds 50,000 rows.");
        }
        return "csv".equals(format) ? csv(first.columns(), rows) : xlsx(first, rows);
    }

    private ReportQuery page(ReportQuery q, int offset) {
        return new ReportQuery(q.dataset(), q.from(), q.to(), q.columns(), q.where(),
                q.groupBy(), q.metrics(), q.sort(), offset, 500);
    }

    static byte[] csv(List<ReportQueryService.Column> columns, List<Map<String, Object>> rows) {
        StringBuilder out = new StringBuilder("\uFEFF");
        appendCsvRow(out, columns.stream().map(ReportQueryService.Column::label).toList());
        for (var row : rows) {
            appendCsvRow(out, columns.stream().map(c -> row.get(c.key())).toList());
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsvRow(StringBuilder out, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.append(',');
            Object value = cells.get(i);
            String text = display(value);
            // Text values are never executable spreadsheet formulas. Numeric negatives remain numbers.
            if (!(value instanceof Number) && !text.isEmpty()) {
                String leading = text.stripLeading();
                if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0
                        || text.charAt(0) == '\t' || text.charAt(0) == '\r' || text.charAt(0) == '\n') {
                    text = "'" + text;
                }
            }
            out.append('"').append(text.replace("\"", "\"\"")).append('"');
        }
        out.append("\r\n");
    }

    private byte[] xlsx(ReportQueryService.Result result, List<Map<String, Object>> rows) throws IOException {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Report");
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##########"));
            var moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            var header = sheet.createRow(0);
            for (int i = 0; i < result.columns().size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(result.columns().get(i).label());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 24 * 256);
            }
            int rowIndex = 1;
            for (var values : rows) {
                var row = sheet.createRow(rowIndex++);
                for (int i = 0; i < result.columns().size(); i++) {
                    var value = values.get(result.columns().get(i).key());
                    var cell = row.createCell(i);
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                        cell.setCellStyle(monetary(result.columns().get(i).key()) ? moneyStyle : numberStyle);
                    } else if (value instanceof Boolean bool) {
                        cell.setCellValue(bool);
                    } else {
                        String text = display(value);
                        if (text.length() > 32767) {
                            throw new IllegalArgumentException("A report value exceeds Excel's cell length. Use CSV to export its full contents.");
                        }
                        cell.setCellValue(text);
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            if (!result.columns().isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,
                        Math.max(0, rowIndex - 1), 0, result.columns().size() - 1));
            }
            var context = workbook.createSheet("Report details");
            context.setColumnWidth(0, 28 * 256);
            context.setColumnWidth(1, 100 * 256);
            int index = 0;
            index = detail(context, index, "Dataset", result.query().dataset());
            index = detail(context, index, "Generated (UTC)", Instant.now().toString());
            index = detail(context, index, "From / to (inclusive)", result.query().from() + " / " + result.query().to());
            index = detail(context, index, "Rows", Long.toString(result.totalRows()));
            index = detail(context, index, "Query", access.json(result.query()));
            index = detail(context, index, "Totals", "All matching records, across all pages");
            if (result.totals().get("numeric") instanceof Map<?, ?> numeric) {
                index = totalDetails(context, index, numeric, "", result.columns(), numberStyle, moneyStyle);
            }
            if (result.totals().get("currencies") instanceof Map<?, ?> currencies) {
                for (var entry : currencies.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> values) {
                        index = totalDetails(context, index, values, entry.getKey().toString(), result.columns(), numberStyle, moneyStyle);
                    }
                }
            }
            for (String note : result.notes()) index = detail(context, index, "Note", note);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private int detail(Sheet sheet, int index, String label, String value) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return index + 1;
    }

    private int totalDetails(Sheet sheet, int index, Map<?, ?> values, String currency,
                             List<ReportQueryService.Column> columns, CellStyle numberStyle, CellStyle moneyStyle) {
        for (var entry : values.entrySet()) {
            String key = entry.getKey().toString();
            String label = columns.stream().filter(c -> c.key().equals(key))
                    .map(ReportQueryService.Column::label).findFirst().orElse(key);
            var row = sheet.createRow(index++);
            row.createCell(0).setCellValue(label + (currency.isEmpty() ? "" : " (" + currency + ")"));
            var cell = row.createCell(1);
            if (entry.getValue() instanceof Number number) {
                cell.setCellValue(number.doubleValue());
                cell.setCellStyle(monetary(key) ? moneyStyle : numberStyle);
            } else {
                cell.setCellValue(display(entry.getValue()));
            }
        }
        return index;
    }

    private static boolean monetary(String key) {
        if (key.startsWith("count_")) return false;
        String field = key.replaceFirst("^(sum|avg|min|max)_", "");
        return List.of("amount", "amountUsd", "amountPaid", "amountWrittenOff", "balance", "rate", "value").contains(field);
    }

    private static String display(Object value) {
        return value == null ? "" : value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
    }
}
