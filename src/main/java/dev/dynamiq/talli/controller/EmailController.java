package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.service.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/emails")
public class EmailController {

    private final EmailRepository emailRepository;
    private final ClientRepository clientRepository;
    private final EmailService emailService;

    public EmailController(EmailRepository emailRepository, ClientRepository clientRepository, EmailService emailService) {
        this.emailRepository = emailRepository;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("emails", emailRepository.findAllByOrderByCreatedAtDesc());
        return "emails/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("email", new Email());
        model.addAttribute("clients", clientRepository.findAll());
        return "emails/_form :: form";
    }

    @PostMapping
    public String send(@RequestParam(value = "clientId", required = false) Long clientId,
                       @RequestParam("toAddress") String toAddress,
                       @RequestParam("subject") String subject,
                       @RequestParam("body") String body) {
        Email email = new Email();
        if (clientId != null) {
            Client client = clientRepository.findById(clientId).orElse(null);
            email.setClient(client);
        }
        email.setToAddress(toAddress);
        email.setSubject(subject);
        email.setBody(body);

        try {
            emailService.sendPlain(toAddress, subject, body);
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
