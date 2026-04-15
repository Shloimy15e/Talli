package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    List<InvoiceItem> findByProjectIdOrderByIdDesc(Long projectId);

    @Query("select coalesce(sum(i.total), 0) from InvoiceItem i where i.project.id = :projectId")
    BigDecimal sumTotalByProjectId(@Param("projectId") Long projectId);

    @Query("""
           select coalesce(sum(i.total), 0) from InvoiceItem i
           where i.project.id = :projectId
             and i.invoice.issuedAt >= :from and i.invoice.issuedAt < :to
           """)
    BigDecimal sumTotalByProjectIdBetween(@Param("projectId") Long projectId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query("""
           select inv from Invoice inv
           where exists (select 1 from InvoiceItem i where i.invoice = inv and i.project.id = :projectId)
           order by inv.issuedAt desc, inv.id desc
           """)
    List<dev.dynamiq.talli.model.Invoice> findInvoicesByProjectId(@Param("projectId") Long projectId);
}
