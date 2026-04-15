package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByPaidAtDescIdDesc(Long invoiceId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoice.id = :invoiceId")
    BigDecimal sumAmountByInvoiceId(@Param("invoiceId") Long invoiceId);
}
