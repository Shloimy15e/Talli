package dev.dynamiq.talli.reporting;

import java.util.*;

/** The public field vocabulary and its private, trusted SQL expressions. */
public final class ReportCatalog {
    private ReportCatalog() {}
    public record Field(String key, String label, String type, boolean numeric, boolean filterOnly) {}
    public record Dataset(String key, String label, String dateField, List<Field> fields,
            List<String> defaultColumns, List<String> notes) {}
    record Definition(Dataset dataset, String fromSql, Map<String, String> expressions, Set<String> money) {}

    static final Map<String, Definition> DEFINITIONS = definitions();
    public static List<Dataset> catalog() {
        return DEFINITIONS.values().stream().map(Definition::dataset).toList();
    }
    private static Map<String, Definition> definitions() {
        Map<String, Definition> result = new LinkedHashMap<>();
        Builder i = new Builder("invoices", "Invoices", "issuedAt",
                "invoices i JOIN clients c ON c.id=i.client_id");
        i.common("i").field("reference", "Invoice Reference", "string", "i.reference")
                .field("status", "Status", "string", "i.status").date("issuedAt", "Issued Date", "i.issued_at")
                .field("dueAt", "Due Date", "date", "i.due_at").money("amount", "Amount", "i.amount")
                .money("amountPaid", "Amount Paid", "i.amount_paid")
                .money("amountWrittenOff", "Written Off", "i.amount_written_off")
                .money("balance", "Current Balance", "i.amount-i.amount_paid-i.amount_written_off")
                .field("outstanding", "Outstanding", "boolean", "i.status IN ('unpaid','overdue') AND i.amount-i.amount_paid-i.amount_written_off>0")
                .field("agingBucket", "Current Aging", "string", "CASE WHEN i.due_at IS NULL OR i.due_at>=CURRENT_DATE THEN 'Current' WHEN i.due_at>=CURRENT_DATE-INTERVAL '30' DAY THEN '1-30' WHEN i.due_at>=CURRENT_DATE-INTERVAL '60' DAY THEN '31-60' WHEN i.due_at>=CURRENT_DATE-INTERVAL '90' DAY THEN '61-90' ELSE '90+' END")
                .field("amountUsd", "Amount (USD)", "number", usd("i.amount","i.currency","i.exchange_rate"))
                .field("exchangeRate", "Stored Exchange Rate", "number", "i.exchange_rate").projectsFilter();
        i.finish(result, List.of("reference", "clientName", "issuedAt", "dueAt", "status", "currency", "amount", "amountPaid", "balance"),
                "Balances, payments and status reflect the current invoice state, not an as-of historical ledger.",
                "The issued-date range excludes older invoices outside the range, including any still outstanding. Aging uses the database current date and is independent of the selected range.",
                "Project filters select whole invoices containing matching project lines; invoice amounts are not attributed to individual projects.");
        Builder p = new Builder("payments", "Payments", "paidAt", "payments p JOIN invoices i ON i.id=p.invoice_id JOIN clients c ON c.id=i.client_id");
        p.field("id", "ID", "number", "p.id").field("clientId", "Client ID", "number", "c.id")
                .field("clientName", "Client", "string", "c.name").field("invoiceId", "Invoice ID", "number", "i.id")
                .field("invoiceReference", "Invoice Reference", "string", "i.reference").date("paidAt", "Payment Date", "p.paid_at")
                .field("currency", "Currency", "string", "i.currency").money("amount", "Amount", "p.amount")
                .field("amountUsd", "Amount (USD)", "number", usd("p.amount","i.currency","p.exchange_rate"))
                .field("exchangeRate", "Stored Exchange Rate", "number", "p.exchange_rate")
                .field("source", "Source", "string", "p.source").field("method", "Method", "string", "p.method")
                .field("reference", "Payment Reference", "string", "p.reference").projectsFilter();
        p.finish(result, List.of("paidAt", "invoiceReference", "clientName", "source", "method", "currency", "amount"),
                "Direct payments record fresh cash; credit payments apply previously received deposits and are not new cash receipts.",
                "Project filters select payments against whole invoices containing matching project lines.");
        Builder e = new Builder("expenses", "Expenses", "incurredOn", "expenses e LEFT JOIN clients c ON c.id=e.client_id LEFT JOIN projects pr ON pr.id=e.project_id");
        e.common("e").projects("e.project_id").date("incurredOn", "Incurred Date", "e.incurred_on")
                .field("category", "Category", "string", "e.category").field("vendor", "Vendor", "string", "e.vendor")
                .field("description", "Description", "string", "e.description").money("amount", "Amount", "e.amount")
                .field("amountUsd", "Amount (USD)", "number", usd("e.amount","e.currency","e.exchange_rate"))
                .field("exchangeRate", "Stored Exchange Rate", "number", "e.exchange_rate")
                .field("billable", "Billable", "boolean", "e.billable").field("billed", "Billed", "boolean", "e.billed")
                .field("paymentMethod", "Payment Method", "string", "e.payment_method");
        e.finish(result, List.of("incurredOn", "vendor", "category", "clientName", "projectName", "currency", "amount", "billable", "billed"));
        Builder t = new Builder("time_entries", "Time Entries", "startedAt", "time_entries t JOIN projects pr ON pr.id=t.project_id JOIN clients c ON c.id=pr.client_id");
        t.field("id", "ID", "number", "t.id").field("clientId", "Client ID", "number", "c.id")
                .field("clientName", "Client", "string", "c.name").projects("t.project_id")
                .date("startedAt", "Started Date", "CAST(t.started_at AS DATE)")
                .field("endedAt", "Ended Date", "date", "CAST(t.ended_at AS DATE)")
                .field("description", "Description", "string", "t.description")
                .field("billable", "Billable", "boolean", "t.billable").field("billed", "Billed", "boolean", "t.billed")
                .field("running", "Running", "boolean", "t.ended_at IS NULL")
                .field("rateType", "Project Rate Type", "string", "pr.rate_type")
                .field("currency", "Project Currency", "string", "pr.currency")
                .money("rate", "Captured Rate", "t.rate")
                .field("minutes", "Completed Minutes", "number", "CASE WHEN t.ended_at IS NOT NULL THEN t.duration_minutes ELSE NULL END")
                .field("hours", "Completed Hours", "number", "CASE WHEN t.ended_at IS NOT NULL THEN t.duration_minutes/60.0 ELSE NULL END")
                .money("value", "Billable Hourly Value", "CASE WHEN t.ended_at IS NOT NULL AND t.billable=TRUE AND pr.rate_type='hourly' THEN t.duration_minutes/60.0*t.rate ELSE NULL END");
        t.finish(result, List.of("startedAt", "clientName", "projectName", "description", "billable", "billed", "running", "hours", "currency", "rate", "value"),
                "Running timers have null completed duration and value. Value includes completed billable hourly work at the captured entry rate; fixed and retainer work has no hourly value.",
                "Time currency and rate type reflect the current project; no historical currency snapshot or exchange rate exists for time entries.");
        return Collections.unmodifiableMap(result);
    }
    private static String usd(String amount,String currency,String rate) {
        return "CASE WHEN "+currency+"='USD' THEN "+amount+" WHEN "+rate+">0 THEN ROUND("+amount+"/NULLIF("+rate+",0),2) ELSE NULL END";
    }
    private static final class Builder {
        final String key, label, date, from;
        final List<Field> fields = new ArrayList<>();
        final Map<String,String> expressions = new LinkedHashMap<>();
        final Set<String> money = new LinkedHashSet<>();
        Builder(String key,String label,String date,String from) { this.key=key;this.label=label;this.date=date;this.from=from; }
        Builder field(String key,String label,String type,String expression) {
            fields.add(new Field(key,label,type,type.equals("number"),false)); expressions.put(key,expression);return this;
        }
        Builder money(String key,String label,String expression) { money.add(key);return field(key,label,"number",expression); }
        Builder common(String a) { return field("id","ID","number",a+".id").field("clientId","Client ID","number","c.id").field("clientName","Client","string","c.name").field("currency","Currency","string",a+".currency"); }
        Builder projects(String expression) { return field("projectId","Project ID","number",expression).field("projectName","Project","string","pr.name"); }
        Builder projectsFilter() {
            fields.add(new Field("projectId","Project ID (Filter)","number",true,true));
            fields.add(new Field("projectName","Project (Filter)","string",false,true));
            expressions.put("projectId","ip.id");expressions.put("projectName","ip.name");return this;
        }
        Builder date(String key,String label,String expression) {
            return field(key,label,"date",expression).field("day","Day","date",expression)
                    .field("month","Month","string","SUBSTRING(CAST("+expression+" AS VARCHAR),1,7)")
                    .field("year","Year","number","EXTRACT(YEAR FROM "+expression+")");
        }
        void finish(Map<String,Definition> definitions,List<String> defaults,String... notes) {
            List<String> n=new ArrayList<>(List.of(notes));
            n.add("Operational report totals are not a formal accounting profit-and-loss statement.");
            if (expressions.containsKey("amountUsd")) n.add("amountUsd uses USD amounts directly and converts other currencies at their stored exchange rate, rounded per row to two decimals; totals are USD. Nonpositive stored rates yield null conversions.");
            n.add("Native monetary aggregates are separated by currency. Date boundaries are inclusive calendar dates; time entries use the stored start date.");
            definitions.put(key,new Definition(new Dataset(key,label,date,List.copyOf(fields),defaults,List.copyOf(n)),from,Collections.unmodifiableMap(expressions),Set.copyOf(money)));
        }
    }
}
