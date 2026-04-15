package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Invoice;
import dev.dynamiq.talli.model.InvoiceItem;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceItemRepository invoiceItemRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
    }

    public List<Invoice> listAll() {
        return invoiceRepository.findAllByOrderByIssuedAtDescIdDesc();
    }

    public Invoice get(Long id) {
        return invoiceRepository.findById(id).orElseThrow();
    }

    public List<InvoiceItem> itemsFor(Long invoiceId) {
        return invoiceItemRepository.findByInvoiceIdOrderByIdAsc(invoiceId);
    }

    /**
     * Persist an invoice along with its line items, computing and storing the total.
     * Items must already have their fields populated (description, unitPrice, unitCount, total)
     * — only the FK back to the invoice is wired here.
     */
    @Transactional
    public Invoice create(Invoice invoice, List<InvoiceItem> items) {
        invoice = invoiceRepository.save(invoice);

        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceItem item : items) {
            item.setInvoice(invoice);
            invoiceItemRepository.save(item);
            total = total.add(item.getTotal());
        }

        invoice.setAmount(total);
        return invoice;
    }

    @Transactional
    public void delete(Long id) {
        invoiceRepository.deleteById(id);
    }

    /**
     * Reference generator. Naive sequence by year — fine for low volume.
     * When generation lands, this should consult MAX(reference) per year, not row count.
     */
    public String nextReference() {
        long next = invoiceRepository.count() + 1;
        return String.format("INV-%d-%04d", LocalDate.now().getYear(), next);
    }

    public BigDecimal balance(Invoice invoice) {
        return invoice.getAmount().subtract(invoice.getAmountPaid());
    }
}
