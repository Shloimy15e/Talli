# Talli — Plan

## Vision

Internal PSA (Professional Services Automation) tool for Dynamiq Solutions — clients, projects, time, invoices, expenses, subscriptions in one place.

Opinionated for service businesses. Other Shloimy-owned businesses (product/e-commerce) will fork Talli and adapt the primitives if they want similar tooling — no attempt at generic multi-industry support here.

## Two parallel goals

**1. Replace the spreadsheet workflow** — the actual product goal. Ship usable features.

**2. Learn Java** — the codebase doubles as a learning vehicle. Idiomatic Spring Boot, not just "make it work." When AI assists, the split is:

- **AI auto-does:** repetitive scaffolding, SQL migrations, boilerplate getters/setters, things you've already learned (CRUD controllers, repository interfaces, Thymeleaf templates).
- **You implement manually:** anything you haven't seen before, where the conceptual learning is the point. AI explains first, then you write it.

A live skills tracker lives in Claude's memory at `feedback_java_learning_split.md` — promotes "currently learning" items to "comfortable" as you demonstrate fluency.

**Currently in "currently learning" (pause if novel angle comes up):**
- Service layer extraction
- `Authentication` / current user pattern
- `@ControllerAdvice` for global model attributes
- `@Transactional`
- `@Valid` validation
- DTO vs entity binding
- Tests (Mockito, `MockMvc`, `@SpringBootTest`)

## Current state

- ✅ Clients, Projects, Time entries, Expenses, Subscriptions, Emails — CRUD + scaffolding
- ✅ Projects: client FK, rate type, currency, billing schedule
- ✅ Time entries: running timer with live counter, live duration in form
- ✅ Dashboard: counts, time this week, unbilled, running timer, recent activity, monthly burn, charts
- ✅ Emails: send + log via Gmail SMTP, Tippy tooltips for failure messages
- ✅ Auth: Spring Security form login, BCrypt, roles (ADMIN/CLIENT), remember-me
- ✅ User profile page (edit name/email, change password)
- ✅ Flyway migrations, PostgreSQL, spring-dotenv
- ✅ Dockerfile + Railway deployment
- ✅ Thymeleaf + Tailwind + HTMX + Alpine + Inter font + Dynamiq branding
- ✅ Service layer: `TimeEntryService` (with `indexView` aggregation + public static helpers shared with `InvoiceService`), `DashboardService` (with unit tests)
- ✅ Media library Phase A: `Media` entity, `HasMedia` interface, `MediaStorage` contract + `LocalMediaStorage`, `MediaService`
- ✅ Media library Phase B: `MediaController` (download/delete), expense receipts upload, custom error page with copy buttons
- ✅ Invoices: `Invoice` + `InvoiceItem` entities + repos, `InvoiceService` with manual CRUD and `generateForClient(clientId, periodStart, periodEnd)` (hourly projects only — one line per project per period, atomically marks time entries billed). Controller + UI: index/show/new/create/delete/generate, attachments (documents + payment_proofs), generate-from-client modal.
- ✅ QB-style sequential invoice references (`INV-1001`+), per-client invoice model (V11).
- ✅ Time index page: summary tiles (today/week/month/unbilled), per-day grouping with totals, per-entry $ values, link from "Billed" badge to the invoice.
- ✅ Phase A (invoicing): `Client.paymentTermsDays` (default 30, Net 30 US B2B standard), honored in `generateForClient.dueAt`. Project implements `HasMedia` + "sow" collection with upload/list on project form. Project has `isHourly()` / `isFixed()` / `isRetainer()` predicates + `contractAmount()` / `hourlyRate()` / `retainerMonthlyFee()` aliases. `Invoice.balance()` moved to entity. `Client._form.html` created (was missing — latent bug). `ClientController.update` persists terms.
- ✅ Shared modal shell (`fragments/modal.html`): all 6 modals (clients, projects, expenses, time, invoices-manual, invoices-generate) use one overlay/card/header. Cap at 90vh with inner scroll; `overflow-hidden` on rounded card + `overflow-y-auto flex-1 min-h-0` on inner body preserves rounded corners without sticky-header hacks.
- ✅ Phase B (project detail): `GET /projects/{id}` with `ProjectService.summary` (billed-to-date, unbilled value for hourly, remaining for fixed, month-billed for retainer). Lists linked invoices, time entries, SOW attachments. Inline change-contract form appends a dated change-order line to `project.notes`. `InvoiceItemRepository` gains `sumTotalByProjectId`, `sumTotalByProjectIdBetween`, `findInvoicesByProjectId` (EXISTS subquery avoids Postgres distinct+order-by rule).
- ✅ Phase C (scheduled jobs): `@EnableScheduling` via `SchedulingConfig`. `ScheduledJobs` runs `markOverdue` daily at 06:00 and `generateRetainersForMonth` on the 1st at 03:00. Retainer generation groups active retainer projects by client, skips clients whose invoice for the current period already exists (`existsByClientIdAndPeriodStartAndPeriodEnd`). Shared `newInvoiceShell` + `singleCurrency` helpers used by both generators.
- ✅ PDF generation: openhtmltopdf + BatikSVGDrawer, `POST /invoices/{id}/pdf` renders and stores via `MediaService` in the `documents` collection.
- ✅ Phase D (email invoices): `InvoiceEmailService.send(id)` attaches the latest stored PDF, sends via Gmail SMTP using a branded Thymeleaf template, stamps `invoice.sentAt`, and logs an `Email` row tied to both client and invoice (V14 added nullable `emails.invoice_id` FK). `EmailService.sendTemplateWithAttachment` returns rendered HTML for log persistence and auto-injects `fromAddress`/`fromName` into every template context. Invoice show page redesigned: status timeline (Issued → Sent → Paid/Overdue), Email History card with per-attempt status/error, Alpine-driven send-confirmation modal showing recipient/subject/attachment, disabled states with tooltips when prerequisites are missing. Reusable Thymeleaf fragments (`attachmentList`, `uploadForm`) extracted to `invoices/_partials.html` so they don't render inline.
- ✅ Phase E (payments): V15 `payments` table with `invoice_id`, `paid_at`, `amount`, `method`, `reference`, `notes`. `PaymentService.record()` recomputes `amount_paid` from `SUM(payments.amount)` (no drift from incrementing cache) and transitions status to `paid` + stamps `paidInFullBy` when balance ≤ 0. `PaymentService.delete()` recomputes too and reverts paid → unpaid/overdue if balance reopens. Invoice show page gains Payments card (totals + per-row list with Remove) and Record Payment modal (date / amount / method dropdown / reference / notes, defaults to outstanding balance).

## Guiding principle

**Self-consistent data — no bookkeeper needed to reconcile.** Assuming the person or automation entering data does so correctly, every summary, graph, and derived number in the app must agree with every other one. No silent drift between invoices, payments, time entries, and dashboard figures. Fix integrity gaps as they're found, prefer single-source-of-truth derivations over cached or parallel values.

## Now

**Phase F — data integrity pass.** Close the drift gaps found while auditing Phase E. Three issues, in order:

1. **Invoice deletion leaks time entries.** `time_entries.invoice_id ON DELETE SET NULL` nulls the FK but leaves `billed=true`, so hours become "billed but unbilled" — invisible to re-invoicing and not on any invoice. Fix: either delete-is-void (preferred for audit) or un-mark billed entries on delete.
2. **Dashboard revenue is fantasy.** `DashboardService.revenueVsExpenses` sums billable time × rate; doesn't match invoiced or received. Rewrite to source from invoices (accrual) and/or payments (cash).
3. **Billable expenses don't flow anywhere.** `Expense.billable=true` is a form checkbox but `generateForClient` never reads it. Either wire it into invoice generation or hide the field.

## Next up (ordered)

1. **Phase F — data integrity** (now) — invoice delete behavior, dashboard revenue source, billable-expense flow.
2. **Receivables view** — KPI tile on the dashboard (total outstanding) + aging buckets (0–30, 31–60, 60+). Follows naturally from Phase F #2.
3. **Customer statements** — per-client statement showing open invoices, totals, aging buckets. Optional PDF export.
4. **Client detail page** — drill into one client: projects, time, invoices, revenue, payment terms.
5. **Media Phase C** — `S3MediaStorage` for Cloudflare R2, switchable via `app.storage.driver`. Required before prod since Railway disk is ephemeral.

## Deliberately skipped for Dynamiq's shape

- **Estimate entity / quote workflow** — SOW lives as an attachment on the project; contract amount on the project itself. No need for a separate estimates system.
- **Tax line items** — US B2B services consulting, no sales tax nexus.
- **Credit memos** — rare at solo/small-agency volume; use void + new invoice.
- **Stripe / payment links** — defer until a client specifically asks.
- **Client portal** — scaffolded via Spring Security CLIENT role, actual pages deferred.

## Later / extensions

- **Chrome extension** — one-click time tracking + quick expense entry via REST API
- **Raycast extension** — log time, add expense, look up clients
- **Reports** — monthly/yearly revenue, per-client P&L, time utilization
- **Currency conversion** — show revenue in a base currency alongside original
- **Multi-org (maybe)** — only if another service business with the same shape of needs wants Talli. Not for product/e-commerce businesses (those would fork). Keeps the app simple.

## Deliberately not doing

- Sales pipeline / lead management (not a sales CRM)
- Non-service business features (inventory, orders, SKUs) — product businesses would fork Talli
- Opening Talli to external customers as a SaaS (internal tool)
- Mobile app (responsive web + Chrome/Raycast extensions cover mobile)

## Stack reference

- Java 21, Spring Boot 3.5 (Web, Data JPA, Thymeleaf, Validation, Security, Mail, DevTools)
- PostgreSQL + Flyway migrations
- Tailwind (CDN) + HTMX + Alpine.js + Tippy.js
- Inter font, Dynamiq palette (#ea7c28 on #161f30)
- Deployed on Railway
- Repo: https://github.com/Shloimy15e/Talli (tag `v1-swing` preserves the abandoned desktop version)
