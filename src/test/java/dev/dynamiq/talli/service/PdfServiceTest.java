package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfServiceTest {

    @Test
    void rendersLegacyScientificHourlyRateAsPlainDecimalWithoutChangingStoredDescription() throws IOException {
        InvoiceItem item = item("Website development — 2.5h @ USD 8E+1/hr");

        assertThat(renderText(item))
                .contains("Website development — 2.5h @ USD 80/hr")
                .doesNotContain("8E+1");
        assertThat(item.getDescription()).isEqualTo("Website development — 2.5h @ USD 8E+1/hr");
    }

    @Test
    void preservesCustomDescriptionsAndAlreadyPlainHourlyRates() throws IOException {
        InvoiceItem custom = item("Research for 8E+1 devices at USD 8E+1/hr");
        InvoiceItem plain = item("Website development — 2.5h @ USD 80.50/hr");

        assertThat(renderText(custom, plain))
                .contains(custom.getDescription(), plain.getDescription());
    }

    private String renderText(InvoiceItem... items) throws IOException {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        Client client = new Client();
        client.setName("Example client");
        Invoice invoice = new Invoice();
        invoice.setReference("INV-TEST");
        invoice.setClient(client);
        invoice.setIssuedAt(LocalDate.of(2026, 10, 9));
        invoice.setAmount(new BigDecimal("200"));

        byte[] pdf = new PdfService(engine).renderInvoice(invoice, List.of(items));
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private InvoiceItem item(String description) {
        InvoiceItem item = new InvoiceItem();
        item.setDescription(description);
        item.setUnit("hr");
        item.setUnitCount(new BigDecimal("2.5"));
        item.setUnitPrice(new BigDecimal("80"));
        item.setTotal(new BigDecimal("200"));
        return item;
    }
}
