package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceServiceTest {

    private InvoiceRepository invoiceRepository;
    private InvoiceItemRepository invoiceItemRepository;
    private TimeEntryRepository timeEntryRepository;
    private ProjectRepository projectRepository;
    private ClientRepository clientRepository;
    private InvoiceService service;

    private Client client;

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        invoiceItemRepository = mock(InvoiceItemRepository.class);
        timeEntryRepository = mock(TimeEntryRepository.class);
        projectRepository = mock(ProjectRepository.class);
        clientRepository = mock(ClientRepository.class);

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            if (i.getId() == null) i.setId(42L);
            return i;
        });
        when(invoiceItemRepository.save(any(InvoiceItem.class))).thenAnswer(inv -> {
            InvoiceItem it = inv.getArgument(0);
            if (it.getId() == null) it.setId(100L);
            return it;
        });

        client = new Client();
        client.setId(1L);
        client.setName("Acme Corp");

        service = new InvoiceService(invoiceRepository, invoiceItemRepository,
                timeEntryRepository, projectRepository, clientRepository);
    }

    // --- CRUD / reads ---

    @Test
    void listAll_delegatesToRepository() {
        Invoice a = new Invoice();
        Invoice b = new Invoice();
        when(invoiceRepository.findAllByOrderByIssuedAtDescIdDesc()).thenReturn(List.of(a, b));

        assertThat(service.listAll()).containsExactly(a, b);
    }

    @Test
    void get_throwsWhenMissing() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void get_returnsEntity() {
        Invoice i = new Invoice();
        when(invoiceRepository.findById(7L)).thenReturn(Optional.of(i));

        assertThat(service.get(7L)).isSameAs(i);
    }

    @Test
    void itemsFor_delegatesToRepository() {
        InvoiceItem item = new InvoiceItem();
        when(invoiceItemRepository.findByInvoiceIdOrderByIdAsc(5L)).thenReturn(List.of(item));

        assertThat(service.itemsFor(5L)).containsExactly(item);
    }

    @Test
    void delete_delegatesToRepository() {
        service.delete(3L);

        verify(invoiceRepository).deleteById(3L);
    }

    // --- create ---

    @Test
    void create_persistsInvoiceAndItemsAndComputesTotal() {
        Invoice invoice = new Invoice();
        InvoiceItem a = itemWithTotal("100.00");
        InvoiceItem b = itemWithTotal("250.50");
        InvoiceItem c = itemWithTotal("49.50");

        Invoice result = service.create(invoice, List.of(a, b, c));

        assertThat(result.getAmount()).isEqualByComparingTo("400.00");
        assertThat(a.getInvoice()).isSameAs(result);
        assertThat(b.getInvoice()).isSameAs(result);
        assertThat(c.getInvoice()).isSameAs(result);
        verify(invoiceRepository).save(invoice);
        verify(invoiceItemRepository).save(a);
        verify(invoiceItemRepository).save(b);
        verify(invoiceItemRepository).save(c);
    }

    @Test
    void create_withNoItems_amountIsZero() {
        Invoice invoice = new Invoice();

        Invoice result = service.create(invoice, List.of());

        assertThat(result.getAmount()).isEqualByComparingTo("0");
        verify(invoiceItemRepository, never()).save(any());
    }

    // --- nextReference ---

    @Test
    void nextReference_startsAt1001WhenNoInvoices() {
        when(invoiceRepository.findAll()).thenReturn(List.of());

        assertThat(service.nextReference()).isEqualTo("INV-1001");
    }

    @Test
    void nextReference_incrementsFromHighestMatching() {
        Invoice a = invoiceWithRef("INV-1001");
        Invoice b = invoiceWithRef("INV-1005");
        Invoice c = invoiceWithRef("INV-1003");
        when(invoiceRepository.findAll()).thenReturn(List.of(a, b, c));

        assertThat(service.nextReference()).isEqualTo("INV-1006");
    }

    @Test
    void nextReference_ignoresNonMatchingHistoricalRefs() {
        Invoice legacy = invoiceWithRef("INV-2026-0002");
        Invoice hand = invoiceWithRef("INV-ACME-5");
        Invoice current = invoiceWithRef("INV-1042");
        when(invoiceRepository.findAll()).thenReturn(List.of(legacy, hand, current));

        assertThat(service.nextReference()).isEqualTo("INV-1043");
    }

    @Test
    void nextReference_floorsAt1001WhenOnlyLegacyRefs() {
        Invoice legacy = invoiceWithRef("INV-2026-0002");
        when(invoiceRepository.findAll()).thenReturn(List.of(legacy));

        assertThat(service.nextReference()).isEqualTo("INV-1001");
    }

    @Test
    void nextReference_floorsAt1001WhenAllNumericButLow() {
        Invoice old1 = invoiceWithRef("INV-0050");
        when(invoiceRepository.findAll()).thenReturn(List.of(old1));

        assertThat(service.nextReference()).isEqualTo("INV-1001");
    }

    // --- generateForClient ---

    @Test
    void generateForClient_hourlyProject_createsInvoiceWithOneLinePerProject() {
        LocalDate periodStart = LocalDate.of(2026, 4, 1);
        LocalDate periodEnd = LocalDate.of(2026, 4, 30);

        Project p = hourlyProject(10L, "Alpha", "USD", "100");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(projectRepository.findByClientId(1L)).thenReturn(List.of(p));
        when(invoiceRepository.findAll()).thenReturn(List.of());

        // Two entries, 60 min + 30 min = 90 min = 1.5h × $100 = $150
        TimeEntry e1 = entry(p, LocalDateTime.of(2026, 4, 5, 9, 0), 60);
        TimeEntry e2 = entry(p, LocalDateTime.of(2026, 4, 10, 14, 0), 30);
        stubEntriesFor(p.getId(), List.of(e1, e2));

        Invoice invoice = service.generateForClient(1L, periodStart, periodEnd);

        assertThat(invoice.getClient()).isSameAs(client);
        assertThat(invoice.getCurrency()).isEqualTo("USD");
        assertThat(invoice.getStatus()).isEqualTo("unpaid");
        assertThat(invoice.getPeriodStart()).isEqualTo(periodStart);
        assertThat(invoice.getPeriodEnd()).isEqualTo(periodEnd);
        assertThat(invoice.getIssuedAt()).isEqualTo(LocalDate.now());
        assertThat(invoice.getDueAt()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(invoice.getAmount()).isEqualByComparingTo("150.00");
        assertThat(invoice.getReference()).startsWith("INV-");

        // Both entries marked billed and wired to invoice + item
        assertThat(e1.getBilled()).isTrue();
        assertThat(e1.getInvoice()).isSameAs(invoice);
        assertThat(e1.getInvoiceItem()).isNotNull();
        assertThat(e2.getBilled()).isTrue();
        assertThat(e2.getInvoice()).isSameAs(invoice);

        // One InvoiceItem saved (one project = one line)
        verify(invoiceItemRepository, times(1)).save(any(InvoiceItem.class));
    }

    @Test
    void generateForClient_multipleProjects_oneLinePerEligibleProject() {
        LocalDate periodStart = LocalDate.of(2026, 4, 1);
        LocalDate periodEnd = LocalDate.of(2026, 4, 30);

        Project alpha = hourlyProject(10L, "Alpha", "USD", "100");
        Project beta  = hourlyProject(11L, "Beta",  "USD", "150");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(projectRepository.findByClientId(1L)).thenReturn(List.of(alpha, beta));
        when(invoiceRepository.findAll()).thenReturn(List.of());

        stubEntriesFor(alpha.getId(),
                List.of(entry(alpha, LocalDateTime.of(2026, 4, 5, 9, 0), 60)));      // $100
        stubEntriesFor(beta.getId(),
                List.of(entry(beta,  LocalDateTime.of(2026, 4, 6, 9, 0), 120)));     // $300

        Invoice invoice = service.generateForClient(1L, periodStart, periodEnd);

        assertThat(invoice.getAmount()).isEqualByComparingTo("400.00");
        verify(invoiceItemRepository, times(2)).save(any(InvoiceItem.class));
    }

    @Test
    void generateForClient_skipsNonHourlyProjects() {
        LocalDate periodStart = LocalDate.of(2026, 4, 1);
        LocalDate periodEnd = LocalDate.of(2026, 4, 30);

        Project hourly = hourlyProject(10L, "Alpha", "USD", "100");
        Project fixed = new Project();
        fixed.setId(11L);
        fixed.setName("Beta");
        fixed.setCurrency("USD");
        fixed.setRateType("fixed");
        fixed.setCurrentRate(new BigDecimal("5000"));

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(projectRepository.findByClientId(1L)).thenReturn(List.of(hourly, fixed));
        when(invoiceRepository.findAll()).thenReturn(List.of());

        stubEntriesFor(hourly.getId(),
                List.of(entry(hourly, LocalDateTime.of(2026, 4, 5, 9, 0), 60)));

        Invoice invoice = service.generateForClient(1L, periodStart, periodEnd);

        assertThat(invoice.getAmount()).isEqualByComparingTo("100.00");
        verify(invoiceItemRepository, times(1)).save(any(InvoiceItem.class));
    }

    @Test
    void generateForClient_throwsWhenNoEligibleWork() {
        Project p = hourlyProject(10L, "Alpha", "USD", "100");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(projectRepository.findByClientId(1L)).thenReturn(List.of(p));
        stubEntriesFor(p.getId(), List.of());

        assertThatThrownBy(() -> service.generateForClient(1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No billable work");
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void generateForClient_throwsOnMixedCurrencies() {
        Project usd = hourlyProject(10L, "Alpha", "USD", "100");
        Project eur = hourlyProject(11L, "Beta",  "EUR", "100");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(projectRepository.findByClientId(1L)).thenReturn(List.of(usd, eur));

        stubEntriesFor(usd.getId(),
                List.of(entry(usd, LocalDateTime.of(2026, 4, 5, 9, 0), 60)));
        stubEntriesFor(eur.getId(),
                List.of(entry(eur, LocalDateTime.of(2026, 4, 6, 9, 0), 60)));

        assertThatThrownBy(() -> service.generateForClient(1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mixed currencies");
    }

    @Test
    void generateForClient_throwsWhenClientMissing() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateForClient(99L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // --- helpers ---

    private Invoice invoiceWithRef(String ref) {
        Invoice i = new Invoice();
        i.setReference(ref);
        return i;
    }

    private InvoiceItem itemWithTotal(String total) {
        InvoiceItem i = new InvoiceItem();
        i.setTotal(new BigDecimal(total));
        return i;
    }

    private Project hourlyProject(Long id, String name, String currency, String rate) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setCurrency(currency);
        p.setRateType("hourly");
        p.setCurrentRate(new BigDecimal(rate));
        return p;
    }

    private TimeEntry entry(Project project, LocalDateTime startedAt, int durationMinutes) {
        TimeEntry e = new TimeEntry();
        e.setProject(project);
        e.setStartedAt(startedAt);
        e.setEndedAt(startedAt.plusMinutes(durationMinutes));
        e.setDurationMinutes(durationMinutes);
        e.setBillable(true);
        e.setBilled(false);
        return e;
    }

    private void stubEntriesFor(Long projectId, List<TimeEntry> entries) {
        when(timeEntryRepository
                .findByProjectIdAndBillableTrueAndBilledFalseAndEndedAtIsNotNullAndStartedAtBetweenOrderByStartedAtAsc(
                        eq(projectId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(entries);
    }
}
