# Talli MCP

Talli exposes a private, stateless Streamable HTTP MCP server at:

```text
https://<your-talli-host>/mcp
```

Authenticate every request with a Talli personal access token:

```http
Authorization: Bearer talli_<token>
```

Create the token on Talli's **Profile** page. The token inherits its user's existing role permissions. Store it in the agent's secret/environment configuration, never in a repository or committed MCP config.

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

List tools return up to 100 records per call. Use `offset` to continue through all matching records.

Additive/update tools:

- `create_client`, `update_client`
- `create_project`, `update_project`
- `log_time`, `start_timer`, `stop_timer`
- `log_expense`
- `record_payment` for settled transactions from any bank or payment provider
- `set_invoice_ach_link` for a validated Mercury ACH payment link

There are no delete, invoice-generation, payment-deletion, email, or money-movement tools. Each tool also checks the matching Talli permission (`view-*` or `manage-*`) at execution time.

`record_payment` requires a provider slug and that provider's stable transaction ID. Their combination is unique, allowing multiple bank accounts while making retries safe. The supplied currency must match the invoice, and Talli rejects overpayments through its existing payment service.

## Agent examples

- “Find the Acme website project and log 90 billable minutes for planning. I started at 9:15 AM today.”
- “Log a $49.99 software expense for GitHub against the Acme website project today.”
- “Start a timer for the internal admin project, then tell me its status.”
- “Show client profit and loss for the last quarter and drill into the largest expenses.”

When `log_time.started_at` is omitted, the entry ends at the current time and starts `duration_minutes` earlier. Expense categories are `software`, `hardware`, `travel`, `meals`, `contractors`, `office`, `marketing`, `taxes`, and `other`.
