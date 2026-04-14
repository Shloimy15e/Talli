package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Subscription;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.SubscriptionRepository;
import dev.dynamiq.talli.service.SubscriptionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionRepository subscriptionRepository,
                                  ClientRepository clientRepository,
                                  ProjectRepository projectRepository,
                                  SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("activeSubs", subscriptionRepository.findByCancelledOnIsNullOrderByVendorAsc());
        model.addAttribute("cancelledSubs", subscriptionRepository.findByCancelledOnIsNotNullOrderByCancelledOnDesc());
        model.addAttribute("monthlyTotal", subscriptionRepository.sumActiveMonthlyEquivalent());
        model.addAttribute("today", LocalDate.now());
        return "subscriptions/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        Subscription s = new Subscription();
        s.setStartedOn(LocalDate.now());
        s.setCycle("monthly");
        s.setCurrency("USD");
        model.addAttribute("subscription", s);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("action", "/subscriptions");
        model.addAttribute("title", "New Subscription");
        return "subscriptions/_form :: form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        Subscription s = subscriptionRepository.findById(id).orElseThrow();
        model.addAttribute("subscription", s);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("action", "/subscriptions/" + id);
        model.addAttribute("title", "Edit Subscription");
        return "subscriptions/_form :: form";
    }

    @PostMapping
    public String create(@ModelAttribute SubscriptionForm form) {
        Subscription s = new Subscription();
        form.applyTo(s, clientRepository, projectRepository);
        subscriptionRepository.save(s);
        return "redirect:/subscriptions";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id, @ModelAttribute SubscriptionForm form) {
        Subscription s = subscriptionRepository.findById(id).orElseThrow();
        form.applyTo(s, clientRepository, projectRepository);
        subscriptionRepository.save(s);
        return "redirect:/subscriptions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        subscriptionRepository.deleteById(id);
        return "redirect:/subscriptions";
    }

    @PostMapping("/{id}/record-charge")
    public String recordCharge(@PathVariable("id") Long id,
                               @RequestParam(value = "paidOn", required = false) String paidOn) {
        Subscription s = subscriptionRepository.findById(id).orElseThrow();
        LocalDate date = (paidOn == null || paidOn.isBlank()) ? LocalDate.now() : LocalDate.parse(paidOn);
        subscriptionService.recordCharge(s, date);
        return "redirect:/subscriptions";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable("id") Long id,
                         @RequestParam(value = "cancelledOn", required = false) String cancelledOn) {
        Subscription s = subscriptionRepository.findById(id).orElseThrow();
        LocalDate date = (cancelledOn == null || cancelledOn.isBlank()) ? LocalDate.now() : LocalDate.parse(cancelledOn);
        subscriptionService.cancel(s, date);
        return "redirect:/subscriptions";
    }

    // Reopen a cancelled subscription (common: user cancelled by mistake, or resubscribed)
    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable("id") Long id) {
        Subscription s = subscriptionRepository.findById(id).orElseThrow();
        s.setCancelledOn(null);
        if (s.getNextDueOn() == null) {
            s.setNextDueOn(LocalDate.now());
        }
        subscriptionRepository.save(s);
        return "redirect:/subscriptions";
    }

    // Inner form-binding class keeps the controller tidy and lets the mapping from
    // request params to entity fields live in one place for both create + update.
    public static class SubscriptionForm {
        private Long clientId;
        private Long projectId;
        private String vendor;
        private String description;
        private String category;
        private BigDecimal amount;
        private String currency;
        private String cycle;
        private String startedOn;
        private String nextDueOn;
        private String manageUrl;
        private String cancelUrl;
        private String paymentMethod;

        public void applyTo(Subscription s, ClientRepository clients, ProjectRepository projects) {
            s.setClient(clientId == null ? null : clients.findById(clientId).orElse(null));
            s.setProject(projectId == null ? null : projects.findById(projectId).orElse(null));
            s.setVendor(vendor);
            s.setDescription(description);
            s.setCategory(category);
            s.setAmount(amount);
            s.setCurrency(currency == null || currency.isBlank() ? "USD" : currency);
            s.setCycle(cycle);
            s.setStartedOn(parseDate(startedOn));
            if (nextDueOn != null && !nextDueOn.isBlank()) {
                s.setNextDueOn(parseDate(nextDueOn));
            } else if (s.getNextDueOn() == null) {
                s.setNextDueOn(parseDate(startedOn));
            }
            s.setManageUrl(emptyToNull(manageUrl));
            s.setCancelUrl(emptyToNull(cancelUrl));
            s.setPaymentMethod(emptyToNull(paymentMethod));
        }

        private static LocalDate parseDate(String v) {
            return (v == null || v.isBlank()) ? null : LocalDate.parse(v);
        }

        private static String emptyToNull(String v) {
            return (v == null || v.isBlank()) ? null : v;
        }

        public void setClientId(Long clientId) { this.clientId = clientId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public void setVendor(String vendor) { this.vendor = vendor; }
        public void setDescription(String description) { this.description = description; }
        public void setCategory(String category) { this.category = category; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public void setCurrency(String currency) { this.currency = currency; }
        public void setCycle(String cycle) { this.cycle = cycle; }
        public void setStartedOn(String startedOn) { this.startedOn = startedOn; }
        public void setNextDueOn(String nextDueOn) { this.nextDueOn = nextDueOn; }
        public void setManageUrl(String manageUrl) { this.manageUrl = manageUrl; }
        public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    }
}
