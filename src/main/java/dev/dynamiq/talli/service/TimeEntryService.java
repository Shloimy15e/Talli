package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // --- Index view ---

    /** Single call assembling everything the time index page needs. */
    public IndexView indexView() {
        List<TimeEntry> entries = timeEntryRepository.findAllByOrderByStartedAtDesc();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        int todayMinutes = 0, weekMinutes = 0, monthMinutes = 0, unbilledMinutes = 0;
        BigDecimal unbilledValue = BigDecimal.ZERO;
        Map<Long, BigDecimal> entryValues = new HashMap<>();

        for (TimeEntry e : entries) {
            if (e.getStartedAt() == null) continue;
            int m = minutesFor(e, now);
            LocalDate day = e.getStartedAt().toLocalDate();
            if (!day.isBefore(today)) todayMinutes += m;
            if (!day.isBefore(weekStart)) weekMinutes += m;
            if (!day.isBefore(monthStart)) monthMinutes += m;
            if (Boolean.TRUE.equals(e.getBillable())) {
                BigDecimal value = valueOf(m, e.getProject().getCurrentRate());
                entryValues.put(e.getId(), value);
                if (Boolean.FALSE.equals(e.getBilled())) {
                    unbilledMinutes += m;
                    unbilledValue = unbilledValue.add(value);
                }
            }
        }

        Map<LocalDate, List<TimeEntry>> byDay = entries.stream()
                .filter(e -> e.getStartedAt() != null && e.getEndedAt() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getStartedAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DayGroup> days = byDay.entrySet().stream()
                .map(en -> {
                    int dayMinutes = en.getValue().stream().mapToInt(e -> minutesFor(e, now)).sum();
                    BigDecimal dayValue = en.getValue().stream()
                            .filter(e -> Boolean.TRUE.equals(e.getBillable()))
                            .map(e -> valueOf(minutesFor(e, now), e.getProject().getCurrentRate()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DayGroup(en.getKey(), en.getValue(), dayMinutes, dayValue);
                })
                .toList();

        return new IndexView(entries, days, entryValues,
                todayMinutes, weekMinutes, monthMinutes, unbilledMinutes, unbilledValue);
    }

    /** Unbilled dollar value + entry count for a single project's time entries. */
    public ProjectTimeTotals totalsForProject(Long projectId, BigDecimal rate) {
        List<TimeEntry> entries = timeEntryRepository.findByProjectIdOrderByStartedAtDesc(projectId);
        LocalDateTime now = LocalDateTime.now();
        int unbilledMinutes = entries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getBillable()))
                .filter(e -> !Boolean.TRUE.equals(e.getBilled()))
                .filter(e -> e.getEndedAt() != null)
                .mapToInt(e -> minutesFor(e, now))
                .sum();
        return new ProjectTimeTotals(valueOf(unbilledMinutes, rate), entries.size());
    }

    public record ProjectTimeTotals(BigDecimal unbilledValue, long entryCount) {}

    /** Elapsed minutes for a time entry at a given instant. Handles running entries. */
    public static int minutesFor(TimeEntry e, LocalDateTime now) {
        if (e.getDurationMinutes() != null) return e.getDurationMinutes();
        if (e.getEndedAt() != null) return (int) ChronoUnit.MINUTES.between(e.getStartedAt(), e.getEndedAt());
        return (int) ChronoUnit.MINUTES.between(e.getStartedAt(), now);
    }

    /** Dollar value of a duration at an hourly rate. Returns 0 if rate is null. */
    public static BigDecimal valueOf(int minutes, BigDecimal rate) {
        if (rate == null) return BigDecimal.ZERO;
        return rate.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public record DayGroup(LocalDate day, List<TimeEntry> entries, int minutes, BigDecimal value) {}

    public record IndexView(
            List<TimeEntry> entries,
            List<DayGroup> days,
            Map<Long, BigDecimal> entryValues,
            int todayMinutes,
            int weekMinutes,
            int monthMinutes,
            int unbilledMinutes,
            BigDecimal unbilledValue) {}
}
