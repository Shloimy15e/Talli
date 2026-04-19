package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.CreateProjectRequest;
import dev.dynamiq.talli.controller.api.dto.ProjectResponse;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ApiProjectController {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;

    public ApiProjectController(ProjectRepository projectRepository, ClientRepository clientRepository) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
    }

    /** Active projects ordered by most recent time entry. */
    @GetMapping
    public List<ProjectResponse> list() {
        return projectRepository.findActiveOrderedByRecentActivity().stream()
                .map(this::toResponse)
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
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(p));
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getClient() != null ? p.getClient().getId() : null,
                p.getClient() != null ? p.getClient().getName() : null,
                p.getStatus()
        );
    }
}
