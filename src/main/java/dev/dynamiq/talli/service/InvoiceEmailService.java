package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceEmailService {

    private final EmailService emailService;
    private final InvoiceRepository invoiceRepository;
    private final MediaService mediaService;
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;

    public InvoiceEmailService(EmailService emailService,
                               InvoiceRepository invoiceRepository,
                               MediaService mediaService,
                               EmailRepository emailRepository,
                               UserRepository userRepository) {
        this.emailService = emailService;
        this.invoiceRepository = invoiceRepository;
        this.mediaService = mediaService;
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void send(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        Client client = invoice.getClient();

        if (client.getEmail() == null || client.getEmail().isBlank()) {
            throw new IllegalStateException("Client " + client.getName() + " has no email address.");
        }

        List<Media> pdfs = mediaService.forOwner(invoice, "documents");
        if (pdfs.isEmpty()) {
            throw new IllegalStateException("Generate the invoice PDF before emailing.");
        }
        Media pdf = pdfs.get(0); // newest — forOwner returns most recent first.
        byte[] bytes = mediaService.loadBytes(pdf);

        String subject = "Invoice " + invoice.getReference() + " from Dynamiq Solutions";
        Map<String, Object> vars = Map.of("invoice", invoice, "client", client);

        // BCC active users linked to this client (skip the primary recipient to avoid duplicates)
        List<String> bcc = userRepository.findByClientIdAndEnabledTrue(client.getId()).stream()
                .map(User::getEmail)
                .filter(e -> e != null && !e.equalsIgnoreCase(client.getEmail()))
                .distinct()
                .toList();

        Email log = new Email();
        log.setClient(client);
        log.setInvoice(invoice);
        log.setToAddress(client.getEmail());
        if (!bcc.isEmpty()) log.setBcc(String.join(", ", bcc));
        log.setSubject(subject);
        log.setStatus("pending");
        log.setBody("");

        try {
            EmailService.Result result = emailService.sendTemplateWithAttachment(
                    client.getEmail(), bcc, subject, "invoice", vars,
                    bytes, pdf.getFilename(), "application/pdf");

            LocalDateTime now = LocalDateTime.now();
            log.setBodyHtml(result.html());
            log.setResendId(result.resendId());
            log.setStatus("sent");
            log.setSentAt(now);
            invoice.setSentAt(now);
        } catch (Exception e) {
            log.setStatus("failed");
            log.setErrorMessage(e.getMessage());
        }

        emailRepository.save(log);
    }
}
