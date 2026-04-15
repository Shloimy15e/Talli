package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceIdOrderByIdAsc(Long invoiceId);
}
