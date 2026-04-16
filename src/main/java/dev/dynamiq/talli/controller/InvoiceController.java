package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.InvoiceEmailService;
import dev.dynamiq.talli.service.InvoiceService;
import dev.dynamiq.talli.service.MediaService;
import dev.dynamiq.talli.service.PaymentService;
import dev.dynamiq.talli.service.PdfService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final MediaService mediaService;
    private final PdfService pdfService;
    private final InvoiceEmailService invoiceEmailService;
    private final EmailRepository emailRepository;
    private final PaymentService paymentService;

    public InvoiceController(InvoiceService invoiceService,
            ClientRepository clientRepository,
            ProjectRepository projectRepository,
            MediaService mediaService, PdfService pdfService,
            InvoiceEmailService invoiceEmailService,
            EmailRepository emailRepository,
            PaymentService paymentService) {
        this.invoiceService = invoiceService;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.mediaService = mediaService;
        this.pdfService = pdfService;
        this.invoiceEmailService = invoiceEmailService;
        this.emailRepository = emailRepository;
        this.paymentService = paymentService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("invoices", invoiceService.listAll());
        return "invoices/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        InvoiceForm form = new InvoiceForm();
        form.setIssuedAt(LocalDate.now().toString());
        form.setDueAt(LocalDate.now().plusDays(30).toString());
        form.setCurrency("USD");
        form.setReference(invoiceService.nextReference());
        form.setItems(new ArrayList<>(List.of(new InvoiceItemForm())));
        model.addAttribute("form", form);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("action", "/invoices");
        model.addAttribute("title", "New Invoice");
        return "invoices/_form :: form";
    }

    @PostMapping
    public String create(@ModelAttribute InvoiceForm form) {
        Invoice invoice = new Invoice();
        form.applyTo(invoice, clientRepository);

        List<InvoiceItem> items = form.nonEmptyItems().stream()
                .map(itemForm -> {
                    InvoiceItem item = new InvoiceItem();
                    itemForm.applyTo(item, projectRepository);
                    return item;
                })
                .toList();

        invoice = invoiceService.create(invoice, items);

        if (form.getInvoiceDoc() != null && !form.getInvoiceDoc().isEmpty()) {
            mediaService.attach(invoice, form.getInvoiceDoc(), "documents");
        }
        if (form.getPaymentProof() != null && !form.getPaymentProof().isEmpty()) {
            mediaService.attach(invoice, form.getPaymentProof(), "payment_proofs");
        }

        return "redirect:/invoices/" + invoice.getId();
    }

    @GetMapping("/{id}")
    public String show(@PathVariable("id") Long id, Model model) {
        Invoice invoice = invoiceService.get(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("items", invoiceService.itemsFor(id));
        model.addAttribute("balance", invoice.balance());
        model.addAttribute("invoiceDocs", mediaService.forOwner(invoice, "documents"));
        model.addAttribute("paymentProofs", mediaService.forOwner(invoice, "payment_proofs"));
        model.addAttribute("emailHistory", emailRepository.findByInvoiceIdOrderByCreatedAtDesc(id));
        model.addAttribute("payments", paymentService.listForInvoice(id));
        return "invoices/show";
    }

    @PostMapping("/{id}/void")
    public String voidInvoice(@PathVariable("id") Long id) {
        invoiceService.voidInvoice(id);
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes flash) {
        try {
            invoiceService.delete(id);
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/invoices/" + id;
        }
        return "redirect:/invoices";
    }

    @PostMapping("/{id}/attachments")
    public String upload(@PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "collection", defaultValue = "documents") String collection) {
        Invoice invoice = invoiceService.get(id);
        if (file != null && !file.isEmpty()) {
            mediaService.attach(invoice, file, collection);
        }
        return "redirect:/invoices/" + id;
    }

    @GetMapping("/generate")
    public String generateForm(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("periodStart", today.withDayOfMonth(1).toString());
        model.addAttribute("periodEnd", today.toString());
        return "invoices/_generate :: form";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam("clientId") Long clientId,
            @RequestParam("periodStart") String periodStart,
            @RequestParam("periodEnd") String periodEnd,
            RedirectAttributes flash) {
        try {
            Invoice invoice = invoiceService.generateForClient(
                    clientId, LocalDate.parse(periodStart), LocalDate.parse(periodEnd));
            return "redirect:/invoices/" + invoice.getId();
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("generateError", e.getMessage());
            return "redirect:/invoices";
        }
    }

    @PostMapping("/{id}/pdf")
    public String generatePdf(@PathVariable Long id) {
        Invoice invoice = invoiceService.get(id);
        byte[] bytes = pdfService.renderInvoice(invoice, invoiceService.itemsFor(id));
        mediaService.attachBytes(invoice, bytes, invoice.getReference() + ".pdf", "application/pdf",    "documents");
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/email")
    public String email(@PathVariable Long id, RedirectAttributes flash) {
        try {
            invoiceEmailService.send(id);
            flash.addFlashAttribute("emailSuccess", "Invoice emailed.");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("emailError", e.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/payments")
    public String recordPayment(@PathVariable Long id,
                                @RequestParam("paidAt") String paidAt,
                                @RequestParam("amount") BigDecimal amount,
                                @RequestParam(value = "method", required = false) String method,
                                @RequestParam(value = "reference", required = false) String reference,
                                @RequestParam(value = "notes", required = false) String notes,
                                RedirectAttributes flash) {
        try {
            paymentService.record(id, parseDate(paidAt), amount,
                    emptyToNull(method), emptyToNull(reference), emptyToNull(notes));
            flash.addFlashAttribute("paymentSuccess", "Payment recorded.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("paymentError", e.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/payments/{paymentId}/delete")
    public String deletePayment(@PathVariable Long id, @PathVariable Long paymentId,
                                RedirectAttributes flash) {
        paymentService.delete(paymentId);
        flash.addFlashAttribute("paymentSuccess", "Payment removed.");
        return "redirect:/invoices/" + id;
    }

    // --- Form beans ---

    public static class InvoiceForm {
        private String reference;
        private Long clientId;
        private String currency;
        private String status;
        private String issuedAt;
        private String dueAt;
        private String periodStart;
        private String periodEnd;
        private String notes;
        private List<InvoiceItemForm> items = new ArrayList<>();
        private MultipartFile invoiceDoc;
        private MultipartFile paymentProof;

        public void applyTo(Invoice inv, ClientRepository clients) {
            inv.setReference(reference);
            inv.setClient(clientId == null ? null : clients.findById(clientId).orElseThrow());
            inv.setCurrency(currency == null || currency.isBlank() ? "USD" : currency);
            inv.setStatus(status == null || status.isBlank() ? "unpaid" : status);
            inv.setIssuedAt(parseDate(issuedAt));
            inv.setDueAt(parseDate(dueAt));
            inv.setPeriodStart(parseDate(periodStart));
            inv.setPeriodEnd(parseDate(periodEnd));
            inv.setNotes(emptyToNull(notes));
        }

        public List<InvoiceItemForm> nonEmptyItems() {
            return items == null ? List.of()
                    : items.stream()
                            .filter(i -> i.getUnitPrice() != null || i.getUnitCount() != null
                                    || (i.getDescription() != null && !i.getDescription().isBlank()))
                            .toList();
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public Long getClientId() {
            return clientId;
        }

        public void setClientId(Long clientId) {
            this.clientId = clientId;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getIssuedAt() {
            return issuedAt;
        }

        public void setIssuedAt(String issuedAt) {
            this.issuedAt = issuedAt;
        }

        public String getDueAt() {
            return dueAt;
        }

        public void setDueAt(String dueAt) {
            this.dueAt = dueAt;
        }

        public String getPeriodStart() {
            return periodStart;
        }

        public void setPeriodStart(String periodStart) {
            this.periodStart = periodStart;
        }

        public String getPeriodEnd() {
            return periodEnd;
        }

        public void setPeriodEnd(String periodEnd) {
            this.periodEnd = periodEnd;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public List<InvoiceItemForm> getItems() {
            return items;
        }

        public void setItems(List<InvoiceItemForm> items) {
            this.items = items;
        }

        public MultipartFile getInvoiceDoc() {
            return invoiceDoc;
        }

        public void setInvoiceDoc(MultipartFile invoiceDoc) {
            this.invoiceDoc = invoiceDoc;
        }

        public MultipartFile getPaymentProof() {
            return paymentProof;
        }

        public void setPaymentProof(MultipartFile paymentProof) {
            this.paymentProof = paymentProof;
        }
    }

    public static class InvoiceItemForm {
        private Long projectId;
        private String description;
        private String unit;
        private BigDecimal unitPrice;
        private BigDecimal unitCount;

        public void applyTo(InvoiceItem item, ProjectRepository projects) {
            item.setProject(projectId == null ? null : projects.findById(projectId).orElse(null));
            item.setDescription(emptyToNull(description));
            item.setUnit(emptyToNull(unit));
            item.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);
            item.setUnitCount(unitCount == null ? BigDecimal.ONE : unitCount);
            item.setTotal(item.getUnitPrice().multiply(item.getUnitCount()).setScale(2, RoundingMode.HALF_UP));
        }

        public Long getProjectId() {
            return projectId;
        }

        public void setProjectId(Long projectId) {
            this.projectId = projectId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getUnitCount() {
            return unitCount;
        }

        public void setUnitCount(BigDecimal unitCount) {
            this.unitCount = unitCount;
        }
    }

    private static LocalDate parseDate(String s) {
        return (s == null || s.isBlank()) ? null : LocalDate.parse(s);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
