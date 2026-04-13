# DynaBill

Internal billing and finance tracker for Dynamiq Solutions.

Tracks clients, rates, invoices, and payments in one place — no more spreadsheets.

## Features

- Client management with rate tracking (hourly / project / retainer)
- Rate change history so nothing gets missed
- Light and dark mode
- Local SQLite database — no cloud, no accounts
- PDF invoice generation (coming soon)
- Email integration (coming soon)
- Payment tracking and overdue alerts (coming soon)

## Setup

Requires JDK 21+ and Maven.

```bash
mvn package
java -jar target/dynabill-1.0.0.jar
```

## Tech

Java 21, Swing, FlatLaf, SQLite, Lucide icons, Inter font.
