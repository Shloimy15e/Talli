package dev.dynamiq.talli.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;

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

    /**
     * Sabbath quiet window: Friday 11:00 → Sunday 06:00, evaluated in NY local time.
     * No scheduled job runs while this window is open.
     */
    private static final ZoneId SABBATH_ZONE = ZoneId.of("America/New_York");

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
        if (isInSabbathQuietHours()) return;
        invoiceService.markOverdue();
    }

    /**
     * Daily 06:30 — send reminders to clients with overdue invoices, throttled per
     * client by their reminder interval (global default via app.reminders.interval-days
     * property, overridable per client).
     */
    @Scheduled(cron = "0 30 6 * * *")
    public void sendPaymentReminders() {
        if (isInSabbathQuietHours()) return;
        reminderService.sendDueReminders();
    }

    /**
     * Days 1–7 of the month at 03:00 — for every active retainer project, generate this
     * month's invoice if one doesn't already exist. The service method is idempotent
     * (skips clients whose period invoice already exists), so the day-1 run does the
     * real work and days 2–7 are cheap no-ops. The 7-day window guarantees a successful
     * run even if the 1st falls inside the Sabbath quiet window.
     */
    @Scheduled(cron = "0 0 3 1-7 * *")
    public void generateMonthlyRetainers() {
        if (isInSabbathQuietHours()) return;
        invoiceService.generateRetainersForMonth();
    }

    private boolean isInSabbathQuietHours() {
        var now = ZonedDateTime.now(SABBATH_ZONE);
        return switch (now.getDayOfWeek()) {
            case FRIDAY -> now.getHour() >= 11;
            case SATURDAY -> true;
            case SUNDAY -> now.getHour() < 6;
            default -> false;
        };
    }
}
