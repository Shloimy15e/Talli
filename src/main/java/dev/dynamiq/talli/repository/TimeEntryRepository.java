package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.TimeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    List<TimeEntry> findByProjectIdOrderByStartedAtDesc(Long projectId);
    List<TimeEntry> findAllByOrderByStartedAtDesc();

    Page<TimeEntry> findAllByOrderByStartedAtDesc(Pageable pageable);

    @Query("SELECT t FROM TimeEntry t JOIN FETCH t.project p JOIN FETCH p.client c WHERE "
         + "(:#{#projectIds == null || #projectIds.isEmpty()} = true OR p.id IN :projectIds) AND "
         + "(:#{#clientIds == null || #clientIds.isEmpty()} = true OR c.id IN :clientIds) AND "
         + "(:#{#statuses == null || #statuses.isEmpty()} = true "
         + "OR ('billed' IN :statuses AND t.billed = true) "
         + "OR ('unbilled' IN :statuses AND t.billed = false AND t.billable = true) "
         + "OR ('nonbillable' IN :statuses AND t.billable = false)) "
         + "ORDER BY t.startedAt DESC")
    Page<TimeEntry> findFiltered(@Param("projectIds") List<Long> projectIds,
                                 @Param("clientIds") List<Long> clientIds,
                                 @Param("statuses") List<String> statuses,
                                 Pageable pageable);
    Optional<TimeEntry> findFirstByEndedAtIsNullOrderByStartedAtDesc(); // the currently running entry, if any

    /**
     * Latest non-empty description per project. Used by the API to prefill the
     * timer description when picking from recent projects in the extension.
     * Uses Postgres DISTINCT ON — the app is Postgres-only (see application.properties).
     */
    @Query(value = "SELECT DISTINCT ON (project_id) project_id, description "
                 + "FROM time_entries "
                 + "WHERE description IS NOT NULL AND description <> '' "
                 + "ORDER BY project_id, started_at DESC",
           nativeQuery = true)
    List<Object[]> findLatestDescriptionPerProject();

    List<TimeEntry> findByInvoiceId(Long invoiceId);

    // Billable, unbilled, ended, within a window — the candidates for an invoice.
    List<TimeEntry> findByProjectIdAndBillableTrueAndBilledFalseAndEndedAtIsNotNullAndStartedAtBetweenOrderByStartedAtAsc(
            Long projectId, LocalDateTime from, LocalDateTime to);
}
