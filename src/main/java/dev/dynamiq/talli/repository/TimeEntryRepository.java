package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    List<TimeEntry> findByProjectIdOrderByStartedAtDesc(Long projectId);
    List<TimeEntry> findAllByOrderByStartedAtDesc();
    Optional<TimeEntry> findFirstByEndedAtIsNullOrderByStartedAtDesc(); // the currently running entry, if any
}
