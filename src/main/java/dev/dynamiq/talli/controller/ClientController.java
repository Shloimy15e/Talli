package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.ClientService;
import dev.dynamiq.talli.service.PdfService;
import dev.dynamiq.talli.service.ReminderService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/clients")
public class ClientController {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final InvoiceRepository invoiceRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final ExpenseRepository expenseRepository;
    private final ClientService clientService;
    private final PdfService pdfService;
    private final ReminderService reminderService;

    public ClientController(ClientRepository clientRepository,
                            ProjectRepository projectRepository,
                            InvoiceRepository invoiceRepository,
                            TimeEntryRepository timeEntryRepository,
                            ExpenseRepository expenseRepository,
                            ClientService clientService,
                            PdfService pdfService,
                            ReminderService reminderService) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.invoiceRepository = invoiceRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.expenseRepository = expenseRepository;
        this.clientService = clientService;
        this.reminderService = reminderService;
        this.pdfService = pdfService;
    }

    // List page
    @GetMapping
    public String index(Model model) {
        List<Client> clients = clientRepository.findAll();
        model.addAttribute("clients", clients);

        // Aggregate KPIs across all clients.
        List<Invoice> allInvoices = invoiceRepository.findAllByOrderByIssuedAtDescIdDesc();
        BigDecimal totalBilled = allInvoices.stream()
                .filter(i -> !"void".equals(i.getStatus()))
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = allInvoices.stream()
                .map(Invoice::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = totalBilled.subtract(totalCollected);
        long activeProjects = projectRepository.findAll().stream()
                .filter(p -> "active".equals(p.getStatus()))
                .count();

        model.addAttribute("totalBilled", totalBilled);
        model.addAttribute("totalCollected", totalCollected);
        model.addAttribute("totalOutstanding", totalOutstanding);
        model.addAttribute("activeProjects", activeProjects);
        return "clients/index";
    }

    // Detail page — projects, time, invoices, revenue for one client
    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Client client = clientRepository.findById(id).orElseThrow();
        List<Project> projects = projectRepository.findByClientId(id);
        List<Invoice> invoices = invoiceRepository.findByClientIdOrderByIssuedAtDescIdDesc(id);

        // Time entries: pull per-project, flatten, sort newest first
        List<TimeEntry> timeEntries = projects.stream()
                .flatMap(p -> timeEntryRepository.findByProjectIdOrderByStartedAtDesc(p.getId()).stream())
                .sorted(Comparator.comparing(TimeEntry::getStartedAt).reversed())
                .toList();

        // Revenue totals: sum of invoice amounts (billed) and amountPaid (collected).
        // Naive sum across currencies — fine while this client is single-currency in practice.
        BigDecimal totalBilled = invoices.stream()
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = invoices.stream()
                .map(Invoice::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = totalBilled.subtract(totalPaid);

        // Unbilled time: minutes of billable, ended, not-yet-billed entries across this client's projects
        long unbilledMinutes = timeEntries.stream()
                .filter(t -> Boolean.TRUE.equals(t.getBillable())
                        && Boolean.FALSE.equals(t.getBilled())
                        && t.getEndedAt() != null
                        && t.getDurationMinutes() != null)
                .mapToLong(TimeEntry::getDurationMinutes)
                .sum();

        long totalMinutes = timeEntries.stream()
                .filter(t -> t.getDurationMinutes() != null)
                .mapToLong(TimeEntry::getDurationMinutes)
                .sum();

        String currency = projects.stream()
                .map(Project::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("USD");

        List<Expense> expenses = expenseRepository.findByClientIdOrderByIncurredOnDesc(id);

        model.addAttribute("client", client);
        model.addAttribute("projects", projects);
        model.addAttribute("invoices", invoices);
        model.addAttribute("timeEntries", timeEntries);
        model.addAttribute("expenses", expenses);
        model.addAttribute("totalBilled", totalBilled);
        model.addAttribute("totalPaid", totalPaid);
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("unbilledMinutes", unbilledMinutes);
        model.addAttribute("totalMinutes", totalMinutes);
        model.addAttribute("currency", currency);
        model.addAttribute("aging", clientService.aging(invoices));
        return "clients/show";
    }

    // Returns just the form fragment — loaded into modal via HTMX
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("client", new Client());
        model.addAttribute("action", "/clients");
        model.addAttribute("title", "New Client");
        return "clients/_form :: form";
    }

    // Same fragment, pre-filled for editing
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Client client = clientRepository.findById(id).orElseThrow();
        model.addAttribute("client", client);
        model.addAttribute("action", "/clients/" + id);
        model.addAttribute("title", "Edit Client");
        return "clients/_form :: form";
    }

    // Create
    @PostMapping
    public String create(@ModelAttribute Client client) {
        clientRepository.save(client);
        return "redirect:/clients";
    }

    // Update
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Client client) {
        Client existing = clientRepository.findById(id).orElseThrow();
        existing.setName(client.getName());
        existing.setEmail(client.getEmail());
        existing.setPhone(client.getPhone());
        existing.setBillingAddress(client.getBillingAddress());
        existing.setTaxId(client.getTaxId());
        existing.setNotes(client.getNotes());
        existing.setPaymentTermsDays(client.getPaymentTermsDays());
        existing.setRemindersEnabled(client.getRemindersEnabled() != null ? client.getRemindersEnabled() : true);
        existing.setReminderIntervalDays(client.getReminderIntervalDays());
        clientRepository.save(existing);
        return "redirect:/clients";
    }

    // Statement PDF — streams directly to browser as download.
    @GetMapping("/{id}/statement")
    public ResponseEntity<byte[]> statement(@PathVariable Long id) {
        Client client = clientRepository.findById(id).orElseThrow();
        List<Invoice> invoices = invoiceRepository.findByClientIdOrderByIssuedAtDescIdDesc(id);
        List<Project> projects = projectRepository.findByClientId(id);
        String currency = projects.stream()
                .map(Project::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst().orElse("USD");

        byte[] pdf = pdfService.renderStatement(client, invoices, clientService.aging(invoices), currency);
        String filename = "Statement-" + client.getName().replaceAll("[^a-zA-Z0-9]", "-") + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // Manually send a payment reminder right now (ignores throttle, includes all unpaid)
    @PostMapping("/{id}/send-reminder")
    public String sendReminder(@PathVariable Long id, RedirectAttributes flash) {
        Client client = clientRepository.findById(id).orElseThrow();
        try {
            reminderService.sendNow(client);
            flash.addFlashAttribute("success", "Reminder sent to " + client.getName());
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/clients/" + id;
    }

    // Delete
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        clientRepository.deleteById(id);
        return "redirect:/clients";
    }
}
