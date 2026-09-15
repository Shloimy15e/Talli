package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalEmailTemplateRenderingTest {

    private static final Path PREVIEW_DIRECTORY = Path.of("docs", "email-templates");
    private static final Path PREVIEW_GALLERY = PREVIEW_DIRECTORY.resolve("preview.html");
    private static final Path ROLE_SIGNATURE = Path.of("docs", "email-signatures", "role.html");
    private static final String INSTALLABLE_START = "<!-- installable-signature:start -->";
    private static final String INSTALLABLE_END = "<!-- installable-signature:end -->";
    private static final String FIXTURES_START = "  <!-- transactional-email-fixtures:start -->";
    private static final String FIXTURES_END = "  <!-- transactional-email-fixtures:end -->";
    private static final String PAYMENT_URL = "https://pay.example.test/invoices/preview";

    private final SpringTemplateEngine templateEngine = templateEngine();

    @Test
    void rendersInvoiceDetailsAndAvailablePaymentActionWithSignatureOnce() throws IOException {
        Invoice invoice = invoice("INV-2026-1042", "4850.00", "1000.00",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        invoice.setPeriodStart(LocalDate.of(2026, 8, 1));
        invoice.setPeriodEnd(LocalDate.of(2026, 8, 31));

        String html = renderInvoice(invoice, PAYMENT_URL, "North & <Co>");

        assertThat(html)
                .contains("North &amp; &lt;Co&gt;")
                .contains("USD 4,850.00", "INV-2026-1042", "September 30, 2026", "Aug 1 – Aug 31, 2026")
                .contains("href=\"" + PAYMENT_URL + "\"")
                .contains("background:#ea7c28; color:#ffffff")
                .contains("border-radius:10px")
                .containsOnlyOnce("class=\"dynamiq-email-signature\"")
                .doesNotContain("Thanks,<br>", "text-transform:uppercase", "box-shadow", "#161f30");
    }

    @Test
    void hidesPaymentActionWithoutAUrlOrForANonOutstandingInvoice() throws IOException {
        Invoice invoice = invoice("INV-2026-1042", "4850.00", "0.00",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(renderInvoice(invoice, null, "Northline Studio"))
                .doesNotContain("Pay with ACH", "Pay securely by ACH through Mercury.");

        invoice.setAmountWrittenOff(invoice.getAmount());
        invoice.setStatus("written_off");
        assertThat(renderInvoice(invoice, PAYMENT_URL, "Northline Studio"))
                .doesNotContain("href=\"" + PAYMENT_URL + "\"", "Pay with ACH");
    }

    @Test
    void rendersMultipleReminderRowsIncludingOverdueAndOpenEndedDueDates() throws IOException {
        Invoice overdue = invoice("INV-2026-1042", "4850.00", "1000.00",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 10));
        Invoice openEnded = invoice("INV-2026-1048", "975.00", "0.00",
                LocalDate.of(2026, 9, 12), null);

        String html = renderReminder(List.of(overdue, openEnded), LocalDate.of(2026, 9, 17), "North & <Co>");

        assertThat(html)
                .contains("North &amp; &lt;Co&gt;")
                .contains("following invoice<span>s</span>", "remain unpaid")
                .contains("INV-2026-1042", "USD 3,850.00", "7d overdue")
                .contains("INV-2026-1048", "USD 975.00", "Due <span>&mdash;</span>")
                .contains("table-layout:fixed", "border-radius:10px")
                .containsOnlyOnce("class=\"dynamiq-email-signature\"")
                .doesNotContain("Thanks,<br>", "text-transform:uppercase", "box-shadow", "#161f30");
    }

    @Test
    void previewArtifactsAreActualThymeleafRendersAndMatchTheGallery() throws IOException {
        String invoice = renderInvoice(invoice("INV-2026-1042", "4850.00", "1000.00",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), PAYMENT_URL, "Northline Studio");
        Invoice overdue = invoice("INV-2026-1042", "4850.00", "1000.00",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 10));
        Invoice openEnded = invoice("INV-2026-1048", "975.00", "0.00",
                LocalDate.of(2026, 9, 12), null);
        String reminder = renderReminder(List.of(overdue, openEnded),
                LocalDate.of(2026, 9, 17), "Northline Studio");

        if (Boolean.getBoolean("transactionalEmailPreviews.update")) {
            Files.createDirectories(PREVIEW_DIRECTORY);
            Files.writeString(PREVIEW_DIRECTORY.resolve("invoice.html"), invoice);
            Files.writeString(PREVIEW_DIRECTORY.resolve("reminder.html"), reminder);
            Files.writeString(PREVIEW_GALLERY,
                    withEmbeddedPreviews(Files.readString(PREVIEW_GALLERY), invoice, reminder));
        }

        String gallery = Files.readString(PREVIEW_GALLERY);
        assertThat(Files.readString(PREVIEW_DIRECTORY.resolve("invoice.html"))).isEqualTo(invoice);
        assertThat(Files.readString(PREVIEW_DIRECTORY.resolve("reminder.html"))).isEqualTo(reminder);
        assertThat(embeddedPreview(gallery, "invoice")).isEqualTo(invoice);
        assertThat(embeddedPreview(gallery, "reminder")).isEqualTo(reminder);
    }

    private String renderInvoice(Invoice invoice, String paymentUrl, String clientName) throws IOException {
        Client client = new Client();
        client.setName(clientName);
        invoice.setClient(client);

        Context context = commonContext();
        context.setVariable("client", client);
        context.setVariable("invoice", invoice);
        context.setVariable("mercuryPaymentUrl", paymentUrl);
        return templateEngine.process("emails/invoice", context);
    }

    private String renderReminder(List<Invoice> invoices, LocalDate today, String clientName) throws IOException {
        Client client = new Client();
        client.setName(clientName);
        invoices.forEach(invoice -> invoice.setClient(client));

        Context context = commonContext();
        context.setVariable("client", client);
        context.setVariable("invoices", invoices);
        context.setVariable("today", today);
        return templateEngine.process("emails/reminder", context);
    }

    private Context commonContext() throws IOException {
        Context context = new Context();
        context.setVariable("fromName", "Dynamiq Solutions");
        context.setVariable("businessName", "Dynamiq Solutions");
        context.setVariable("businessEmail", "billing@dynamiq.dev");
        context.setVariable("businessAddress", "100 Cherry Ln, Airmont, NY 10952");
        context.setVariable("signatureResponsiveStyles", EmailTemplateCatalog.signatureStyles());
        context.setVariable("signature", installableSignature(Files.readString(ROLE_SIGNATURE))
                .replace("{{role}}", EmailService.plainToHtml("Billing"))
                .replace("{{email}}", EmailService.plainToHtml("billing@dynamiq.dev")));
        return context;
    }

    private static Invoice invoice(String reference, String amount, String paid,
                                   LocalDate issuedAt, LocalDate dueAt) {
        Invoice invoice = new Invoice();
        invoice.setReference(reference);
        invoice.setCurrency("USD");
        invoice.setAmount(new BigDecimal(amount));
        invoice.setAmountPaid(new BigDecimal(paid));
        invoice.setAmountWrittenOff(BigDecimal.ZERO);
        invoice.setStatus("unpaid");
        invoice.setIssuedAt(issuedAt);
        invoice.setDueAt(dueAt);
        return invoice;
    }

    private static SpringTemplateEngine templateEngine() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static String withEmbeddedPreviews(String gallery, String invoice, String reminder) {
        int start = gallery.indexOf(FIXTURES_START);
        int end = gallery.indexOf(FIXTURES_END);
        if (start < 0 || end < start) {
            throw new IllegalStateException("Transactional email gallery fixture markers are missing.");
        }

        String fixtures = FIXTURES_START + '\n'
                + "<script type=\"text/plain\" id=\"preview-source-invoice\">" + invoice + "</script>\n"
                + "<script type=\"text/plain\" id=\"preview-source-reminder\">" + reminder + "</script>\n"
                + FIXTURES_END;
        return gallery.substring(0, start) + fixtures
                + gallery.substring(end + FIXTURES_END.length());
    }

    private static String embeddedPreview(String gallery, String previewId) {
        String openingTag = "<script type=\"text/plain\" id=\"preview-source-" + previewId + "\">";
        int start = gallery.indexOf(openingTag);
        if (start < 0) return "";
        start += openingTag.length();
        int end = gallery.indexOf("</script>", start);
        return end < 0 ? "" : gallery.substring(start, end);
    }

    private static String installableSignature(String standaloneSignature) {
        int start = standaloneSignature.indexOf(INSTALLABLE_START);
        int end = standaloneSignature.indexOf(INSTALLABLE_END, start + INSTALLABLE_START.length());
        if (start < 0 || end < start) return "";
        return standaloneSignature.substring(start + INSTALLABLE_START.length(), end).trim();
    }
}
