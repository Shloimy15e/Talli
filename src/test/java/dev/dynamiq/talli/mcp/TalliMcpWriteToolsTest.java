package dev.dynamiq.talli.mcp;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Payment;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.AgentEmailService;
import dev.dynamiq.talli.service.ExpenseService;
import dev.dynamiq.talli.service.InvoiceService;
import dev.dynamiq.talli.service.PaymentService;
import dev.dynamiq.talli.service.ProjectService;
import dev.dynamiq.talli.service.TimeEntryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TalliMcpWriteToolsTest {

    private ClientRepository clients;
    private ProjectRepository projects;
    private TimeEntryRepository timeEntries;
    private TimeEntryService timeEntryService;
    private ExpenseService expenseService;
    private ProjectService projectService;
    private InvoiceRepository invoices;
    private PaymentService paymentService;
    private InvoiceService invoiceService;
    private AgentEmailService agentEmailService;
    private TalliMcpWriteTools tools;

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        projects = mock(ProjectRepository.class);
        timeEntries = mock(TimeEntryRepository.class);
        timeEntryService = mock(TimeEntryService.class);
        expenseService = mock(ExpenseService.class);
        projectService = mock(ProjectService.class);
        invoices = mock(InvoiceRepository.class);
        paymentService = mock(PaymentService.class);
        invoiceService = mock(InvoiceService.class);
        agentEmailService = mock(AgentEmailService.class);
        tools = new TalliMcpWriteTools(clients, projects, timeEntries,
                timeEntryService, expenseService, projectService,
                invoices, paymentService, invoiceService, agentEmailService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logTimeBuildsCompletedEntryFromDuration() {
        Client client = client(1L, "Acme");
        Project project = project(2L, "Website", client);
        when(timeEntryService.create(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            TimeEntry entry = new TimeEntry();
            entry.setId(3L);
            entry.setProject(project);
            entry.setStartedAt(invocation.getArgument(1));
            entry.setEndedAt(invocation.getArgument(2));
            entry.setDescription(invocation.getArgument(3));
            entry.setBillable(invocation.getArgument(4));
            entry.setBilled(false);
            return entry;
        });

        var result = tools.logTime(2L, 90, "2020-01-02T09:15:00", "Planning", true);

        assertThat(result.startedAt()).isEqualTo(LocalDateTime.of(2020, 1, 2, 9, 15));
        assertThat(result.endedAt()).isEqualTo(LocalDateTime.of(2020, 1, 2, 10, 45));
        assertThat(result.durationMinutes()).isEqualTo(90);
        verify(timeEntryService).create(2L,
                LocalDateTime.of(2020, 1, 2, 9, 15),
                LocalDateTime.of(2020, 1, 2, 10, 45), "Planning", true);
    }

    @Test
    void logExpenseUsesProjectsClient() {
        Client client = client(1L, "Acme");
        Project project = project(2L, "Website", client);
        when(projects.findById(2L)).thenReturn(Optional.of(project));
        when(expenseService.create(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(3L);
            expense.setExchangeRate(BigDecimal.ONE);
            expense.setBilled(false);
            return expense;
        });

        var result = tools.logExpense(new BigDecimal("49.99"), "software", "2026-08-12",
                "usd", null, 2L, "GitHub", "Hosting", "card", null, true);

        assertThat(result.clientId()).isEqualTo(1L);
        assertThat(result.projectId()).isEqualTo(2L);
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.incurredOn()).isEqualTo(LocalDate.of(2026, 8, 12));
    }

    @Test
    void logExpenseRejectsMismatchedClientAndProject() {
        Client projectClient = client(1L, "Acme");
        Client otherClient = client(9L, "Other");
        when(projects.findById(2L)).thenReturn(Optional.of(project(2L, "Website", projectClient)));
        when(clients.findById(9L)).thenReturn(Optional.of(otherClient));

        assertThatThrownBy(() -> tools.logExpense(new BigDecimal("10"), "other", null,
                null, 9L, 2L, null, null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("project_id does not belong to client_id");
        verify(expenseService, never()).create(any());
    }

    @Test
    void updateProjectRecordsRateChangeReason() {
        Client client = client(1L, "Acme");
        Project project = project(2L, "Website", client);
        project.setCurrentRate(new BigDecimal("100"));
        project.setRateType("hourly");
        project.setCurrency("USD");
        project.setStatus("active");
        project.setBillable(true);
        when(projects.findById(2L)).thenReturn(Optional.of(project));
        when(projects.findByClientId(1L)).thenReturn(List.of(project));
        when(projects.save(project)).thenReturn(project);
        var rate = ArgumentCaptor.forClass(BigDecimal.class);

        tools.updateProject(2L, null, null, new BigDecimal("125"),
                "2026 renewal", null, null, null, null, null);

        verify(projectService).changeContractAmount(org.mockito.ArgumentMatchers.eq(2L),
                rate.capture(), org.mockito.ArgumentMatchers.eq("2026 renewal"));
        assertThat(rate.getValue()).isEqualByComparingTo("125");
    }

    @Test
    void recordPaymentDelegatesToIdempotentBankPaymentPath() {
        Invoice invoice = invoice(7L, "INV-7", "USD");
        Payment payment = new Payment();
        payment.setId(8L);
        payment.setInvoice(invoice);
        payment.setPaidAt(LocalDate.of(2026, 8, 13));
        payment.setAmount(new BigDecimal("125.00"));
        payment.setExchangeRate(BigDecimal.ONE);
        payment.setMethod("ACH");
        payment.setReference("bank-ref");
        payment.setSource("direct");
        payment.setExternalProvider("mercury");
        payment.setExternalId("txn-123");
        when(invoices.findById(7L)).thenReturn(Optional.of(invoice));
        when(paymentService.recordExternal(7L, LocalDate.of(2026, 8, 13),
                new BigDecimal("125.00"), "ACH", "bank-ref", "Matched by agent",
                "mercury", "txn-123")).thenReturn(payment);

        var result = tools.recordPayment(7L, new BigDecimal("125.00"), "usd",
                "2026-08-13", "Mercury", "txn-123", "ACH", "bank-ref",
                "Matched by agent");

        assertThat(result.invoiceId()).isEqualTo(7L);
        assertThat(result.externalProvider()).isEqualTo("mercury");
        assertThat(result.externalId()).isEqualTo("txn-123");
    }

    @Test
    void recordPaymentRejectsCurrencyMismatch() {
        when(invoices.findById(7L)).thenReturn(Optional.of(invoice(7L, "INV-7", "EUR")));

        assertThatThrownBy(() -> tools.recordPayment(7L, new BigDecimal("125.00"), "USD",
                "2026-08-13", "mercury", "txn-123", "ACH", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice currency EUR");
        verify(paymentService, never()).recordExternal(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void setInvoiceAchLinkUsesValidatedInvoiceService() {
        Invoice invoice = invoice(7L, "INV-7", "USD");
        invoice.setMercuryPaymentUrl("https://app.mercury.com/pay/example");
        when(invoiceService.updateMercuryPaymentUrl(7L, invoice.getMercuryPaymentUrl()))
                .thenReturn(invoice);

        var result = tools.setInvoiceAchLink(7L, invoice.getMercuryPaymentUrl());

        assertThat(result.paymentUrl()).isEqualTo(invoice.getMercuryPaymentUrl());
        assertThat(result.buttonLabel()).isEqualTo("Pay with ACH");
    }

    @Test
    void previewsThenSendsClientEmailUsingAuthenticatedAgentAndSignedDefault() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance@dynamiq.dev", null));
        when(agentEmailService.preview("finance@dynamiq.dev", 7L, "Invoice update",
                "Hello", "branded", true))
                .thenReturn(new AgentEmailService.Preview(7L, "billing@acme.test",
                        "shloimy@dynamiq.dev", "Invoice update", "Hello", "<html>Preview</html>",
                        "branded", true, "preview-token"));
        Email email = new Email();
        email.setId(12L);
        email.setToAddress("billing@acme.test");
        email.setCc("shloimy@dynamiq.dev");
        email.setSubject("Invoice update");
        email.setStatus("sent");
        when(agentEmailService.send("finance@dynamiq.dev", 7L, "Invoice update",
                "Hello", "branded", true, "preview-token", true))
                .thenReturn(new AgentEmailService.SendResult(email, "branded", true));

        var preview = tools.previewClientEmail(7L, "Invoice update", "Hello", "branded", null);
        var result = tools.sendClientEmail(7L, "Invoice update", "Hello",
                "branded", null, preview.previewToken(), true);

        assertThat(preview.ccAddress()).isEqualTo("shloimy@dynamiq.dev");
        assertThat(result.emailId()).isEqualTo(12L);
        assertThat(result.toAddress()).isEqualTo("billing@acme.test");
        assertThat(result.ccAddress()).isEqualTo("shloimy@dynamiq.dev");
        assertThat(result.signatureIncluded()).isTrue();
        verify(agentEmailService).send("finance@dynamiq.dev", 7L, "Invoice update",
                "Hello", "branded", true, "preview-token", true);
    }

    private static Client client(Long id, String name) {
        Client client = new Client();
        client.setId(id);
        client.setName(name);
        return client;
    }

    private static Project project(Long id, String name, Client client) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setClient(client);
        project.setBillable(true);
        return project;
    }

    private static Invoice invoice(Long id, String reference, String currency) {
        Client client = client(1L, "Acme");
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setReference(reference);
        invoice.setClient(client);
        invoice.setCurrency(currency);
        invoice.setAmount(new BigDecimal("500.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus("unpaid");
        return invoice;
    }
}
