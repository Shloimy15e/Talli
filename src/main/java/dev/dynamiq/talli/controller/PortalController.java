package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.ClientService;
import dev.dynamiq.talli.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;

/**
 * Client-facing portal — shows the logged-in client user their own invoices,
 * projects, and statement. Guarded by "portal-access" permission in SecurityConfig.
 */
@Controller
@RequestMapping("/portal")
public class PortalController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientService clientService;
    private final PdfService pdfService;

    public PortalController(UserRepository userRepository,
                            ProjectRepository projectRepository,
                            InvoiceRepository invoiceRepository,
                            ClientService clientService,
                            PdfService pdfService) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.invoiceRepository = invoiceRepository;
        this.clientService = clientService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public String dashboard(Authentication auth, Model model) {
        Client client = resolveClient(auth);
        if (client == null) {
            model.addAttribute("error", "Your account is not linked to a client.");
            return "portal/error";
        }

        List<Project> projects = projectRepository.findByClientId(client.getId());
        List<Invoice> invoices = invoiceRepository.findByClientIdOrderByIssuedAtDescIdDesc(client.getId());

        BigDecimal totalBilled = invoices.stream()
                .filter(i -> !"void".equals(i.getStatus()))
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = invoices.stream()
                .map(Invoice::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = totalBilled.subtract(totalPaid);

        String currency = projects.stream()
                .map(Project::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst().orElse("USD");

        model.addAttribute("client", client);
        model.addAttribute("projects", projects);
        model.addAttribute("invoices", invoices);
        model.addAttribute("totalBilled", totalBilled);
        model.addAttribute("totalPaid", totalPaid);
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("currency", currency);
        model.addAttribute("aging", clientService.aging(invoices));
        return "portal/dashboard";
    }

    @GetMapping("/invoices/{id}")
    public String showInvoice(@PathVariable Long id, Authentication auth, Model model) {
        Client client = resolveClient(auth);
        Invoice invoice = invoiceRepository.findById(id).orElseThrow();
        if (client == null || !invoice.getClient().getId().equals(client.getId())) {
            model.addAttribute("error", "Invoice not found.");
            return "portal/error";
        }
        model.addAttribute("invoice", invoice);
        model.addAttribute("balance", invoice.balance());
        return "portal/invoice";
    }

    @GetMapping("/statement")
    public ResponseEntity<byte[]> statement(Authentication auth) {
        Client client = resolveClient(auth);
        if (client == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Invoice> invoices = invoiceRepository.findByClientIdOrderByIssuedAtDescIdDesc(client.getId());
        List<Project> projects = projectRepository.findByClientId(client.getId());
        String currency = projects.stream()
                .map(Project::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst().orElse("USD");

        byte[] pdf = pdfService.renderStatement(client, invoices, clientService.aging(invoices), currency);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Statement.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Client resolveClient(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .map(User::getClient)
                .orElse(null);
    }
}
