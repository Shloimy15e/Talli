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

# Run the app (dev mode with hot reload)
mvn spring-boot:run
```

App runs at `http://localhost:8080`.

## Mercury invoice sync

Mercury integration is opt-in. Configure these environment variables in the
deployed app:

```bash
MERCURY_ENABLED=true
MERCURY_API_KEY=secret-token:mercury_production_...
MERCURY_DESTINATION_ACCOUNT_ID=00000000-0000-0000-0000-000000000000
```

New local invoices are then created in Mercury immediately. Open invoices are
reconciled every five minutes; when Mercury reports an invoice as `Paid`, Talli
records the remaining balance as one idempotent Mercury payment. The Mercury
payment page is shown as the primary payment action in Talli's customer portal,
invoice PDF, and invoice email.

Mercury email delivery is off by default so it does not duplicate Talli's
existing invoice email. Set `MERCURY_SEND_INVOICES=true` if Mercury should send
the invoice instead. For sandbox testing, set
`MERCURY_BASE_URL=https://api-sandbox.mercury.com/api/v1` and use a sandbox token.
