package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.Media;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
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

    public InvoiceEmailService(EmailService emailService,
                               InvoiceRepository invoiceRepository,
                               MediaService mediaService,
                               EmailRepository emailRepository) {
        this.emailService = emailService;
        this.invoiceRepository = invoiceRepository;
        this.mediaService = mediaService;
        this.emailRepository = emailRepository;
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

        Email log = new Email();
        log.setClient(client);
        log.setInvoice(invoice);
        log.setToAddress(client.getEmail());
        log.setSubject(subject);
        log.setStatus("pending");
        log.setBody("");

        try {
            String renderedBody = emailService.sendTemplateWithAttachment(
                    client.getEmail(), subject, "invoice", vars,
                    bytes, pdf.getFilename(), "application/pdf");

            LocalDateTime now = LocalDateTime.now();
            log.setBody(renderedBody);
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
