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
- ✅ Service layer: `TimeEntryService`, `DashboardService` (with unit tests)
- ✅ Media library Phase A: `Media` entity, `HasMedia` interface, `MediaStorage` contract + `LocalMediaStorage`, `MediaService`
- ✅ Media library Phase B: `MediaController` (download/delete), expense receipts upload, custom error page with copy buttons
- ✅ Invoices Phase A: `Invoice` + `InvoiceItem` entities, repos, manual CRUD controller (index/show/new/create/delete), dynamic line-item form, attachments per invoice (documents + payment_proofs), `time_entries.invoice_id` FK ready for generation

## Now

**`InvoiceService` extraction + generation logic.** Pull manual CRUD out of the controller into `InvoiceService`, then add the real business logic on top: `generateForProject(project, periodStart, periodEnd)` (group billable unbilled time entries → invoice + items, mark billed), `recordPayment(invoice, amount, ...)` with cached `amount_paid`, state transitions (`paid` / `overdue` / `void`).

## Next up (ordered)

1. **`InvoiceService`** — extract CRUD, then add generation, payments, state transitions
2. **PDF generation** — render invoice as PDF, store via `MediaService` in `documents` collection
3. **Email invoices** — send generated PDF via Gmail SMTP, set `sentAt`
4. **Media Phase C** — `S3MediaStorage` for Cloudflare R2, switchable via `app.storage.driver`. Required before prod since Railway disk is ephemeral.
5. **Payments** — separate `payments` table, `PaymentService` updates `invoice.amount_paid` cache + transitions status to `paid`
6. **Client detail page** — drill into one client: projects, time, invoices, revenue
7. **Project detail page** — same for one project, with "Generate invoice" button wired to `InvoiceService.generateForProject`

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
