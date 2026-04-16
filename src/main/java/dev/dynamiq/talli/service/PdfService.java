package dev.dynamiq.talli.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.service.ClientService.AgingBuckets;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Renders domain objects as PDF bytes by running a dedicated Thymeleaf template
 * (no layout chrome, inline print-friendly CSS) through OpenHTMLtoPDF.
 *
 * Kept separate from {@code MediaService} because the concern is rendering;
 * persisting the resulting bytes is the caller's job (via {@code MediaService.attachBytes}).
 */
@Service
public class PdfService {

    private final SpringTemplateEngine templateEngine;

    public PdfService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] renderInvoice(Invoice invoice, List<InvoiceItem> items) {
        BigDecimal balance = invoice.getAmount().subtract(
                invoice.getAmountPaid() == null ? BigDecimal.ZERO : invoice.getAmountPaid());

        Context ctx = new Context();
        ctx.setVariable("invoice", invoice);
        ctx.setVariable("items", items);
        ctx.setVariable("balance", balance);

        String html = templateEngine.process("invoices/pdf", ctx);
        return htmlToPdf(html);
    }

    public byte[] renderStatement(Client client, List<Invoice> invoices, AgingBuckets aging, String currency) {
        Context ctx = new Context();
        ctx.setVariable("client", client);
        ctx.setVariable("invoices", invoices);
        ctx.setVariable("aging", aging);
        ctx.setVariable("currency", currency);
        ctx.setVariable("today", java.time.LocalDate.now());

        String html = templateEngine.process("clients/statement-pdf", ctx);
        return htmlToPdf(html);
    }

    private byte[] htmlToPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useSVGDrawer(new BatikSVGDrawer());
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to render PDF", e);
        }
    }
}
