# Talli

Internal PSA (Professional Services Automation) tool for Dynamiq Solutions — tracks clients, projects, time, invoices, and expenses in one place.

No more spreadsheets.

## Core concepts

- **Clients** — the businesses we work with
- **Projects** — scoped engagements under a client, with their own billing terms
- **Rates** — live on projects, with full history (so rate changes are never lost)
- **Time entries** — logged against projects whether they're billed hourly or not
- **Invoices** — auto-generated based on each project's billing schedule

## Billing models

Each project defines how it bills:

- **Hourly** — invoice every N days/weeks/months, based on logged time
- **Fixed rate** — invoice on milestones or a configured schedule

## Features

**Shipping:**
- Client management
- Project management with rate history
- Time tracking (manual entry for now)

**Coming soon:**
- Auto-generated invoices per project billing rules
- PDF invoice generation
- Email invoices directly from the app
- Payment tracking and overdue alerts
- Expense tracking (quick-add on the fly, assignable to clients/projects)
- Revenue, expense, and income dashboard
- Chrome extension for time tracking + quick expense entry
- Raycast extension for quick time logging, expense entry, and client lookup

## Stack

- Java 21
- Spring Boot
- Thymeleaf + Tailwind + HTMX + Alpine.js
- PostgreSQL
- Flyway migrations

## Setup

Requires JDK 21+, Maven, and PostgreSQL.

```bash
# Create the database
createdb talli

# Run the app (see below for automatic local updates)
mvn spring-boot:run
```

App runs at `http://localhost:8080`.

### Automatic local updates (Windows)

```powershell
.\scripts\dev.ps1
```

This starts the local server and watches `src/main`. After saves settle, Maven
compiles the changes; Spring Boot DevTools then refreshes connected browser tabs
and restarts the application when Java or configuration changes require it.
No browser extension is needed. This is automatic page reload, not component HMR.
Open pages once after starting this mode to load the development client.

The server binds to loopback and disables outbound email. Watcher output is in
`target/dev-watch.log`; failed compilation is retried on the next source save.
Stop with Ctrl+C. The `local` profile and browser reload client are not enabled
by the normal run command. Save non-email forms before code changes trigger a
page reload; the email composer retains its existing local draft behavior.

## Mail oversight and notifications

MCP emails can optionally include the configured `MCP_EMAIL_CC` as a visible
oversight recipient. This is off by default and shown in the approval preview.
The app records the authenticated initiating account, provider ID, delivery
events, and conversation separately; recipient copies are not the audit trail.

Incoming mail is stored in Talli without forwarding it to admins. Every day at
05:00 UTC, enabled admins with an email address outside `dynamiq.dev` receive a
count and inbox link if their own inbox has unread conversations. Read or archived
conversations do not trigger a notification; a new incoming reply can make an
archived conversation eligible again. These notifications use `APP_BASE_URL` for
the inbox link and require the application to be running at the scheduled time.

## Mercury

Mercury invoice creation is handled outside Talli. Paste a Mercury payment link
onto a Talli invoice to make it the primary payment action in the customer
portal, PDF, and invoice email. When recording the payment, choose
`Mercury ACH` and put the Mercury transaction ID in the existing reference field.

Expense import is opt-in and webhook-driven. Configure:

```bash
MERCURY_ENABLED=true
MERCURY_API_KEY=secret-token:mercury_production_...
MERCURY_WEBHOOK_SECRET=...
```

Use a read-only token with transaction access; it does not require an IP
whitelist. Register `https://your-domain/webhooks/mercury` for
`transaction.created` and `transaction.updated` events, then store the returned
signing secret as `MERCURY_WEBHOOK_SECRET`. Each event triggers a read-only
transaction lookup. Settled expense transactions are imported once, keyed by
Mercury transaction ID. Historical backfill is intentionally not included.
