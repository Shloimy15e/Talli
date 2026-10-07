# Talli MCP

Talli exposes a private, stateless Streamable HTTP MCP server at:

```text
https://<your-talli-host>/mcp
```

## Native discovery and incoming email events

The endpoint supports MCP 2.0 discovery (`2026-07-28`) alongside existing Streamable HTTP tool calls and the legacy initialization handshake. Native `email.received` events can monitor `theo@dynamiq.dev` through a verified, signed webhook subscription. Event delivery is disabled by default; see [MCP Events setup and plugin refresh](docs/mcp-events.md) for configuration, subscription lifecycle, and later dot activation.

## Connect from ChatGPT

Before deploying, set Railway's `APP_BASE_URL` variable to the exact public HTTPS origin of Talli, without `/mcp` or another path:

```text
APP_BASE_URL=https://your-talli-domain.up.railway.app
```

After the deployment and database migration complete:

1. In ChatGPT, open **Settings > Security and login** and enable **Developer mode**.
2. Open **Plugins**, select the plus button, and create a connection.
3. Enter `https://<your-talli-host>/mcp` as the MCP server URL. No OAuth client ID or secret is needed.
4. When ChatGPT opens Talli, sign in as the Talli user the agent should act as.

Talli publishes the required OAuth discovery metadata, registers ChatGPT automatically, and uses the authorization-code flow with S256 PKCE. OAuth access tokens last one hour by default; refresh tokens last 30 days and rotate on use. Every request reloads the linked user's current roles, permissions, and enabled state, so disabling the user immediately blocks existing access tokens.

The dynamic registration endpoint accepts only current or legacy `https://chatgpt.com` OAuth callback URLs. See OpenAI's [authentication](https://developers.openai.com/plugins/build/auth) and [connection](https://developers.openai.com/plugins/deploy/connect-chatgpt) guides for the corresponding ChatGPT flow.

Optional Railway settings:

```text
OAUTH_DYNAMIC_CLIENT_LIMIT=25
OAUTH_ACCESS_TOKEN_TTL=PT1H
OAUTH_REFRESH_TOKEN_TTL=P30D
```

Durations use ISO-8601 syntax. Restart Talli after changing them.

## Connect other MCP clients

Clients that support custom request headers can continue to use a Talli personal access token:

```http
Authorization: Bearer talli_<token>
```

Create the token on Talli's **Profile** page. The token inherits its user's current role permissions. Store it in the agent's secret/environment configuration, never in a repository or committed MCP config.

Generic MCP client configuration:

```json
{
  "mcpServers": {
    "talli": {
      "type": "http",
      "url": "https://<your-talli-host>/mcp",
      "headers": {
        "Authorization": "Bearer ${TALLI_API_TOKEN}"
      }
    }
  }
}
```

Environment-variable syntax differs between MCP clients. If the client does not expand `${TALLI_API_TOKEN}`, use its native secret/header setting instead of placing the token in a checked-in file.

## Tool surface

Read tools:

- `find_clients`, `find_projects`
- `find_time_entries`, `current_timer`
- `find_expenses`, `find_subscriptions`
- `find_invoices`, `get_invoice`
- `run_report` for financial trends, client P&L, time utilization, receivables aging, project revenue, expense categories, payment history, and outstanding invoices
- `list_email_senders` to list all active sender profiles, their default status, and HTML signatures
- `find_client_emails` to discover a client's recorded emails with their source, initiating account, provider ID, send status, errors, and delivery-event timestamps
- `get_email_conversation` to read those audit details and the messages in a conversation before replying
- `preview_client_email` to render the exact sender, recipient, visible CC, body, template, and signature before sending client or standalone email

List tools return up to 100 records per call. Use `offset` to continue through all matching records.

Write tools:

- `create_client`, `update_client`
- `create_project`, `update_project`
- `log_time`, `start_timer`, `stop_timer`, `update_time_entry`, `delete_time_entry`
- `log_expense`, `update_expense`, `delete_expense`
- `create_subscription`, `update_subscription`, `delete_subscription`, `cancel_subscription`, `reactivate_subscription`
- `record_subscription_charge`, `link_expense_to_subscription`, `unlink_expense_from_subscription`
- `record_payment` for settled transactions from any bank or payment provider
- `delete_payment` to remove a recorded payment
- `set_invoice_ach_link` for a validated Mercury ACH payment link
- `send_client_email` for a previously previewed and explicitly approved client or standalone email

There are no invoice-generation or money-movement tools. Each tool checks the matching Talli permission (`view-*`, `manage-*`, or `send-emails`) at execution time; inbox reads and threaded replies additionally retain admin-only access.

`record_payment` requires a provider slug and that provider's stable transaction ID. Their combination is unique, allowing multiple bank accounts while making retries safe. The supplied currency must match the invoice, and Talli rejects overpayments through its existing payment service.

## Agent email

Email is a two-step workflow:

1. Call `list_email_senders`, then choose an active profile's address as `senderEmail`. If omitted, Talli uses the profile currently marked as default.
2. Call `preview_client_email`. It sends nothing and returns both the plain and rendered HTML bodies plus a `previewToken` bound to the exact sender, recipients, subject, body, template, signature, reply target, and oversight choice. Set `includeOversightCc=true` only when the owner should receive a visible copy; it defaults to false.
3. After a human approves that preview, call `send_client_email` with the same inputs, including `includeOversightCc`, its token, and `confirmSend=true`. Changed or unpreviewed content is rejected.

For client email, provide `clientId` for an existing client with a valid saved email address. For standalone email, omit `clientId` and provide `toAddress`; no client record is required. An explicit `toAddress` supplied with `clientId` must match that client's saved address. When `includeOversightCc=true`, agent email visibly CCs `${MCP_EMAIL_CC}`, which defaults to `shloimy@dynamiq.dev`, for owner oversight and stores that CC in the email record. Talli omits the oversight CC when the address is already To or From. Threaded replies retain other valid saved CC/BCC recipients regardless of the oversight choice; opting out also removes the configured oversight address from a saved reply envelope. Separately, Talli records `mcp` as the source and the authenticated account email as the initiator before delivery begins, so failed attempts remain attributable without relying on recipient copies.

Admins manage shared sender profiles at **Emails → Sender profiles** (`/emails/senders`). Each profile has an address, display name, HTML signature, active status, and default status. The migration starts with the existing `info@dynamiq.dev` (default), `billing@dynamiq.dev`, `finance@dynamiq.dev`, `support@dynamiq.dev`, and `sales@dynamiq.dev` identities. Admins can edit them and add more; each sending domain must be verified in Resend. Disabling a profile removes it from selection without rewriting historical emails.

The browser composer and MCP share this catalog. System email uses its default profile. `MAIL_FROM` and `MAIL_FROM_NAME` no longer control sending identity. `RESEND_API_KEY` and webhook configuration remain provider settings; `MCP_EMAIL_CC` controls the owner oversight copy.

The body is plain text and is safely escaped when HTML is required. `templateId` may be omitted or set to `branded`, `branded-notice`, `formal`, or `minimal`. `includeSignature` defaults to true; set it to false for an unsigned email. The selected profile supplies its own HTML signature and template branding. Updating its name or included signature invalidates older previews; preview again before sending.

### Replying in a conversation

Find a message with `find_client_emails`, read its thread with `get_email_conversation`, and pass that message's ID as `replyToEmailId` to both preview and send. Talli keeps the reply recipient and subject tied to the original message, checks the client association, and binds the reply headers into the preview token. A new email omits `replyToEmailId`.

The browser email detail page also offers **Reply** and shows expandable messages in that conversation. The composer suggests the sender profile that received or sent the original message; another active profile can be chosen explicitly.

Email discovery and conversation reads accept `offset` and `limit` (default 50, maximum 100) and return `nextOffset` when another page is available. Discovery returns short body excerpts; conversation reads include the full plain and HTML bodies. Both expose the local source and initiator, provider ID, current send status, send error or bounce reason, and separate timestamps for known provider delivery events. Historical messages created before this audit metadata was added return null for source and initiator. Inbox reads and threaded replies retain the app's existing admin-only inbox access. Ordinary client email and sender discovery continue to require `send-emails`.

Threading uses RFC `Message-ID`, `In-Reply-To`, and `References`, following [Resend's reply guidance](https://resend.com/docs/dashboard/receiving/reply-to-emails). Sent message IDs come from the provider's retrieval endpoint and email webhooks. Ensure the Resend webhook subscription includes `email.sent` as well as `email.received` and the delivery events already used by the app.

The one-time email migration groups existing messages by external contact and normalized subject (ignoring `Re:` prefixes), keeping different known local sender identities separate. It writes their conversation root IDs once; normal reads use those stored IDs without a legacy fallback. New messages are linked through their RFC headers. The migration does not manufacture message IDs: an original message ID is still required to send a reliable in-thread reply. Previous personal signature HTML can be copied from **Profile** into a sender profile.

## Agent examples

- “Find the Acme website project and log 90 billable minutes for planning. I started at 9:15 AM today.”
- “Log a $49.99 software expense for GitHub against the Acme website project today.”
- “Start a timer for the internal admin project, then tell me its status.”
- “Show client profit and loss for the last quarter and drill into the largest expenses.”
- “Draft a signed, branded payment reminder for Acme, show me the preview, and wait for my approval before sending it.”

Use the camelCase argument names published in each tool schema. When `log_time.startedAt` is omitted, the entry ends at the current time and starts `durationMinutes` earlier. Expense categories are `software`, `hardware`, `travel`, `meals`, `contractors`, `office`, `marketing`, `taxes`, and `other`.
