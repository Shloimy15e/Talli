package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {
    List<Email> findAllByOrderByCreatedAtDesc();

    List<Email> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);
}
