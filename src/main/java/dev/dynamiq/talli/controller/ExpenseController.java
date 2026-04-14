package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseRepository expenseRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;

    public ExpenseController(ExpenseRepository expenseRepository,
                             ClientRepository clientRepository,
                             ProjectRepository projectRepository) {
        this.expenseRepository = expenseRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public String index(Model model) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        model.addAttribute("expenses", expenseRepository.findAllByOrderByIncurredOnDesc());
        model.addAttribute("monthTotal", expenseRepository.sumAmountBetween(monthStart, today));
        model.addAttribute("monthLabel", monthStart);
        return "expenses/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        Expense e = new Expense();
        e.setIncurredOn(LocalDate.now());
        e.setCurrency("USD");
        model.addAttribute("expense", e);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("action", "/expenses");
        model.addAttribute("title", "Log Expense");
        return "expenses/_form :: form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        Expense e = expenseRepository.findById(id).orElseThrow();
        model.addAttribute("expense", e);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("action", "/expenses/" + id);
        model.addAttribute("title", "Edit Expense");
        return "expenses/_form :: form";
    }

    @PostMapping
    public String create(@ModelAttribute ExpenseForm form) {
        Expense e = new Expense();
        form.applyTo(e, clientRepository, projectRepository);
        expenseRepository.save(e);
        return "redirect:/expenses";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id, @ModelAttribute ExpenseForm form) {
        Expense e = expenseRepository.findById(id).orElseThrow();
        form.applyTo(e, clientRepository, projectRepository);
        expenseRepository.save(e);
        return "redirect:/expenses";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        expenseRepository.deleteById(id);
        return "redirect:/expenses";
    }

    public static class ExpenseForm {
        private Long clientId;
        private Long projectId;
        private String incurredOn;
        private BigDecimal amount;
        private String currency;
        private String category;
        private String vendor;
        private String description;
        private String paymentMethod;
        private String receiptUrl;
        private Boolean billable;

        public void applyTo(Expense e, ClientRepository clients, ProjectRepository projects) {
            e.setClient(clientId == null ? null : clients.findById(clientId).orElse(null));
            e.setProject(projectId == null ? null : projects.findById(projectId).orElse(null));
            e.setIncurredOn(incurredOn == null || incurredOn.isBlank() ? LocalDate.now() : LocalDate.parse(incurredOn));
            e.setAmount(amount);
            e.setCurrency(currency == null || currency.isBlank() ? "USD" : currency);
            e.setCategory(category);
            e.setVendor(emptyToNull(vendor));
            e.setDescription(emptyToNull(description));
            e.setPaymentMethod(emptyToNull(paymentMethod));
            e.setReceiptUrl(emptyToNull(receiptUrl));
            e.setBillable(Boolean.TRUE.equals(billable));
        }

        private static String emptyToNull(String v) {
            return (v == null || v.isBlank()) ? null : v;
        }

        public void setClientId(Long clientId) { this.clientId = clientId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public void setIncurredOn(String incurredOn) { this.incurredOn = incurredOn; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public void setCurrency(String currency) { this.currency = currency; }
        public void setCategory(String category) { this.category = category; }
        public void setVendor(String vendor) { this.vendor = vendor; }
        public void setDescription(String description) { this.description = description; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }
        public void setBillable(Boolean billable) { this.billable = billable; }
    }
}
