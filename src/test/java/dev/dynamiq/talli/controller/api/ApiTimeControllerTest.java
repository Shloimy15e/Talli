package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.StartTimerRequest;
import dev.dynamiq.talli.controller.api.dto.TimeEntryResponse;
import dev.dynamiq.talli.controller.api.dto.TimerStatusResponse;
import dev.dynamiq.talli.controller.api.dto.UpdateDescriptionRequest;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.TimeEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApiTimeControllerTest {

    private TimeEntryService timeEntryService;
    private TimeEntryRepository timeEntryRepository;
    private ApiTimeController controller;

    private Project project;

    @BeforeEach
    void setUp() {
        timeEntryService = mock(TimeEntryService.class);
        timeEntryRepository = mock(TimeEntryRepository.class);
        controller = new ApiTimeController(timeEntryService, timeEntryRepository);

        Client client = new Client();
        client.setId(1L);
        client.setName("Acme Corp");

        project = new Project();
        project.setId(10L);
        project.setName("Website Redesign");
        project.setClient(client);
    }

    @Test
    void currentTimer_returnsTimerWhenRunning() {
        TimeEntry running = makeEntry(1L, project, LocalDateTime.of(2026, 4, 16, 9, 0), null);
        when(timeEntryRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc())
                .thenReturn(Optional.of(running));

        ResponseEntity<TimerStatusResponse> response = controller.currentTimer();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TimerStatusResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(1L);
        assertThat(body.projectId()).isEqualTo(10L);
        assertThat(body.projectName()).isEqualTo("Website Redesign");
        assertThat(body.running()).isTrue();
    }

    @Test
    void currentTimer_returns204WhenNoRunningTimer() {
        when(timeEntryRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc())
                .thenReturn(Optional.empty());

        ResponseEntity<TimerStatusResponse> response = controller.currentTimer();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void startTimer_returns201WithNewEntry() {
        TimeEntry created = makeEntry(5L, project, LocalDateTime.now(), null);
        created.setDescription("coding");
        created.setBillable(true);
        when(timeEntryService.startTimer(10L, "coding")).thenReturn(created);

        ResponseEntity<TimeEntryResponse> response =
                controller.startTimer(new StartTimerRequest(10L, "coding"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TimeEntryResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(5L);
        assertThat(body.projectName()).isEqualTo("Website Redesign");
        assertThat(body.description()).isEqualTo("coding");
        assertThat(body.endedAt()).isNull();
    }

    @Test
    void stopTimer_returnsStoppedEntry() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 16, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 16, 10, 30);
        TimeEntry stopped = makeEntry(5L, project, start, end);
        stopped.setDurationMinutes(90);
        when(timeEntryService.endTimer(5L)).thenReturn(stopped);

        ResponseEntity<TimeEntryResponse> response = controller.stopTimer(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TimeEntryResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.endedAt()).isEqualTo(end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        assertThat(body.durationMinutes()).isEqualTo(90);
    }

    @Test
    void updateDescription_returnsEntryWithUpdatedDescription() {
        TimeEntry updated = makeEntry(5L, project, LocalDateTime.of(2026, 4, 16, 9, 0), null);
        updated.setDescription("new text");
        when(timeEntryService.updateDescription(5L, "new text")).thenReturn(updated);

        ResponseEntity<TimeEntryResponse> response =
                controller.updateDescription(5L, new UpdateDescriptionRequest("new text"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TimeEntryResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(5L);
        assertThat(body.description()).isEqualTo("new text");
        verify(timeEntryService).updateDescription(5L, "new text");
    }

    @Test
    void updateDescription_acceptsNullToClear() {
        TimeEntry updated = makeEntry(5L, project, LocalDateTime.of(2026, 4, 16, 9, 0), null);
        updated.setDescription(null);
        when(timeEntryService.updateDescription(5L, null)).thenReturn(updated);

        ResponseEntity<TimeEntryResponse> response =
                controller.updateDescription(5L, new UpdateDescriptionRequest(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().description()).isNull();
    }

    @Test
    void stopTimer_propagatesExceptionWhenAlreadyStopped() {
        when(timeEntryService.endTimer(5L))
                .thenThrow(new IllegalStateException("Timer is not running"));

        assertThatThrownBy(() -> controller.stopTimer(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Timer is not running");
    }

    private TimeEntry makeEntry(Long id, Project proj, LocalDateTime start, LocalDateTime end) {
        TimeEntry e = new TimeEntry();
        e.setId(id);
        e.setProject(proj);
        e.setStartedAt(start);
        e.setEndedAt(end);
        e.setBillable(true);
        return e;
    }
}
