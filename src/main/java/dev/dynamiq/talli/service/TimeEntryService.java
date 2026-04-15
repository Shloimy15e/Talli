package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;

    public TimeEntryService(TimeEntryRepository timeEntryRepository, ProjectRepository projectRepository) {
        this.timeEntryRepository = timeEntryRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TimeEntry startTimer(Long projectId, String description) {
        return create(projectId, LocalDateTime.now(), null, description, true);
    }

    @Transactional
    public TimeEntry create(Long projectId, LocalDateTime startedAt, LocalDateTime endedAt,
                            String description, Boolean billable) {
        TimeEntry entry = new TimeEntry();
        applyFields(entry, projectId, startedAt, endedAt, description, billable);
        return timeEntryRepository.save(entry);
    }

    @Transactional
    public TimeEntry update(Long id, Long projectId, LocalDateTime startedAt, LocalDateTime endedAt,
                            String description, Boolean billable) {
        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();
        applyFields(entry, projectId, startedAt, endedAt, description, billable);
        return timeEntryRepository.save(entry);
    }

    @Transactional
    public TimeEntry endTimer(Long id) {
        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();

        if (entry.getEndedAt() != null) {
            throw new IllegalStateException("Timer is not running");
        }

        entry.setEndedAt(LocalDateTime.now());
        return entry;
    }

    @Transactional
    public void delete(Long id) {
        timeEntryRepository.deleteById(id);
    }

    private void applyFields(TimeEntry entry, Long projectId, LocalDateTime startedAt,
                             LocalDateTime endedAt, String description, Boolean billable) {
        entry.setProject(projectRepository.findById(projectId).orElseThrow());
        entry.setStartedAt(startedAt);
        entry.setEndedAt(endedAt);
        entry.setDescription(description);
        entry.setBillable(billable);
    }
}
