package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.StartTimerRequest;
import dev.dynamiq.talli.controller.api.dto.TimeEntryResponse;
import dev.dynamiq.talli.controller.api.dto.TimerStatusResponse;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.TimeEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private TimerStatusResponse toTimerStatus(TimeEntry e) {
        return new TimerStatusResponse(
                e.getId(),
                e.getProject().getId(),
                e.getProject().getName(),
                e.getDescription(),
                e.getStartedAt(),
                e.isRunning()
        );
    }

    private TimeEntryResponse toResponse(TimeEntry e) {
        return new TimeEntryResponse(
                e.getId(),
                e.getProject().getId(),
                e.getProject().getName(),
                e.getStartedAt(),
                e.getEndedAt(),
                e.getDurationMinutes(),
                e.getDescription(),
                e.getBillable()
        );
    }
}
