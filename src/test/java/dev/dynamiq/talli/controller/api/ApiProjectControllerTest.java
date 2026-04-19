package dev.dynamiq.talli.controller.api;

import dev.dynamiq.talli.controller.api.dto.ProjectResponse;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiProjectControllerTest {

    private ProjectRepository projectRepository;
    private ClientRepository clientRepository;
    private TimeEntryRepository timeEntryRepository;
    private ApiProjectController controller;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        clientRepository = mock(ClientRepository.class);
        timeEntryRepository = mock(TimeEntryRepository.class);
        when(timeEntryRepository.findLatestDescriptionPerProject()).thenReturn(List.of());
        controller = new ApiProjectController(projectRepository, clientRepository, timeEntryRepository);
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

        when(projectRepository.findActiveOrderedByRecentActivity()).thenReturn(List.of(active));

        List<ProjectResponse> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).name()).isEqualTo("Website");
        assertThat(result.get(0).clientName()).isEqualTo("Acme");
        assertThat(result.get(0).status()).isEqualTo("active");
    }

    @Test
    void list_returnsEmptyWhenNoActiveProjects() {
        when(projectRepository.findActiveOrderedByRecentActivity()).thenReturn(List.of());

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

        when(projectRepository.findActiveOrderedByRecentActivity()).thenReturn(List.of(noClient));

        List<ProjectResponse> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientId()).isNull();
        assertThat(result.get(0).clientName()).isNull();
    }

    @Test
    void list_attachesLastDescriptionPerProject() {
        Client client = new Client();
        client.setId(1L);
        client.setName("Acme");

        Project a = new Project();
        a.setId(10L);
        a.setName("Website");
        a.setClient(client);
        a.setStatus("active");

        Project b = new Project();
        b.setId(11L);
        b.setName("Mobile app");
        b.setClient(client);
        b.setStatus("active");

        when(projectRepository.findActiveOrderedByRecentActivity()).thenReturn(List.of(a, b));
        // Native query returns Object[] rows: [project_id (Number), description (String)].
        // Project 11 has no description history, so its lastDescription should stay null.
        List<Object[]> rows = List.<Object[]>of(new Object[] { 10L, "fixing nav bar" });
        when(timeEntryRepository.findLatestDescriptionPerProject()).thenReturn(rows);

        List<ProjectResponse> result = controller.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).lastDescription()).isEqualTo("fixing nav bar");
        assertThat(result.get(1).lastDescription()).isNull();
    }
}
