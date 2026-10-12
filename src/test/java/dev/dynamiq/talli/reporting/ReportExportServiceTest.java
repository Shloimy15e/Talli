package dev.dynamiq.talli.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportExportServiceTest {
    private final ReportQueryService reports = mock(ReportQueryService.class);
    private final ReportAccessService access = new ReportAccessService(reports, new ObjectMapper(), "https://talli.example");
    private final ReportExportService exports = new ReportExportService(reports, access);
    private final ReportQuery query = new ReportQuery("invoices", "2026-01-01", "2026-12-31",
            List.of("reference", "amount"), null, List.of(), List.of(), List.of(), 50, 50);
    private final List<ReportQueryService.Column> columns = List.of(
            new ReportQueryService.Column("reference", "Reference", "string"),
            new ReportQueryService.Column("amount", "Amount", "number"));

    @Test
    void csvEscapesTextAndFormulasAndKeepsNumericNegativesAndUnicode() {
        byte[] csv = ReportExportService.csv(columns, List.of(
                Map.of("reference", "  =HYPERLINK(\"bad\")", "amount", new BigDecimal("-12.50")),
                Map.of("reference", "שלום,\ninvoice", "amount", new BigDecimal("80.00"))));
        assertThat(new String(csv, StandardCharsets.UTF_8)).startsWith("\uFEFF\"Reference\",\"Amount\"\r\n")
                .contains("\"'  =HYPERLINK(\"\"bad\"\")\",\"-12.50\"")
                .contains("\"שלום,\ninvoice\",\"80.00\"");
    }

    @Test
    void exportStartsAtFirstRowAndIncludesEveryPageWithNumericExcelCells() throws Exception {
        when(reports.normalize(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reports.run(any())).thenAnswer(invocation -> {
            ReportQuery page = invocation.getArgument(0);
            assertThat(page.limit()).isEqualTo(500);
            assertThat(page.offset()).isZero();
            return new ReportQueryService.Result(page, columns,
                    List.of(Map.of("reference", "=danger", "amount", new BigDecimal("80.25"))),
                    2, 1, Map.of("rowCount", 2, "currencies", Map.of("USD", Map.of("amount", new BigDecimal("160.50")))), List.of("Amounts in USD"));
        });
        when(reports.rows(any())).thenAnswer(invocation -> {
            ReportQuery page = invocation.getArgument(0);
            assertThat(page.offset()).isEqualTo(1);
            return List.of(Map.of("reference", "Invoice two", "amount", new BigDecimal("80.25")));
        });
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exports.export(query, "xlsx")))) {
            var sheet = workbook.getSheet("Report");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("=danger");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(80.25);
            assertThat(sheet.getRow(2).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            var details = workbook.getSheet("Report details");
            assertThat(details).isNotNull();
            assertThat(details.getRow(6).getCell(0).getStringCellValue()).isEqualTo("Amount (USD)");
            assertThat(details.getRow(6).getCell(1).getNumericCellValue()).isEqualTo(160.50);
        }
        verify(reports, times(1)).run(any());
        verify(reports, times(1)).rows(any());
    }

    @Test
    void refusesOversizedExportInsteadOfSilentlyTruncating() {
        when(reports.normalize(any())).thenReturn(query);
        when(reports.run(any())).thenReturn(new ReportQueryService.Result(query, columns, List.of(),
                50001, 500, Map.of(), List.of()));
        assertThatThrownBy(() -> exports.export(query, "csv")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50,000");
        verify(reports, times(1)).run(any());
    }
}
