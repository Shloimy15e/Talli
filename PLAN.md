# Talli — Plan

## Vision

An internal PSA (Professional Services Automation) tool for Dynamiq Solutions.
One place for clients, projects, time, invoices, and expenses.
Replace the current spreadsheet-based workflow; eliminate the "forgot to invoice" and "forgot the rate change" problems.

## Current state (v2 / Spring Boot web)

- ✅ Clients CRUD
- ✅ Projects CRUD (with client FK, rate type, currency, billing schedule)
- ✅ Inline client creation from project form
- ✅ Time entries CRUD + running timer with live counter
- ✅ Dashboard (counts, time this week, unbilled, recent activity)
- ✅ Dark sidebar + light content, Dynamiq branding
- ✅ Thymeleaf + HTMX + Alpine for interactions
- ✅ Flyway migrations, PostgreSQL

## Now

**Refactor controllers to extract services.** Controllers shouldn't hold business logic. Extract `TimeEntryService`, `DashboardService`, `ProjectService`. Makes future invoicing/reporting/exports clean.

## Next up (ordered)

1. **Expenses** — quick-add, category, attachable to client/project, currency
2. **Invoices** — auto-generate per project billing schedule; PDF output; mark time entries as billed when included
3. **Email invoices** — Gmail SMTP from the app
4. **Client detail page** — drill into one client to see their projects, time, invoices, revenue
5. **Project detail page** — same for one project
6. **Auth** — Spring Security; single-user now, team members later
7. **Production deploy** — Dockerfile, Railway/Fly.io, env-based config

## Later / extensions

- **Chrome extension** — one-click time tracking + quick expense entry, talks to Talli via REST API
- **Raycast extension** — log time, add expense, look up clients from Spotlight-style launcher
- **Reports** — monthly/yearly revenue, per-client P&L, time utilization
- **Rate change tracking** — if/when retainers need mid-project rate changes
- **Currency conversion** — show revenue in a base currency alongside original

## Deliberately not doing

- Sales pipeline / CRM-style lead management (not a CRM)
- Multi-tenancy or becoming a SaaS product (internal tool only)
- Mobile app (responsive web + Chrome/Raycast extensions cover mobile needs)

## Stack reference

- Java 21, Spring Boot 3.5 (Spring Web, Data JPA, Thymeleaf, Validation, DevTools)
- PostgreSQL + Flyway migrations
- Tailwind (CDN for now) + HTMX + Alpine.js
- Inter font, Dynamiq brand palette (#ea7c28 on #161f30)
- Hosted on GitHub: https://github.com/Shloimy15e/Talli (tag `v1-swing` preserves the abandoned desktop version)
