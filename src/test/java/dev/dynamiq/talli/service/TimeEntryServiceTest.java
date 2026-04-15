package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TimeEntryServiceTest {

    private TimeEntryRepository timeEntryRepository;
    private ProjectRepository projectRepository;
    private TimeEntryService service;

    private Project project;

    @BeforeEach
    void setUp() {
        timeEntryRepository = mock(TimeEntryRepository.class);
        projectRepository = mock(ProjectRepository.class);
        when(timeEntryRepository.save(any(TimeEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        project = new Project();
        project.setId(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        service = new TimeEntryService(timeEntryRepository, projectRepository);
    }

    @Test
    void create_persistsEntryWithAllFields() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 14, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 14, 10, 30);

        TimeEntry created = service.create(1L, start, end, "design work", true);

        assertThat(created.getProject()).isSameAs(project);
        assertThat(created.getStartedAt()).isEqualTo(start);
        assertThat(created.getEndedAt()).isEqualTo(end);
        assertThat(created.getDescription()).isEqualTo("design work");
        assertThat(created.getBillable()).isTrue();
        verify(timeEntryRepository).save(created);
    }

    @Test
    void startTimer_createsRunningBillableEntry() {
        LocalDateTime before = LocalDateTime.now();

        TimeEntry started = service.startTimer(1L, "quick task");

        assertThat(started.getProject()).isSameAs(project);
        assertThat(started.getStartedAt()).isBetween(before, LocalDateTime.now());
        assertThat(started.getEndedAt()).isNull();
        assertThat(started.getBillable()).isTrue();
        assertThat(started.getDescription()).isEqualTo("quick task");
        verify(timeEntryRepository).save(started);
    }

    @Test
    void update_loadsExistingEntryAndReplacesFields() {
        TimeEntry existing = new TimeEntry();
        existing.setId(7L);
        existing.setDescription("old");
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(existing));

        LocalDateTime start = LocalDateTime.of(2026, 4, 14, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 14, 11, 0);

        TimeEntry updated = service.update(7L, 1L, start, end, "new desc", false);

        assertThat(updated).isSameAs(existing);
        assertThat(updated.getStartedAt()).isEqualTo(start);
        assertThat(updated.getEndedAt()).isEqualTo(end);
        assertThat(updated.getDescription()).isEqualTo("new desc");
        assertThat(updated.getBillable()).isFalse();
        verify(timeEntryRepository).save(existing);
    }

    @Test
    void endTimer_setsEndedAtOnRunningEntry() {
        TimeEntry running = new TimeEntry();
        running.setId(3L);
        running.setStartedAt(LocalDateTime.of(2026, 4, 14, 9, 0));
        running.setEndedAt(null);
        when(timeEntryRepository.findById(3L)).thenReturn(Optional.of(running));

        LocalDateTime before = LocalDateTime.now();
        TimeEntry ended = service.endTimer(3L);

        assertThat(ended.getEndedAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void endTimer_throwsWhenAlreadyStopped() {
        TimeEntry stopped = new TimeEntry();
        stopped.setId(4L);
        stopped.setStartedAt(LocalDateTime.of(2026, 4, 14, 9, 0));
        stopped.setEndedAt(LocalDateTime.of(2026, 4, 14, 10, 0));
        when(timeEntryRepository.findById(4L)).thenReturn(Optional.of(stopped));

        assertThatThrownBy(() -> service.endTimer(4L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Timer is not running");
    }

    @Test
    void delete_callsRepository() {
        service.delete(42L);

        verify(timeEntryRepository).deleteById(42L);
    }

    @Test
    void create_throwsWhenProjectMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, LocalDateTime.now(), null, "x", true))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(timeEntryRepository, never()).save(any());
    }

    @Test
    void update_throwsWhenEntryMissing() {
        when(timeEntryRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(123L, 1L, LocalDateTime.now(), null, "x", true))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(timeEntryRepository, never()).save(any(TimeEntry.class));
    }
}
