package dev.dynamiq.talli.integration.mercury;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.support.RefreshDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RefreshDatabaseTest
class MercuryInvoiceTemplateTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void emailAndPdfRenderMercuryPaymentLink() {
        Invoice invoice = invoice();
        String paymentUrl = "https://app.mercury.com/pay/pay-123";

        Context email = commonContext(invoice, paymentUrl);
        email.setVariable("client", invoice.getClient());

        Context pdf = commonContext(invoice, paymentUrl);
        pdf.setVariable("items", List.of(invoiceItem(invoice)));
        pdf.setVariable("balance", invoice.balance());

        assertThat(templateEngine.process("emails/invoice", email))
                .contains("href=\"" + paymentUrl + "\"")
                .contains("Pay invoice securely");
        assertThat(templateEngine.process("invoices/pdf", pdf))
                .contains("href=\"" + paymentUrl + "\"")
                .contains("Pay this invoice");
    }

    @Test
    void emailAndPdfHideMercuryPaymentLinkForWrittenOffInvoice() {
        Invoice invoice = invoice();
        invoice.setAmountWrittenOff(invoice.getAmount());
        invoice.setStatus("written_off");
        String paymentUrl = "https://app.mercury.com/pay/pay-123";

        Context email = commonContext(invoice, paymentUrl);
        email.setVariable("client", invoice.getClient());

        Context pdf = commonContext(invoice, paymentUrl);
        pdf.setVariable("items", List.of(invoiceItem(invoice)));
        pdf.setVariable("balance", invoice.balance());

        assertThat(templateEngine.process("emails/invoice", email))
                .doesNotContain("href=\"" + paymentUrl + "\"");
        assertThat(templateEngine.process("invoices/pdf", pdf))
                .doesNotContain("href=\"" + paymentUrl + "\"");
    }

    private Context commonContext(Invoice invoice, String paymentUrl) {
        Context context = new Context();
        context.setVariable("invoice", invoice);
        context.setVariable("mercuryPaymentUrl", paymentUrl);
        context.setVariable("businessName", "Dynamiq Solutions Inc");
        context.setVariable("businessEmail", "info@dynamiq.dev");
        context.setVariable("businessAddress", "100 Cherry Ln");
        return context;
    }

    private Invoice invoice() {
        Client client = new Client();
        client.setName("Acme Corp");
        client.setEmail("billing@acme.test");

        Invoice invoice = new Invoice();
        invoice.setClient(client);
        invoice.setReference("INV-1042");
        invoice.setCurrency("USD");
        invoice.setAmount(new BigDecimal("250.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus("unpaid");
        invoice.setIssuedAt(LocalDate.of(2026, 8, 13));
        invoice.setDueAt(LocalDate.of(2026, 9, 12));
        return invoice;
    }

    private InvoiceItem invoiceItem(Invoice invoice) {
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setDescription("Consulting");
        item.setUnitCount(new BigDecimal("2.5"));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTotal(new BigDecimal("250.00"));
        return item;
    }
}
