package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.github.GithubCommitResult;
import dev.dynamiq.talli.service.github.GithubFileChange;
import dev.dynamiq.talli.service.github.GithubRepositoryClient;
import dev.dynamiq.talli.support.factory.ProjectFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebsiteContentServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WebsiteProjectService websiteProjectService;

    @Mock
    private GithubRepositoryClient github;

    @Mock
    private WebsiteContentAdapter adapter;

    @Test
    void saveReturnsUnchangedWhenAdapterProducesNoChanges() {
        Project project = ProjectFactory.connectedWebsiteProject();
        when(websiteProjectService.readContent(project)).thenReturn(snapshot(Map.of("content/home.json", bytes("{}"))));
        when(adapter.apply(eq(1L), any(), any(), any())).thenReturn(List.of());

        WebsiteContentService service = new WebsiteContentService(projectRepository, websiteProjectService, github);

        WebsiteSaveResult result = service.save(project, Map.of(), new LinkedMultiValueMap<>());

        assertThat(result.changed()).isFalse();
        verify(github, never()).commitFiles(any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void savePassesLoadedContentToAdapter() {
        Project project = ProjectFactory.connectedWebsiteProject();
        when(websiteProjectService.readContent(project)).thenReturn(snapshot(Map.of(
                "talli/editor.schema.json", bytes("schema"),
                "content/home.json", bytes("home")
        )));
        when(adapter.apply(eq(1L), any(), any(), any())).thenReturn(List.of());

        WebsiteContentService service = new WebsiteContentService(projectRepository, websiteProjectService, github);

        service.save(project, Map.of(), new LinkedMultiValueMap<>());

        ArgumentCaptor<Map<String, byte[]>> files = ArgumentCaptor.forClass(Map.class);
        verify(adapter).apply(eq(1L), files.capture(), any(), any());
        assertThat(new String(files.getValue().get("talli/editor.schema.json"), StandardCharsets.UTF_8)).isEqualTo("schema");
        assertThat(new String(files.getValue().get("content/home.json"), StandardCharsets.UTF_8)).isEqualTo("home");
    }

    @Test
    void saveCommitsChangesAndStoresLastPublishData() {
        Project project = ProjectFactory.connectedWebsiteProject();
        Project managed = ProjectFactory.connectedWebsiteProject();
        GithubFileChange change = new GithubFileChange("content/home.json", "{}".getBytes(StandardCharsets.UTF_8));
        when(websiteProjectService.readContent(project)).thenReturn(snapshot(Map.of("content/home.json", bytes("{}"))));
        when(adapter.apply(eq(1L), any(), any(), any())).thenReturn(List.of(change));
        when(github.commitFiles(eq("owner"), eq("repo"), eq("main"), eq(123L), any(), eq(List.of(change))))
                .thenReturn(new GithubCommitResult("abc123"));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(managed));

        WebsiteContentService service = new WebsiteContentService(projectRepository, websiteProjectService, github);

        WebsiteSaveResult result = service.save(project, Map.of(), new LinkedMultiValueMap<>());

        assertThat(result.changed()).isTrue();
        assertThat(result.commitSha()).isEqualTo("abc123");
        assertThat(managed.getLastPublishSha()).isEqualTo("abc123");
        assertThat(managed.getLastPublishAt()).isNotNull();
    }

    @Test
    void saveEnsuresProjectIsConnectedBeforePublishing() {
        Project project = ProjectFactory.connectedWebsiteProject();
        when(websiteProjectService.readContent(project)).thenReturn(snapshot(Map.of("content/home.json", bytes("{}"))));
        when(adapter.apply(eq(1L), any(), any(), any())).thenReturn(List.of());

        WebsiteContentService service = new WebsiteContentService(projectRepository, websiteProjectService, github);

        service.save(project, Map.of(), new LinkedMultiValueMap<>());

        verify(websiteProjectService).ensureConnected(project);
    }

    private WebsiteProjectService.WebsiteContentSnapshot snapshot(Map<String, byte[]> files) {
        return new WebsiteProjectService.WebsiteContentSnapshot(adapter, files);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
