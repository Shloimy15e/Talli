package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.ProjectResponse;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiProjectControllerTest {

    private ProjectRepository projectRepository;
    private ApiProjectController controller;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        controller = new ApiProjectController(projectRepository);
    }

    @Test
    void list_returnsOnlyActiveProjects() {
        Client client = new Client();
        client.setId(1L);
        client.setName("Acme");

        Project active = new Project();
        active.setId(10L);
        active.setName("Website");
        active.setClient(client);
        active.setStatus("active");

        Project completed = new Project();
        completed.setId(11L);
        completed.setName("Old Project");
        completed.setClient(client);
        completed.setStatus("completed");

        when(projectRepository.findAll()).thenReturn(List.of(active, completed));

        List<ProjectResponse> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).name()).isEqualTo("Website");
        assertThat(result.get(0).clientName()).isEqualTo("Acme");
        assertThat(result.get(0).status()).isEqualTo("active");
    }

    @Test
    void list_returnsEmptyWhenNoActiveProjects() {
        when(projectRepository.findAll()).thenReturn(List.of());

        List<ProjectResponse> result = controller.list();

        assertThat(result).isEmpty();
    }

    @Test
    void list_handlesProjectWithNullClient() {
        Project noClient = new Project();
        noClient.setId(12L);
        noClient.setName("Internal");
        noClient.setStatus("active");
        // client is null

        when(projectRepository.findAll()).thenReturn(List.of(noClient));

        List<ProjectResponse> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientId()).isNull();
        assertThat(result.get(0).clientName()).isNull();
    }
}
