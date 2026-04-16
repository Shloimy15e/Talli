package dev.dynamiq.talli.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background jobs.
 *
 * Cron format: Spring uses 6 fields — second minute hour day-of-month month day-of-week.
 * Examples:
 *   "0 0 6 * * *"   = every day at 06:00
 *   "0 0 3 1 * *"   = 1st of every month at 03:00
 *   "0 * * * * *"   = every minute (useful for local testing)
 *
 * Each job runs on Spring's default single-thread TaskScheduler, so two jobs
 * scheduled at the same instant queue up rather than overlap.
 *
 * @Transactional belongs on the *service method* the job calls, not on the @Scheduled
 * method itself — there's no HTTP request scope here, so transaction boundaries
 * must be opened explicitly per unit of work.
 */
@Component
public class ScheduledJobs {

    private final InvoiceService invoiceService;
    private final ReminderService reminderService;

    public ScheduledJobs(InvoiceService invoiceService, ReminderService reminderService) {
        this.invoiceService = invoiceService;
        this.reminderService = reminderService;
    }

    /**
     * Daily 06:00 — flip every unpaid invoice whose dueAt is before today to "overdue".
     * TODO: implement InvoiceService.markOverdue() — find unpaid invoices with
     * dueAt < today, set status = "overdue". Wrap in @Transactional.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void markOverdueInvoices() {
        invoiceService.markOverdue();
    }

    /**
     * Daily 06:30 — send reminders to clients with overdue invoices, throttled per
     * client by their reminder interval (global default via app.reminders.interval-days
     * property, overridable per client).
     */
    @Scheduled(cron = "0 30 6 * * *")
    public void sendPaymentReminders() {
        reminderService.sendDueReminders();
    }

    /**
     * Monthly on the 1st at 03:00 — for every active retainer project, generate this
     * month's invoice if one doesn't already exist. The service method is idempotent,
     * so a manual re-run won't double-bill.
     */
    @Scheduled(cron = "0 0 3 1 * *")
    public void generateMonthlyRetainers() {
        invoiceService.generateRetainersForMonth();
    }
}
