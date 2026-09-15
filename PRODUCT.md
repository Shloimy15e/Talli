# Talli

## Platform
Web application: Spring Boot, Thymeleaf, HTMX, Alpine.js and PostgreSQL.

## Product
Internal client, project, time and billing workspace for Dynamiq Solutions.
Email belongs to the client workflow: read conversations, reply, and compose from shared sender profiles.

## Email commitments
- The user wants the usability and craft of Gmail or iCloud Mail.
- The confirmed reading flow is a conversation list beside the reading pane.
- Sender profiles are managed in the app and own their display name and optional HTML signature.
- Browser email and MCP use the same sender and conversation services.
- Historical grouping is a one-time migration; normal operation uses stored conversation roots.
- Prefer clear, carefully factored code with one source of truth over compatibility scaffolding.

## Boundaries
Preserve invoice/client links, attachment handling and access controls. Do not imply delivery, cloud draft sync, or reply headers when they are unavailable.
