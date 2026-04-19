package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {
    List<Email> findAllByOrderByCreatedAtDesc();

    List<Email> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    java.util.Optional<Email> findByResendId(String resendId);

    @Query("SELECT e FROM Email e LEFT JOIN e.client c WHERE "
         + "(:#{#statuses.isEmpty()} = true OR e.status IN :statuses) AND "
         + "(:search = '' "
         + "  OR LOWER(e.subject)   LIKE LOWER(CONCAT('%', :search, '%')) "
         + "  OR LOWER(e.toAddress) LIKE LOWER(CONCAT('%', :search, '%')) "
         + "  OR LOWER(e.body)      LIKE LOWER(CONCAT('%', :search, '%')) "
         + "  OR LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))) "
         + "ORDER BY "
         + "CASE WHEN :sort = 'created' AND :direction = 'desc' THEN e.createdAt END DESC, "
         + "CASE WHEN :sort = 'created' AND :direction = 'asc'  THEN e.createdAt END ASC, "
         + "CASE WHEN :sort = 'sent'    AND :direction = 'desc' THEN e.sentAt    END DESC NULLS LAST, "
         + "CASE WHEN :sort = 'sent'    AND :direction = 'asc'  THEN e.sentAt    END ASC  NULLS LAST, "
         + "CASE WHEN :sort = 'subject' AND :direction = 'asc'  THEN e.subject   END ASC, "
         + "CASE WHEN :sort = 'subject' AND :direction = 'desc' THEN e.subject   END DESC, "
         + "CASE WHEN :sort = 'status'  AND :direction = 'asc'  THEN e.status    END ASC, "
         + "CASE WHEN :sort = 'status'  AND :direction = 'desc' THEN e.status    END DESC, "
         + "e.id DESC")
    Page<Email> findFiltered(@Param("statuses") List<String> statuses,
                             @Param("search") String search,
                             @Param("sort") String sort,
                             @Param("direction") String direction,
                             Pageable pageable);
}
