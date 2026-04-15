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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            TimeEntryRepository timeEntryRepository,
            ProjectRepository projectRepository,
            ClientRepository clientRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
    }

    public List<Invoice> listAll() {
        return invoiceRepository.findAllByOrderByIssuedAtDescIdDesc();
    }

    public Invoice get(Long id) {
        return invoiceRepository.findById(id).orElseThrow();
    }

    public List<InvoiceItem> itemsFor(Long invoiceId) {
        return invoiceItemRepository.findByInvoiceIdOrderByIdAsc(invoiceId);
    }

    /**
     * Persist an invoice along with its line items, computing and storing the
     * total.
     * Items must already have their fields populated (description, unitPrice,
     * unitCount, total)
     * — only the FK back to the invoice is wired here.
     */
    @Transactional
    public Invoice create(Invoice invoice, List<InvoiceItem> items) {
        invoice = invoiceRepository.save(invoice);

        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceItem item : items) {
            item.setInvoice(invoice);
            invoiceItemRepository.save(item);
            total = total.add(item.getTotal());
        }

        invoice.setAmount(total);
        return invoice;
    }

    @Transactional
    public void delete(Long id) {
        invoiceRepository.deleteById(id);
    }

    /**
     * Generate an invoice for a client covering all eligible billable work across
     * the client's projects within the given period. One line per project:
     * - hourly: sum of billable unbilled time entries × project rate
     * - fixed / retainer: not yet supported, those projects are skipped
     * Marks time entries as billed and links them to their invoice + item.
     * Whole operation is atomic.
     */
    @Transactional
    public Invoice generateForClient(Long clientId, LocalDate periodStart, LocalDate periodEnd) {
        Client client = clientRepository.findById(clientId).orElseThrow();
        List<Project> projects = projectRepository.findByClientId(clientId);

        // Collect per-project eligible results before touching any invoice state.
        List<EligibleLine> lines = new ArrayList<>();
        String currency = null;
        LocalDateTime now = LocalDateTime.now();

        for (Project project : projects) {
            if (!"hourly".equals(project.getRateType())) {
                // TODO: handle "fixed" milestones and "retainer" monthly fees.
                continue;
            }
            List<TimeEntry> entries = timeEntryRepository
                    .findByProjectIdAndBillableTrueAndBilledFalseAndEndedAtIsNotNullAndStartedAtBetweenOrderByStartedAtAsc(
                            project.getId(),
                            periodStart.atStartOfDay(),
                            periodEnd.plusDays(1).atStartOfDay());
            if (entries.isEmpty())
                continue;

            int totalMinutes = entries.stream()
                    .mapToInt(e -> TimeEntryService.minutesFor(e, now))
                    .sum();
            BigDecimal hours = BigDecimal.valueOf(totalMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            BigDecimal rate = project.getCurrentRate() == null ? BigDecimal.ZERO : project.getCurrentRate();
            BigDecimal lineTotal = TimeEntryService.valueOf(totalMinutes, rate);

            if (currency == null)
                currency = project.getCurrency();
            else if (!currency.equals(project.getCurrency())) {
                throw new IllegalStateException(
                        "Projects have mixed currencies; cannot combine on a single invoice.");
            }

            lines.add(new EligibleLine(project, entries, hours, rate, lineTotal));
        }

        if (lines.isEmpty()) {
            throw new IllegalStateException(
                    "No billable work to invoice for " + client.getName() + " in this period");
        }

        Invoice invoice = newInvoiceShell(client, periodStart, periodEnd, currency);

        BigDecimal total = BigDecimal.ZERO;
        for (EligibleLine line : lines) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setProject(line.project());
            item.setDescription(line.project().getName() + " — "
                    + line.hours().stripTrailingZeros().toPlainString() + "h @ "
                    + currency + " " + line.rate() + "/hr");
            item.setUnit("hr");
            item.setUnitCount(line.hours());
            item.setUnitPrice(line.rate());
            item.setTotal(line.total());
            item = invoiceItemRepository.save(item);

            for (TimeEntry e : line.entries()) {
                e.setInvoice(invoice);
                e.setInvoiceItem(item);
                e.setBilled(true);
                // Managed entity inside @Transactional — flushes on commit.
            }

            total = total.add(line.total());
        }

        invoice.setAmount(total);
        return invoice;
    }

    @Transactional
    public void markOverdue() {
        List<Invoice> overdueInvoices = invoiceRepository.findByStatusAndDueAtBefore("unpaid", LocalDate.now());

        overdueInvoices.forEach(i -> i.setStatus("overdue"));
    }

    /**
     * For every active retainer project, ensure an invoice exists covering the
     * current calendar month. Groups projects by client so each client receives
     * one invoice with one line per retainer project. Idempotent: skips clients
     * whose invoice for this period already exists.
     */
    @Transactional
    public void generateRetainersForMonth() {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.withDayOfMonth(1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        Map<Client, List<Project>> byClient = projectRepository
                .findByStatusAndRateType("active", "retainer").stream()
                .collect(Collectors.groupingBy(Project::getClient, LinkedHashMap::new, Collectors.toList()));

        for (var entry : byClient.entrySet()) {
            Client client = entry.getKey();
            List<Project> projects = entry.getValue();

            if (invoiceRepository.existsByClientIdAndPeriodStartAndPeriodEnd(
                    client.getId(), periodStart, periodEnd)) {
                continue;
            }

            String currency = singleCurrency(projects, client);
            Invoice invoice = newInvoiceShell(client, periodStart, periodEnd, currency);

            BigDecimal total = BigDecimal.ZERO;
            for (Project project : projects) {
                BigDecimal fee = project.retainerMonthlyFee() == null
                        ? BigDecimal.ZERO : project.retainerMonthlyFee();

                InvoiceItem item = new InvoiceItem();
                item.setInvoice(invoice);
                item.setProject(project);
                item.setDescription(project.getName() + " — retainer "
                        + periodStart + " to " + periodEnd);
                item.setUnit("mo");
                item.setUnitCount(BigDecimal.ONE);
                item.setUnitPrice(fee);
                item.setTotal(fee);
                invoiceItemRepository.save(item);

                total = total.add(fee);
            }

            invoice.setAmount(total);
        }
    }

    /**
     * Build and persist a fresh invoice with the standard header fields.
     * Callers fill in line items + total afterwards.
     */
    private Invoice newInvoiceShell(Client client, LocalDate periodStart, LocalDate periodEnd, String currency) {
        LocalDate today = LocalDate.now();
        int terms = client.getPaymentTermsDays() == null ? 30 : client.getPaymentTermsDays();

        Invoice invoice = new Invoice();
        invoice.setClient(client);
        invoice.setReference(nextReference());
        invoice.setIssuedAt(today);
        invoice.setDueAt(today.plusDays(terms));
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setCurrency(currency);
        invoice.setStatus("unpaid");
        return invoiceRepository.save(invoice);
    }

    /** Throw if the projects span more than one currency; return the single currency otherwise. */
    private String singleCurrency(List<Project> projects, Client client) {
        String currency = projects.get(0).getCurrency();
        for (Project p : projects) {
            if (!currency.equals(p.getCurrency())) {
                throw new IllegalStateException(
                        "Client " + client.getName() + " has projects in mixed currencies.");
            }
        }
        return currency;
    }

    private record EligibleLine(Project project, List<TimeEntry> entries,
            BigDecimal hours, BigDecimal rate, BigDecimal total) {
    }

    /**
     * Reference format: INV-NNNN — global sequential, QuickBooks-style.
     * First invoice starts at 1001 (customary to avoid looking brand-new).
     * Lexical sort == numeric sort while the number stays 4 digits.
     *
     * Non-matching historical refs (e.g. year-prefixed INV-2026-0002) are ignored
     * so the new sequence can continue alongside legacy ones.
     */
    public String nextReference() {
        String prefix = "INV-";
        long next = invoiceRepository.findAll().stream()
                .map(Invoice::getReference)
                .filter(r -> r != null && r.startsWith(prefix))
                .map(r -> r.substring(prefix.length()))
                .filter(s -> s.matches("\\d+"))
                .mapToLong(Long::parseLong)
                .max()
                .orElse(1000L) + 1;
        if (next < 1001L)
            next = 1001L;
        return prefix + String.format("%04d", next);
    }

}
