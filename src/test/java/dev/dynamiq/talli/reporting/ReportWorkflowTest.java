package dev.dynamiq.talli.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.mcp.TalliMcpReportTools;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import jakarta.persistence.EntityManager;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RefreshDatabaseTest
@AutoConfigureMockMvc
class ReportWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TalliMcpReportTools tools;
    @Autowired McpStatelessSyncServer server;
    @Autowired EntityManager entityManager;

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void bookkeeperCanOpenBuilderAndAllDatasetsAndExportTheSameQuery() throws Exception {
        var session = session("view-reports");
        String html = mvc.perform(get("/reports").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("report-builder", "data-catalog=", "data-query=");
        mvc.perform(get("/reports/overview").session(session)).andExpect(status().isOk());
        mvc.perform(get("/reports/overview").param("months", "0").session(session))
                .andExpect(status().isBadRequest());
        for (String dataset : List.of("invoices", "payments", "expenses", "time_entries")) {
            String q = "{\"dataset\":\"" + dataset + "\",\"from\":\"2026-01-01\",\"to\":\"2026-12-31\"}";
            var response = mvc.perform(get("/reports/query").param("q", q).session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.query.dataset").value(dataset))
                    .andExpect(jsonPath("$.reportUrl").isString()).andReturn();
            String normalized = mapper.readTree(response.getResponse().getContentAsString()).get("query").toString();
            mvc.perform(get("/reports/export").param("q", normalized).param("format", "csv").session(session))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/reports/export").param("q", normalized).param("format", "xlsx").session(session))
                    .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        }
    }

    @Test
    void excelExportsMoreThanOneDatabasePageEvenWhenViewingLaterPage() throws Exception {
        var client = new Client();
        client.setName("Export fixture");
        entityManager.persist(client);
        for (int i = 0; i < 501; i++) {
            var invoice = new Invoice();
            invoice.setClient(client);
            invoice.setReference("EXPORT-" + i);
            invoice.setIssuedAt(LocalDate.of(2026, 1, 15));
            invoice.setAmount(new BigDecimal("12.50"));
            entityManager.persist(invoice);
        }
        entityManager.flush();
        byte[] bytes = mvc.perform(get("/reports/export").session(session("view-reports"))
                        .param("q", "{\"dataset\":\"invoices\",\"from\":\"2026-01-01\",\"to\":\"2026-12-31\",\"offset\":500,\"limit\":1}")
                        .param("format", "xlsx"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Report");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(502);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("EXPORT-0");
            assertThat(sheet.getRow(501).getCell(0).getStringCellValue()).isEqualTo("EXPORT-500");
        }
    }

    @Test
    void reportPermissionProtectsDataExportsAndMcp() throws Exception {
        var session = session("view-dashboard");
        for (String route : List.of("/reports", "/reports/builder", "/reports/query", "/reports/export")) {
            mvc.perform(get(route).param("q", "{\"dataset\":\"invoices\"}").session(session))
                    .andExpect(status().isForbidden());
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff@example.test", null, List.of(new SimpleGrantedAuthority("view-dashboard"))));
        assertThatThrownBy(tools::catalog).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tools.build(new ReportQuery("invoices", null, null, null,
                null, null, null, null, null, null))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invalidQueryIsReadableAndMcpPublishesStructuredQueryAndSameLinks() throws Exception {
        mvc.perform(get("/reports/query").param("q", "{\"dataset\":\"invoices\",\"from\":\"oops\"}")
                        .session(session("view-reports")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        var tool = server.listTools().stream().filter(t -> t.name().equals("build_report")).findFirst().orElseThrow();
        assertThat(tool.inputSchema().properties()).containsKey("query");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bookkeeper@example.test", null, List.of(new SimpleGrantedAuthority("view-reports"))));
        var report = tools.build(new ReportQuery("invoices", "2026-01-01", "2026-12-31", null,
                null, null, null, null, null, null));
        assertThat(report.reportUrl()).contains("/reports/builder?q=");
        assertThat(tools.catalog().datasets()).hasSize(4);
    }

    private MockHttpSession session(String authority) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("bookkeeper@example.test", null,
                List.of(new SimpleGrantedAuthority(authority))));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
