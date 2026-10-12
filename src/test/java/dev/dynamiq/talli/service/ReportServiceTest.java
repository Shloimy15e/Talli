package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.*;
import dev.dynamiq.talli.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final TimeEntryRepository time = mock(TimeEntryRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final InvoiceItemRepository items = mock(InvoiceItemRepository.class);
    private final ExchangeRateService exchange = mock(ExchangeRateService.class);
    private final ReportService reports = new ReportService(invoices, payments, expenses, time, projects, items, exchange);
    private final LocalDate day = LocalDate.now();
    private Client client;

    @BeforeEach
    void setUp() {
        client = new Client();
        client.setId(1L);
        client.setName("Client");
        when(exchange.toUsd(any(), anyString(), any())).thenAnswer(call -> {
            BigDecimal amount = call.getArgument(0);
            BigDecimal rate = call.getArgument(2);
            return "USD".equals(call.getArgument(1)) ? amount : amount.divide(rate);
        });
    }

    @Test
    void projectRevenueUsesInclusiveIssueDatesAndInvoiceCurrency() {
        Project project = new Project();
        project.setId(2L);
        project.setName("Project");
        project.setClient(client);
        project.setCurrency("EUR");
        when(projects.findAll()).thenReturn(List.of(project));
        when(items.findByProjectIdOrderByIdDesc(2L)).thenReturn(List.of(
                item(day, "unpaid", "ILS", "4", "400"),
                item(day.plusDays(1), "written_off", "USD", "1", "20"),
                item(day.minusDays(1), "unpaid", "USD", "1", "999"),
                item(day.plusDays(2), "unpaid", "USD", "1", "999"),
                item(day, "void", "USD", "1", "999"),
                item(null, "unpaid", "USD", "1", "999")));

        var row = reports.revenueByProject(day, day.plusDays(1)).getFirst();
        assertThat(row.billed()).isEqualByComparingTo("120");
        assertThat(row.currency()).isEqualTo("USD");
    }

    @Test
    void cashSummariesExcludeCreditApplicationsButPaymentHistoryRetainsThem() {
        Invoice invoice = item(day, "unpaid", "USD", "1", "100").getInvoice();
        Payment cash = payment(invoice, "direct", "30");
        Payment credit = payment(invoice, "credit", "70");
        when(invoices.findAll()).thenReturn(List.of(invoice));
        when(payments.findAll()).thenReturn(List.of(cash, credit));

        assertThat(reports.clientProfitLoss(day, day).getFirst().received()).isEqualByComparingTo("30");
        assertThat(reports.clientProfitLoss(day, day).getFirst().profit()).isEqualByComparingTo("100");
        assertThat(reports.monthlyRevenue(1).getFirst().received()).isEqualByComparingTo("30");
        assertThat(reports.monthlyRevenue(1).getFirst().net()).isEqualByComparingTo("30");
        assertThat(reports.paymentHistory(day, day)).hasSize(2);
    }

    @Test
    void validatesAllPublicPeriodInputsBeforeReadingData() {
        for (int months : new int[]{0, -1, 121, Integer.MAX_VALUE}) {
            assertThatIllegalArgumentException().isThrownBy(() -> reports.monthlyRevenue(months));
            assertThatIllegalArgumentException().isThrownBy(() -> reports.quarterlyRevenue(months));
            assertThatIllegalArgumentException().isThrownBy(() -> reports.yearlyRevenue(months));
        }
        LocalDate later = day.plusDays(1);
        assertThatIllegalArgumentException().isThrownBy(() -> reports.clientProfitLoss(later, day));
        assertThatIllegalArgumentException().isThrownBy(() -> reports.timeUtilization(later, day));
        assertThatIllegalArgumentException().isThrownBy(() -> reports.revenueByProject(later, day));
        assertThatIllegalArgumentException().isThrownBy(() -> reports.expensesByCategory(later, day));
        assertThatIllegalArgumentException().isThrownBy(() -> reports.paymentHistory(later, day));
        assertThatIllegalArgumentException().isThrownBy(() -> reports.paymentHistory(null, day));
        verifyNoInteractions(invoices, payments, expenses, time, projects, items);
        assertThat(reports.monthlyRevenue(120)).hasSize(120);
    }

    private InvoiceItem item(LocalDate issueDate, String status, String currency, String rate, String total) {
        Invoice invoice = new Invoice();
        invoice.setClient(client);
        invoice.setIssuedAt(issueDate);
        invoice.setStatus(status);
        invoice.setCurrency(currency);
        invoice.setExchangeRate(new BigDecimal(rate));
        invoice.setAmount(new BigDecimal(total));
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setTotal(new BigDecimal(total));
        return item;
    }

    private Payment payment(Invoice invoice, String source, String amount) {
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setPaidAt(day);
        payment.setSource(source);
        payment.setAmount(new BigDecimal(amount));
        payment.setExchangeRate(BigDecimal.ONE);
        return payment;
    }
}
