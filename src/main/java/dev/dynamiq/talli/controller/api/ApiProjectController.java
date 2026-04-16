package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.ProjectResponse;
import dev.dynamiq.talli.repository.ProjectRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ApiProjectController {

    private final ProjectRepository projectRepository;

    public ApiProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /** Active projects ordered by most recent time entry. */
    @GetMapping
    public List<ProjectResponse> list() {
        return projectRepository.findActiveOrderedByRecentActivity().stream()
                .map(p -> new ProjectResponse(
                        p.getId(),
                        p.getName(),
                        p.getClient() != null ? p.getClient().getId() : null,
                        p.getClient() != null ? p.getClient().getName() : null,
                        p.getStatus()
                ))
                .toList();
    }
}
