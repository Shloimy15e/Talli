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
- ✅ Dashboard: counts, time this week, unbilled, running timer, recent activity, monthly burn
- ✅ Emails: send + log via Gmail SMTP, Tippy tooltips for failure messages
- ✅ Auth: Spring Security form login, BCrypt, roles (ADMIN/CLIENT), remember-me
- ✅ User profile page (edit name/email, change password)
- ✅ Flyway migrations, PostgreSQL, spring-dotenv
- ✅ Dockerfile + Railway deployment
- ✅ Thymeleaf + Tailwind + HTMX + Alpine + Inter font + Dynamiq branding

## Now

**Service layer refactor.** Extract business logic out of controllers into services (`TimeEntryService`, `InvoiceService`, etc.) — controllers should only handle HTTP. Pays off as invoicing logic grows.

## Next up (ordered)

1. **Service layer refactor (above)**
2. **Invoices** — auto-generate per project billing schedule; PDF output; mark time entries as billed
3. **Email invoices** — send generated PDFs via Gmail SMTP
4. **Client detail page** — drill into one client: projects, time, invoices, revenue
5. **Project detail page** — same for one project

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
