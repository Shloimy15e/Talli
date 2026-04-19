package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/emails")
public class EmailController {

    private final EmailRepository emailRepository;
    private final ClientRepository clientRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;

    public EmailController(EmailRepository emailRepository,
                           ClientRepository clientRepository,
                           EmailService emailService,
                           UserRepository userRepository) {
        this.emailRepository = emailRepository;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) List<String> status,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "created") String sort,
                        @RequestParam(defaultValue = "desc") String direction,
                        Model model) {
        List<String> statuses = status == null ? List.of() : status;
        String q = (search == null) ? "" : search;
        String normalizedSort = switch (sort) {
            case "sent", "subject", "status" -> sort;
            default -> "created";
        };
        String normalizedDir = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";

        var emailPage = emailRepository.findFiltered(
                statuses, q, normalizedSort, normalizedDir,
                org.springframework.data.domain.PageRequest.of(page, 25));

        model.addAttribute("emails", emailPage.getContent());
        model.addAttribute("page", emailPage);
        model.addAttribute("filterStatuses", statuses);
        model.addAttribute("filterSearch", search);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("direction", normalizedDir);
        return "emails/index";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Email email = emailRepository.findById(id).orElseThrow();
        model.addAttribute("email", email);
        return "emails/show";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("email", new Email());
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("users", userRepository.findAllByOrderByCreatedAtDesc());
        return "emails/_form :: form";
    }

    @PostMapping
    public String send(@RequestParam(value = "clientId", required = false) Long clientId,
                       @RequestParam("toAddress") String toAddress,
                       @RequestParam("subject") String subject,
                       @RequestParam("body") String body,
                       @RequestParam(value = "bccUserId", required = false) List<Long> bccUserIds,
                       @RequestParam(value = "bccManual", required = false) String bccManual) {
        Email email = new Email();
        if (clientId != null) {
            Client client = clientRepository.findById(clientId).orElse(null);
            email.setClient(client);
        }
        email.setToAddress(toAddress);
        email.setSubject(subject);
        email.setBody(body);

        java.util.Set<String> bccSet = new java.util.LinkedHashSet<>();
        if (bccUserIds != null) {
            userRepository.findAllById(bccUserIds).forEach(u -> {
                if (u.getEmail() != null && !u.getEmail().isBlank()) bccSet.add(u.getEmail());
            });
        }
        if (bccManual != null && !bccManual.isBlank()) {
            for (String addr : bccManual.split("[,;\\s]+")) {
                if (!addr.isBlank()) bccSet.add(addr.trim());
            }
        }
        List<String> bcc = bccSet.stream()
                .filter(e -> !e.equalsIgnoreCase(toAddress))
                .toList();
        if (!bcc.isEmpty()) email.setBcc(String.join(", ", bcc));

        try {
            EmailService.Result result = emailService.sendPlain(toAddress, bcc, subject, body);
            email.setResendId(result.resendId());
            email.setStatus("sent");
            email.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            email.setStatus("failed");
            email.setErrorMessage(e.getMessage());
        }

        emailRepository.save(email);
        return "redirect:/emails";
    }
}
