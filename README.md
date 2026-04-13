# DynaBill

A desktop billing & finance tracker for freelancers and small agencies. Built with Java Swing.

I got tired of juggling Google Sheets for client rates, invoices, and payments — and missing things like rate changes because life got busy. DynaBill keeps everything in one place with a UI that doesn't make you want to close it immediately.

## What it does (so far)

- **Client management** — add, edit, delete clients with rates (hourly/project/retainer)
- **Rate history** — never forget a rate change again
- **Local SQLite database** — your data stays on your machine, no accounts or cloud sync
- **Light/dark mode** — toggle in the sidebar, persists across sessions

## What's coming

- Invoice generation (PDF)
- Email invoices directly from the app
- Payment tracking
- Dashboard with revenue overview and overdue alerts

## Running it

You'll need JDK 21+ and Maven.

```bash
mvn package
java -jar target/dynabill-1.0.0.jar
```

Or just double-click the jar.

## Stack

- Java 21
- Swing + FlatLaf (modern look-and-feel)
- FlatLaf Extras (SVG icon support)
- SQLite via JDBC
- Lucide icons
- Inter font (bundled)

## Why a desktop app?

Most billing tools are web apps that want a monthly subscription for features you could build in a weekend. This runs locally, starts instantly, and doesn't need internet. Plus I wanted to learn Java.
