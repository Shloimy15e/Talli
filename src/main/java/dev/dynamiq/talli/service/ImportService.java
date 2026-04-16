package dev.dynamiq.talli.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses CSV and Excel (.xlsx) files into a list of header names + row maps.
 * The controller handles the column-mapping UI and actual persistence.
 */
@Service
public class ImportService {

    public record ParsedFile(List<String> headers, List<Map<String, String>> rows) {}

    public ParsedFile parse(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name != null && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            return parseExcel(file.getInputStream());
        }
        return parseCsv(file.getInputStream());
    }

    private ParsedFile parseExcel(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) return new ParsedFile(List.of(), List.of());

            Row headerRow = it.next();
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cellToString(cell, wb));
            }

            List<Map<String, String>> rows = new ArrayList<>();
            while (it.hasNext()) {
                Row row = it.next();
                if (isBlankRow(row)) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    map.put(headers.get(i), cellToString(cell, wb));
                }
                rows.add(map);
            }
            return new ParsedFile(headers, rows);
        }
    }

    private ParsedFile parseCsv(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return new ParsedFile(List.of(), List.of());

            // Detect delimiter: tab, semicolon, or comma
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitCsvLine(headerLine, delimiter);

            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> values = splitCsvLine(line, delimiter);
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    map.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                rows.add(map);
            }
            return new ParsedFile(headers, rows);
        }
    }

    private char detectDelimiter(String line) {
        if (line.contains("\t")) return '\t';
        if (line.contains(";")) return ';';
        return ',';
    }

    private List<String> splitCsvLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString().trim());
        return fields;
    }

    private String cellToString(Cell cell, Workbook wb) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Format dates as yyyy-MM-dd for consistent parsing
                    java.time.LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield date.toString();
                }
                // Avoid trailing .0 on whole numbers
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    yield String.valueOf((long) v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
                    CellValue cv = eval.evaluate(cell);
                    yield switch (cv.getCellType()) {
                        case STRING -> cv.getStringValue().trim();
                        case NUMERIC -> {
                            double fv = cv.getNumberValue();
                            if (fv == Math.floor(fv) && !Double.isInfinite(fv)) yield String.valueOf((long) fv);
                            yield String.valueOf(fv);
                        }
                        default -> "";
                    };
                } catch (Exception e) {
                    yield "";
                }
            }
            default -> "";
        };
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) {
                String val = cellToString(cell, row.getSheet().getWorkbook());
                if (!val.isBlank()) return false;
            }
        }
        return true;
    }
}
