package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    List<TimeEntry> findByProjectIdOrderByStartedAtDesc(Long projectId);
    List<TimeEntry> findAllByOrderByStartedAtDesc();
    Optional<TimeEntry> findFirstByEndedAtIsNullOrderByStartedAtDesc(); // the currently running entry, if any

    // Billable, unbilled, ended, within a window — the candidates for an invoice.
    List<TimeEntry> findByProjectIdAndBillableTrueAndBilledFalseAndEndedAtIsNotNullAndStartedAtBetweenOrderByStartedAtAsc(
            Long projectId, LocalDateTime from, LocalDateTime to);
}
