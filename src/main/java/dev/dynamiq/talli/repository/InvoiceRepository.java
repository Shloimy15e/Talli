package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByIssuedAtDescIdDesc();

    Page<Invoice> findAllByOrderByIssuedAtDescIdDesc(Pageable pageable);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.client c WHERE "
         + "(:#{#statuses.isEmpty()} = true OR i.status IN :statuses) AND "
         + "(:search = '' OR LOWER(i.reference) LIKE LOWER(CONCAT('%', :search, '%')) "
         + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) "
         + "ORDER BY i.issuedAt DESC, i.id DESC")
    Page<Invoice> findFiltered(@Param("statuses") List<String> statuses,
                               @Param("search") String search,
                               Pageable pageable);

    List<Invoice> findByStatusOrderByIssuedAtDesc(String status);

    List<Invoice> findByClientIdOrderByIssuedAtDescIdDesc(Long clientId);

    Optional<Invoice> findTopByReferenceStartingWithOrderByReferenceDesc(String prefix);

    List<Invoice> findByStatusAndDueAtBefore(String status, LocalDate dueAtBefore);

    boolean existsByClientIdAndPeriodStartAndPeriodEnd(Long clientId,
                                                       LocalDate periodStart,
                                                       LocalDate periodEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForMercurySync(@Param("id") Long id);

    @Query("select i.id from Invoice i where i.status in ('unpaid', 'overdue') and "
         + "((i.mercuryInvoiceId is not null and (i.mercuryStatus is null or i.mercuryStatus in ('Unpaid', 'Processing'))) "
         + "or (i.mercuryInvoiceId is null and i.mercurySyncError is not null)) order by i.id")
    List<Long> findMercurySyncCandidateIds();
}
