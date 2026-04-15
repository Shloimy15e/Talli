package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByIssuedAtDescIdDesc();

    List<Invoice> findByStatusOrderByIssuedAtDesc(String status);

    List<Invoice> findByClientIdOrderByIssuedAtDescIdDesc(Long clientId);

    Optional<Invoice> findTopByReferenceStartingWithOrderByReferenceDesc(String prefix);
}
