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

## Now

**Phase A — fixed-project supporting infrastructure (QB-grade basics).** Add `Client.paymentTermsDays` (default 30, Net 30 standard), use it in invoice generation. Project implements `HasMedia` with SOW attachment. Neutral helper methods on Project for contract-amount vs. hourly-rate naming without schema churn.

## Next up (ordered)

1. **Phase A — client payment terms + SOW attachment** (now) — `Client.paymentTermsDays`, Project `HasMedia` + SOW upload, `generateForClient` honors per-client Net terms.
2. **Phase B — project detail page** — list time entries, linked invoices, SOW, running balance (`SUM(invoice_items WHERE project_id)`), editable contract amount (lightweight change-order workflow).
3. **Phase C — scheduled jobs** — daily `markOverdue` (`@Scheduled`), monthly retainer auto-generation for active retainer projects.
4. **PDF generation** — render invoice as PDF, store via `MediaService` in `documents` collection at generation time.
5. **Email invoices** — send generated PDF via Gmail SMTP, set `sentAt`, transition nothing (status stays `unpaid` until payment).
6. **Payments** — separate `payments` table, `PaymentService` updates `invoice.amount_paid` cache + transitions status to `paid` when balance ≤ 0.
7. **Customer statements** — per-client statement showing open invoices, totals, aging buckets. Optional PDF export.
8. **Client detail page** — drill into one client: projects, time, invoices, revenue, payment terms.
9. **Media Phase C** — `S3MediaStorage` for Cloudflare R2, switchable via `app.storage.driver`. Required before prod since Railway disk is ephemeral.

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
