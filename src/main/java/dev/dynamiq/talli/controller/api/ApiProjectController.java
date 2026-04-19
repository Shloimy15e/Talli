package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.CreateProjectRequest;
import dev.dynamiq.talli.controller.api.dto.ProjectResponse;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class ApiProjectController {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final TimeEntryRepository timeEntryRepository;

    public ApiProjectController(ProjectRepository projectRepository,
                                ClientRepository clientRepository,
                                TimeEntryRepository timeEntryRepository) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.timeEntryRepository = timeEntryRepository;
    }

    /** Active projects ordered by most recent time entry. */
    @GetMapping
    public List<ProjectResponse> list() {
        Map<Long, String> lastDescByProject = new HashMap<>();
        for (Object[] row : timeEntryRepository.findLatestDescriptionPerProject()) {
            Long projectId = ((Number) row[0]).longValue();
            String description = (String) row[1];
            lastDescByProject.put(projectId, description);
        }
        return projectRepository.findActiveOrderedByRecentActivity().stream()
                .map(p -> toResponse(p, lastDescByProject.get(p.getId())))
                .toList();
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest req) {
        Project p = new Project();
        p.setName(req.name());
        p.setClient(clientRepository.findById(req.clientId()).orElseThrow());
        p.setRateType(req.rateType() != null ? req.rateType() : "hourly");
        p.setCurrentRate(req.currentRate());
        p.setCurrency(req.currency() != null ? req.currency() : "USD");
        p.setBillingFrequency(req.billingFrequency());
        p.setBillable(req.billable() != null ? req.billable() : true);
        p.setStatus("active");
        projectRepository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(p, null));
    }

    private ProjectResponse toResponse(Project p, String lastDescription) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getClient() != null ? p.getClient().getId() : null,
                p.getClient() != null ? p.getClient().getName() : null,
                p.getStatus(),
                lastDescription
        );
    }
}
