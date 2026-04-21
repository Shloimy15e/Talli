package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;

    @Value("${app.reminders.interval-days:7}")
    private int defaultIntervalDays;

    public ReminderService(ClientRepository clientRepository,
                           InvoiceRepository invoiceRepository,
                           EmailService emailService,
                           EmailRepository emailRepository,
                           UserRepository userRepository) {
        this.clientRepository = clientRepository;
        this.invoiceRepository = invoiceRepository;
        this.emailService = emailService;
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
    }

    /** Run for all eligible clients. Returns count of reminders sent. */
    @Transactional
    public int sendDueReminders() {
        int sent = 0;
        for (Client client : clientRepository.findAll()) {
            if (sendIfDue(client)) sent++;
        }
        return sent;
    }

    /** Send a reminder to a single client if eligible. Returns true if sent. */
    @Transactional
    public boolean sendIfDue(Client client) {
        if (!Boolean.TRUE.equals(client.getRemindersEnabled())) return false;
        if (client.getEmail() == null || client.getEmail().isBlank()) return false;

        // Throttle: don't send if last reminder was within the interval
        int interval = client.getReminderIntervalDays() != null
                ? client.getReminderIntervalDays() : defaultIntervalDays;
        if (client.getLastReminderAt() != null
                && client.getLastReminderAt().isAfter(LocalDateTime.now().minusDays(interval))) {
            return false;
        }

        // Find overdue invoices for this client
        List<Invoice> overdue = invoiceRepository.findByClientIdOrderByIssuedAtDescIdDesc(client.getId()).stream()
                .filter(i -> "overdue".equals(i.getStatus()))
                .filter(i -> i.balance().signum() > 0)
                .toList();
        if (overdue.isEmpty()) return false;

        return send(client, overdue);
    }

    /** Force-send a reminder to a client regardless of throttle. */
    @Transactional
    public boolean sendNow(Client client) {
        if (client.getEmail() == null || client.getEmail().isBlank()) {
            throw new IllegalStateException("Client " + client.getName() + " has no email address.");
        }
        List<Invoice> overdue = invoiceRepository.findByClientIdOrderByIssuedAtDescIdDesc(client.getId()).stream()
                .filter(i -> "overdue".equals(i.getStatus()) || "unpaid".equals(i.getStatus()))
                .filter(i -> i.balance().signum() > 0)
                .toList();
        if (overdue.isEmpty()) {
            throw new IllegalStateException("No unpaid invoices for this client.");
        }
        return send(client, overdue);
    }

    private boolean send(Client client, List<Invoice> invoices) {
        LocalDate today = LocalDate.now();
        String subject = "Payment reminder — " + invoices.size()
                + (invoices.size() == 1 ? " unpaid invoice" : " unpaid invoices");

        List<String> bcc = userRepository.findByClientIdAndEnabledTrue(client.getId()).stream()
                .map(User::getEmail)
                .filter(e -> e != null && !e.equalsIgnoreCase(client.getEmail()))
                .distinct()
                .toList();

        Map<String, Object> vars = Map.of(
                "client", client,
                "invoices", invoices,
                "today", today);

        Email logEntry = new Email();
        logEntry.setClient(client);
        logEntry.setToAddress(client.getEmail());
        if (!bcc.isEmpty()) logEntry.setBcc(String.join(", ", bcc));
        logEntry.setSubject(subject);
        logEntry.setStatus("pending");
        logEntry.setBody("");

        try {
            EmailService.Result result = emailService.sendTemplate(client.getEmail(), bcc, subject, "reminder", vars);
            logEntry.setBodyHtml(result.html());
            logEntry.setResendId(result.resendId());
            logEntry.setStatus("sent");
            logEntry.setSentAt(LocalDateTime.now());
            client.setLastReminderAt(LocalDateTime.now());
            log.info("Sent reminder to client {} for {} invoices", client.getName(), invoices.size());
        } catch (Exception e) {
            logEntry.setStatus("failed");
            logEntry.setErrorMessage(e.getMessage());
            log.error("Failed to send reminder to {}: {}", client.getName(), e.getMessage());
        }

        emailRepository.save(logEntry);
        return "sent".equals(logEntry.getStatus());
    }

    /** Clients currently eligible for a reminder — for admin preview. */
    public List<Client> eligibleClients() {
        List<Client> out = new ArrayList<>();
        for (Client client : clientRepository.findAll()) {
            if (!Boolean.TRUE.equals(client.getRemindersEnabled())) continue;
            if (client.getEmail() == null || client.getEmail().isBlank()) continue;

            int interval = client.getReminderIntervalDays() != null
                    ? client.getReminderIntervalDays() : defaultIntervalDays;
            if (client.getLastReminderAt() != null
                    && client.getLastReminderAt().isAfter(LocalDateTime.now().minusDays(interval))) {
                continue;
            }

            boolean hasOverdue = invoiceRepository.findByClientIdOrderByIssuedAtDescIdDesc(client.getId()).stream()
                    .anyMatch(i -> "overdue".equals(i.getStatus()) && i.balance().signum() > 0);
            if (hasOverdue) out.add(client);
        }
        return out;
    }
}
