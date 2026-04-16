# Talli — Plan

## Vision

Internal PSA (Professional Services Automation) tool for Dynamiq Solutions — clients, projects, time, invoices, expenses, subscriptions in one place.

Opinionated for service businesses. Other Shloimy-owned businesses (product/e-commerce) will fork Talli and adapt the primitives if they want similar tooling — no attempt at generic multi-industry support here.

## Two parallel goals

**1. Replace the spreadsheet workflow** — the actual product goal. Ship usable features.

**2. Learn Java** — the codebase doubles as a learning vehicle. Idiomatic Spring Boot, not just "make it work." Default mode is now regular AI-assisted coding — Claude writes the code. Only pause to teach when something genuinely novel comes up.

A live skills tracker lives in Claude's memory at `feedback_java_learning_split.md`.

## Guiding principle

**Self-consistent data — no bookkeeper needed to reconcile.** Assuming the person or automation entering data does so correctly, every summary, graph, and derived number in the app must agree with every other one. No silent drift between invoices, payments, time entries, and dashboard figures. Fix integrity gaps as they're found, prefer single-source-of-truth derivations over cached or parallel values.

## Current state

- ✅ Clients, Projects, Time entries, Expenses, Subscriptions, Emails — CRUD + scaffolding
- ✅ Projects: client FK, rate type, currency, billing schedule
- ✅ Time entries: running timer with live counter, live duration in form
- ✅ Dashboard: counts, time this week, unbilled, running timer, recent activity, monthly burn, charts (invoiced + received + expenses)
- ✅ Receivables KPI tile on dashboard (total outstanding across unpaid/overdue invoices)
- ✅ Emails: send + log via Gmail SMTP, Tippy tooltips for failure messages
- ✅ Auth: Spring Security form login, BCrypt, remember-me
- ✅ Spatie-style roles/permissions: 4 roles (admin, bookkeeper, accountant, client), 17 permissions, `@ManyToMany` pivots, `thymeleaf-extras-springsecurity6` for `sec:authorize`
- ✅ Email-based user invitations: admin invites by email+name+roles → UUID token → branded invite email → set-password page → login. Inline new-client creation when inviting client-role users.
- ✅ User profile page (edit name/email, change password)
- ✅ Flyway migrations (V1–V21), PostgreSQL, spring-dotenv
- ✅ Dockerfile + Railway deployment
- ✅ Thymeleaf + Tailwind + HTMX + Alpine + Lucide Icons + Inter font + Dynamiq branding
- ✅ `@ControllerAdvice` for currency symbols (`$`, `₪`, `€`, `£` via `csym` function)
- ✅ Service layer: `TimeEntryService`, `DashboardService` (with unit tests), `InvoiceService`, `ProjectService`, `PaymentService`, `ClientService`, `InvoiceEmailService`, `PdfService`, `MediaService`, `EmailService`
- ✅ Media library: `Media` entity, `HasMedia` interface, `MediaStorage` contract + `LocalMediaStorage`, `MediaController` (download/delete), expense receipts, project SOW, invoice documents/payment proofs
- ✅ Invoices: per-client model, `generateForClient` (hourly time + billable expenses), manual CRUD, QB-style sequential references (`INV-1001`+), attachments, generate-from-client modal
- ✅ Invoice PDF: openhtmltopdf + BatikSVGDrawer, `POST /invoices/{id}/pdf`
- ✅ Invoice email: branded Thymeleaf template, PDF attachment, `sentAt` stamp, Email log with invoice FK
- ✅ Invoice show page: status timeline (Issued → Sent → Paid/Overdue), Email History card, Payments card, send-confirmation modal, record-payment modal
- ✅ Payments: V15 `payments` table, `PaymentService.record()` recomputes from SUM (no cache drift), status transitions to `paid` when balance ≤ 0, delete recomputes + reverts
- ✅ Scheduled jobs: `@EnableScheduling`, daily `markOverdue`, monthly retainer auto-generation (idempotent via period check)
- ✅ Phase A invoicing: `Client.paymentTermsDays`, project SOW, `Invoice.balance()`, shared modal shell
- ✅ Phase B project detail: summary tiles, time/invoice lists, SOW, inline change-contract form
- ✅ Phase F data integrity: void-based invoice delete (un-marks time entries + expenses), dashboard revenue from invoices/payments not fantasy time, billable expenses flow through to invoices with invoice_id FK (V18)
- ✅ Client detail page: header with contact info + payment terms + billing address, KPI tiles, receivables aging buckets (current/31-60/61-90/90+), projects/invoices/expenses/time tables with links
- ✅ Customer statements: PDF generation via `PdfService.renderStatement`, inline download at `/clients/{id}/statement`
- ✅ Client portal: separate layout (no sidebar), dashboard with KPIs + aging + invoices + projects, invoice detail view, statement PDF download, login routing (client users → `/portal`)

- ✅ Role enforcement: SecurityConfig has 30+ granular route → permission rules matching HTTP method to specific permissions. V19 removes `manage-clients` from bookkeeper. Standalone emails page restricted to admin. Every sidebar nav link + action button (New/Edit/Delete/Void/Generate/Send/Record/Timer) guarded with `sec:authorize` across all templates.
- ✅ Test coverage: ClientServiceTest (6 tests: aging buckets), PaymentServiceTest (7 tests: record/delete/transitions/validation), InvoiceServiceTest additions (6 tests: void/idempotent/expenses/expense-only invoices).
- ✅ Index page improvements: client index KPI tiles + search, invoice index status/client filters + search, expense index pagination.
- ✅ Lucide icon conversion: all inline SVGs replaced with `data-lucide` attributes, icons added to all action buttons (Edit/Delete/Void/Stop/Start/Remove/back arrows) — 55 Lucide refs across 13 templates.
- ✅ Media Phase C: `S3MediaStorage` for Cloudflare R2, switchable via `app.storage.driver` env var. AWS SDK v2 with path-style access for R2 compatibility.
- ✅ Reports page: `ReportService` + `ReportController` with monthly/yearly revenue, per-client P&L, time utilization, expense breakdown. Filterable by date range.
- ✅ CSV/Excel expense import: `ImportService` (CSV + Excel parser via Apache POI) + `ImportController` with 3-step admin flow (upload → column-mapping preview with auto-mapping → confirm). Validates categories, supports 9 date formats.
- ✅ Historic data migration: `MigrationService` + `MigrationController` — multi-sheet xlsx upload → preview (transformed data, client normalization via regex rules in `ClientMapping`) → confirm → dependency-ordered import (clients → projects → invoices+payments → time entries → subscriptions+expenses). Time entries link to invoice projects. Subscriptions generate monthly expense records. `SpreadsheetUtil` for shared parse helpers. Unit tests for ClientMapping, SpreadsheetUtil, MigrationService.
- ✅ Exchange rate support (V20): `ExchangeRateService` with 1h-cached current rates (open.er-api.com) + historic rates (fawazahmed0/currency-api). `exchange_rate` column on invoices + payments. Dashboard/reports convert all aggregates to USD: received at payment-date rate, outstanding at current rate. Backfill endpoint for existing data. Net line fixed to cash basis (received − expenses).
- ✅ UI improvements: invoice index pagination (25/page) with server-side search + status pill filters. Time index pagination (30/page) with client/project dropdowns + status pills, currency-aware values (per-entry native currency, totals in USD), hh:mm chart formatting. Quarterly + yearly breakdown tables and quarterly bar chart on reports. Quarterly chart on dashboard. Currency symbols (`csym`) on expense/subscription index. `expenseCategories` via `@ControllerAdvice` (fixes T() SpEL DevTools bug). Cancelled subscriptions visible in collapsible section.
- ✅ Chrome extension Phase 1–2: Personal Access Tokens (V21, SHA-256 hashed, generate/revoke UI on profile page), dual `SecurityFilterChain` (stateless API chain + session web chain), REST API (`/api/v1/time`, `/api/v1/expenses`, `/api/v1/projects`) with `@RestController` + Java record DTOs + `ApiExceptionHandler`, CORS for Chrome extensions. Tests: `ApiTokenServiceTest` (11), `ApiTimeControllerTest` (5), `ApiExpenseControllerTest` (4), `ApiProjectControllerTest` (3).

## Now — WIP

**Chrome extension** — Phase 1 (PAT infrastructure) done. Phase 2 (REST API + CORS) done. Phase 3 (extension itself) next.

## Deliberately skipped for Dynamiq's shape

- **Estimate entity / quote workflow** — SOW lives as an attachment on the project; contract amount on the project itself. No need for a separate estimates system.
- **Tax line items** — US B2B services consulting, no sales tax nexus.
- **Credit memos** — rare at solo/small-agency volume; use void + new invoice.
- **Stripe / payment links** — defer until a client specifically asks.
- **Admin role-permission UI** — admin screen to edit which permissions belong to which role at runtime. Currently roles + permissions are seeded via V16 migration; changes require a new migration. Low-priority for a 4-role internal tool.

## Later / extensions

- ~~**Chrome extension**~~ — in progress (PAT auth + REST API + Manifest V3 popup)
- **Raycast extension** — log time, add expense, look up clients
- ~~**Currency conversion**~~ — done (V20, ExchangeRateService)
- **Multi-org (maybe)** — only if another service business with the same shape of needs wants Talli. Not for product/e-commerce businesses (those would fork). Keeps the app simple.

## Deliberately not doing

- Sales pipeline / lead management (not a sales CRM)
- Non-service business features (inventory, orders, SKUs) — product businesses would fork Talli
- Opening Talli to external customers as a SaaS (internal tool)
- Mobile app (responsive web + Chrome/Raycast extensions cover mobile)

## Stack reference

- Java 21, Spring Boot 3.5 (Web, Data JPA, Thymeleaf, Validation, Security, Mail, DevTools)
- PostgreSQL + Flyway migrations
- Tailwind (CDN) + HTMX + Alpine.js + Lucide Icons + Tippy.js
- Inter font, Dynamiq palette (#ea7c28 on #161f30)
- Deployed on Railway
- Repo: https://github.com/Shloimy15e/Talli (tag `v1-swing` preserves the abandoned desktop version)
