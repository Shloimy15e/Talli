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
import java.util.List;

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
     * Persist an invoice along with its line items, computing and storing the total.
     * Items must already have their fields populated (description, unitPrice, unitCount, total)
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
     *   - hourly: sum of billable unbilled time entries × project rate
     *   - fixed / retainer: not yet supported, those projects are skipped
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
            if (entries.isEmpty()) continue;

            int totalMinutes = entries.stream()
                    .mapToInt(e -> TimeEntryService.minutesFor(e, now))
                    .sum();
            BigDecimal hours = BigDecimal.valueOf(totalMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            BigDecimal rate = project.getCurrentRate() == null ? BigDecimal.ZERO : project.getCurrentRate();
            BigDecimal lineTotal = TimeEntryService.valueOf(totalMinutes, rate);

            if (currency == null) currency = project.getCurrency();
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

        Invoice invoice = new Invoice();
        invoice.setClient(client);
        invoice.setReference(nextReference());
        invoice.setIssuedAt(LocalDate.now());
        invoice.setDueAt(LocalDate.now().plusDays(30));
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setCurrency(currency);
        invoice.setStatus("unpaid");
        invoice = invoiceRepository.save(invoice);

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

    private record EligibleLine(Project project, List<TimeEntry> entries,
                                BigDecimal hours, BigDecimal rate, BigDecimal total) {}

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
        if (next < 1001L) next = 1001L;
        return prefix + String.format("%04d", next);
    }

}
