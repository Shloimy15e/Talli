package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.StartTimerRequest;
import dev.dynamiq.talli.controller.api.dto.TimeEntryResponse;
import dev.dynamiq.talli.controller.api.dto.TimerStatusResponse;
import dev.dynamiq.talli.controller.api.dto.UpdateDescriptionRequest;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.TimeEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/time")
public class ApiTimeController {

    private final TimeEntryService timeEntryService;
    private final TimeEntryRepository timeEntryRepository;

    public ApiTimeController(TimeEntryService timeEntryService,
                             TimeEntryRepository timeEntryRepository) {
        this.timeEntryService = timeEntryService;
        this.timeEntryRepository = timeEntryRepository;
    }

    @GetMapping("/current")
    public ResponseEntity<TimerStatusResponse> currentTimer() {
        return timeEntryRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc()
                .map(e -> ResponseEntity.ok(toTimerStatus(e)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/start")
    public ResponseEntity<TimeEntryResponse> startTimer(@Valid @RequestBody StartTimerRequest req) {
        TimeEntry entry = timeEntryService.startTimer(req.projectId(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entry));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<TimeEntryResponse> stopTimer(@PathVariable Long id) {
        TimeEntry entry = timeEntryService.endTimer(id);
        return ResponseEntity.ok(toResponse(entry));
    }

    @PostMapping("/{id}/description")
    public ResponseEntity<TimeEntryResponse> updateDescription(@PathVariable Long id,
                                                               @RequestBody UpdateDescriptionRequest req) {
        TimeEntry entry = timeEntryService.updateDescription(id, req.description());
        return ResponseEntity.ok(toResponse(entry));
    }

    /**
     * Convert a server-local LocalDateTime (wall-clock, no zone) to Unix epoch millis
     * using the JVM's default timezone. Returning long epoch millis is unambiguous —
     * JS does `new Date(millis)` regardless of either side's timezone.
     */
    private static long toEpochMillis(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static Long toEpochMillisNullable(LocalDateTime ldt) {
        return ldt == null ? null : toEpochMillis(ldt);
    }

    private TimerStatusResponse toTimerStatus(TimeEntry e) {
        long elapsedSeconds = Duration.between(e.getStartedAt(), LocalDateTime.now()).getSeconds();
        return new TimerStatusResponse(
                e.getId(),
                e.getProject().getId(),
                e.getProject().getName(),
                e.getDescription(),
                toEpochMillis(e.getStartedAt()),
                Math.max(elapsedSeconds, 0),
                e.isRunning()
        );
    }

    private TimeEntryResponse toResponse(TimeEntry e) {
        return new TimeEntryResponse(
                e.getId(),
                e.getProject().getId(),
                e.getProject().getName(),
                toEpochMillis(e.getStartedAt()),
                toEpochMillisNullable(e.getEndedAt()),
                e.getDurationMinutes(),
                e.getDescription(),
                e.getBillable()
        );
    }
}
