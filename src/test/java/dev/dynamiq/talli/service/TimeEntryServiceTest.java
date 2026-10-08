package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        project.setRateType("hourly");
        project.setCurrentRate(new BigDecimal("120.00"));
        project.setCurrency("USD");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
        when(exchangeRateService.toUsdCurrent(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        service = new TimeEntryService(timeEntryRepository, projectRepository, exchangeRateService);
    }

    @Test
    void create_persistsEntryWithAllFields() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 14, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 14, 10, 30);

        TimeEntry created = service.create(1L, start, end, "design work", true);

        assertThat(created.getProject()).isSameAs(project);
        assertThat(created.getRate()).isEqualByComparingTo("120.00");
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
        assertThat(started.getRate()).isEqualByComparingTo("120.00");
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
    void update_clearsCachedDurationWhenRestartingTimer() {
        TimeEntry existing = new TimeEntry();
        existing.setId(7L);
        existing.setDurationMinutes(60);
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(existing));

        TimeEntry updated = service.update(7L, 1L,
                LocalDateTime.of(2026, 4, 14, 9, 0), null, "resumed", true);

        assertThat(updated.getEndedAt()).isNull();
        assertThat(updated.getDurationMinutes()).isNull();
    }

    @Test
    void update_preservesSnapshotWhenProjectRateChanges() {
        TimeEntry existing = entry(7L, project, LocalDateTime.now().minusHours(1), LocalDateTime.now(), 60);
        project.setCurrentRate(new BigDecimal("180.00"));
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(existing));

        TimeEntry updated = service.update(7L, 1L, existing.getStartedAt(), existing.getEndedAt(), "edited", false);
        service.updateDescription(7L, "description edit");

        assertThat(updated.getRate()).isEqualByComparingTo("120.00");
    }

    @Test
    void update_capturesNewProjectsCurrentRateOnReassignment() {
        TimeEntry existing = entry(7L, project, LocalDateTime.now().minusHours(1), LocalDateTime.now(), 60);
        Project other = new Project();
        other.setId(2L);
        other.setCurrentRate(new BigDecimal("175.00"));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(other));
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(existing));

        TimeEntry updated = service.update(7L, 2L, existing.getStartedAt(), existing.getEndedAt(), "moved", true);

        assertThat(updated.getProject()).isSameAs(other);
        assertThat(updated.getRate()).isEqualByComparingTo("175.00");
    }

    @Test
    void endTimer_preservesRateCapturedWhenStarted() {
        TimeEntry running = service.startTimer(1L, "work");
        running.setId(7L);
        when(timeEntryRepository.findById(7L)).thenReturn(Optional.of(running));
        project.setCurrentRate(new BigDecimal("180.00"));

        assertThat(service.endTimer(7L).getRate()).isEqualByComparingTo("120.00");
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

    @Test
    void indexView_countsOnlyCompletedHourlyEntriesAsUnbilled() {
        LocalDateTime now = LocalDateTime.now();
        TimeEntry completed = entry(1L, project, now.minusHours(2), now.minusHours(1), 60);
        TimeEntry running = entry(2L, project, now.minusMinutes(20), null, null);

        Project fixed = new Project();
        fixed.setRateType("fixed");
        fixed.setCurrentRate(new BigDecimal("5000.00"));
        fixed.setCurrency("USD");
        TimeEntry fixedTime = entry(3L, fixed, now.minusHours(3), now.minusHours(2), 60);
        when(timeEntryRepository.findAllByOrderByStartedAtDesc())
                .thenReturn(List.of(completed, running, fixedTime));

        TimeEntryService.IndexView view = service.indexView();

        assertThat(view.unbilledMinutes()).isEqualTo(60);
        assertThat(view.unbilledValueUsd()).isEqualByComparingTo("120.00");
    }

    @Test
    void valuations_useEachSnapshotAfterProjectRateChanges() {
        LocalDateTime now = java.time.LocalDate.now().atTime(12, 0);
        TimeEntry first = entry(1L, project, now.minusHours(3), now.minusHours(2), 60);
        TimeEntry second = entry(2L, project, now.minusHours(2), now.minusHours(1), 60);
        second.setRate(new BigDecimal("150.00"));
        TimeEntry billed = entry(3L, project, now.minusHours(4), now.minusHours(3), 60);
        billed.setBilled(true);
        project.setCurrentRate(new BigDecimal("200.00"));
        when(timeEntryRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(first, second, billed));
        when(timeEntryRepository.findByProjectIdOrderByStartedAtDesc(1L)).thenReturn(List.of(first, second, billed));

        TimeEntryService.IndexView view = service.indexView();
        TimeEntryService.ProjectTimeTotals totals = service.totalsForProject(1L);

        assertThat(view.entryValues().get(1L)).isEqualByComparingTo("120.00");
        assertThat(view.entryValues().get(2L)).isEqualByComparingTo("150.00");
        assertThat(view.days()).hasSize(1);
        assertThat(view.days().getFirst().valueUsd()).isEqualByComparingTo("390.00");
        assertThat(view.unbilledValueUsd()).isEqualByComparingTo("270.00");
        assertThat(totals.unbilledValue()).isEqualByComparingTo("270.00");
        assertThat(totals.entryCount()).isEqualTo(3);
    }

    private TimeEntry entry(Long id, Project entryProject, LocalDateTime startedAt,
                            LocalDateTime endedAt, Integer durationMinutes) {
        TimeEntry entry = new TimeEntry();
        entry.setId(id);
        entry.setProject(entryProject);
        entry.setRate(entryProject.getCurrentRate());
        entry.setStartedAt(startedAt);
        entry.setEndedAt(endedAt);
        entry.setDurationMinutes(durationMinutes);
        entry.setBillable(true);
        entry.setBilled(false);
        return entry;
    }
}
