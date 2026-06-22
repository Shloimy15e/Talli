package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.github.GithubApiException;
import dev.dynamiq.talli.service.github.GithubRepo;
import dev.dynamiq.talli.service.github.GithubRepositoryClient;
import dev.dynamiq.talli.support.factory.ProjectFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsiteProjectServiceTest {

    @Test
    void parseRepoUrlAcceptsHttpsUrl() {
        GithubRepo repo = WebsiteProjectService.parseRepoUrl("https://github.com/Shloimy15e/north-light");

        assertThat(repo.owner()).isEqualTo("Shloimy15e");
        assertThat(repo.repo()).isEqualTo("north-light");
    }

    @Test
    void parseRepoUrlAcceptsHttpsGitUrl() {
        GithubRepo repo = WebsiteProjectService.parseRepoUrl("https://github.com/Shloimy15e/north-light.git");

        assertThat(repo.owner()).isEqualTo("Shloimy15e");
        assertThat(repo.repo()).isEqualTo("north-light");
    }

    @Test
    void parseRepoUrlAcceptsSshUrl() {
        GithubRepo repo = WebsiteProjectService.parseRepoUrl("git@github.com:Shloimy15e/north-light.git");

        assertThat(repo.owner()).isEqualTo("Shloimy15e");
        assertThat(repo.repo()).isEqualTo("north-light");
    }

    @Test
    void parseRepoUrlAcceptsOwnerRepoShorthand() {
        GithubRepo repo = WebsiteProjectService.parseRepoUrl("Shloimy15e/north-light");

        assertThat(repo.owner()).isEqualTo("Shloimy15e");
        assertThat(repo.repo()).isEqualTo("north-light");
    }

    @Test
    void parseRepoUrlRejectsUnsupportedUrl() {
        assertThatThrownBy(() -> WebsiteProjectService.parseRepoUrl("https://gitlab.com/acme/site"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enter a GitHub repo URL");
    }

    @Test
    void ensureWebsiteConfiguredRequiresEnabledProject() {
        WebsiteProjectService service = new WebsiteProjectService(null, null, null);
        Project project = new Project();
        project.setWebsiteEnabled(false);

        assertThatThrownBy(() -> service.ensureWebsiteConfigured(project))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Website editing is not enabled");
    }

    @Test
    void ensureWebsiteConfiguredRequiresSupportedType() {
        WebsiteContentAdapters adapters = mock(WebsiteContentAdapters.class);
        WebsiteProjectService service = new WebsiteProjectService(null, null, adapters);
        Project project = ProjectFactory.configuredWebsiteProject();
        project.setWebsiteType("unknown");
        when(adapters.require("unknown")).thenThrow(new IllegalStateException("Unsupported website type: unknown"));

        assertThatThrownBy(() -> service.ensureWebsiteConfigured(project))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported website type");
    }

    @Test
    void ensureConnectedRequiresInstallationId() {
        WebsiteContentAdapters adapters = mock(WebsiteContentAdapters.class);
        WebsiteContentAdapter adapter = mock(WebsiteContentAdapter.class);
        WebsiteProjectService service = new WebsiteProjectService(null, null, adapters);
        Project project = ProjectFactory.configuredWebsiteProject();
        when(adapters.require(project.getWebsiteType())).thenReturn(adapter);

        assertThatThrownBy(() -> service.ensureConnected(project))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void connectExplainsMissingGithubAppInstallation() {
        Project project = ProjectFactory.configuredWebsiteProject();
        ProjectRepository projects = mock(ProjectRepository.class);
        GithubRepositoryClient github = mock(GithubRepositoryClient.class);
        WebsiteContentAdapters adapters = mock(WebsiteContentAdapters.class);
        WebsiteContentAdapter adapter = mock(WebsiteContentAdapter.class);

        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(adapters.require(project.getWebsiteType())).thenReturn(adapter);
        when(github.findInstallationId("owner", "repo")).thenThrow(new GithubApiException(404, "Not Found"));

        WebsiteProjectService service = new WebsiteProjectService(projects, github, adapters);

        assertThatThrownBy(() -> service.connect(project.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub App is not installed")
                .hasMessageContaining("https://github.com/owner/repo")
                .hasMessageContaining("GITHUB_APP_ID");
    }

    @Test
    void connectVerifiesSchemaDeclaredContentFiles() {
        Project project = ProjectFactory.configuredWebsiteProject();
        project.setWebsiteType(TalliWebsiteSchemaAdapter.TYPE);
        ProjectRepository projects = mock(ProjectRepository.class);
        GithubRepositoryClient github = mock(GithubRepositoryClient.class);
        WebsiteContentAdapters adapters = mock(WebsiteContentAdapters.class);
        WebsiteContentAdapter adapter = mock(WebsiteContentAdapter.class);

        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(github.findInstallationId("owner", "repo")).thenReturn(123L);
        when(github.branchHeadSha("owner", "repo", "main", 123L)).thenReturn("sha");
        when(adapters.require(TalliWebsiteSchemaAdapter.TYPE)).thenReturn(adapter);
        when(adapter.expectedPaths()).thenReturn(List.of(TalliWebsiteSchemaAdapter.SCHEMA_PATH));
        when(adapter.additionalPaths(any())).thenReturn(List.of("content/home.json"));
        when(github.readFile(eq("owner"), eq("repo"), eq("main"), eq(123L), eq(TalliWebsiteSchemaAdapter.SCHEMA_PATH)))
                .thenReturn("schema".getBytes(StandardCharsets.UTF_8));
        when(github.readFile(eq("owner"), eq("repo"), eq("main"), eq(123L), eq("content/home.json")))
                .thenReturn("home".getBytes(StandardCharsets.UTF_8));

        WebsiteProjectService service = new WebsiteProjectService(projects, github, adapters);

        service.connect(project.getId());

        assertThat(project.getGithubInstallationId()).isEqualTo(123L);
        verify(github).readFile("owner", "repo", "main", 123L, TalliWebsiteSchemaAdapter.SCHEMA_PATH);
        verify(github).readFile("owner", "repo", "main", 123L, "content/home.json");
    }

    @Test
    void connectPrefersRepoTalliSchemaOverLegacyProjectType() {
        Project project = ProjectFactory.configuredWebsiteProject();
        ProjectRepository projects = mock(ProjectRepository.class);
        GithubRepositoryClient github = mock(GithubRepositoryClient.class);
        WebsiteContentAdapters adapters = mock(WebsiteContentAdapters.class);
        WebsiteContentAdapter adapter = mock(WebsiteContentAdapter.class);

        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(github.findInstallationId("owner", "repo")).thenReturn(123L);
        when(github.branchHeadSha("owner", "repo", "main", 123L)).thenReturn("sha");
        when(adapters.require(NorthlightWebsiteAdapter.TYPE)).thenReturn(adapter);
        when(adapters.require(TalliWebsiteSchemaAdapter.TYPE)).thenReturn(adapter);
        when(adapter.expectedPaths()).thenReturn(List.of(TalliWebsiteSchemaAdapter.SCHEMA_PATH));
        when(adapter.additionalPaths(any())).thenReturn(List.of("content/home.json"));
        when(github.readFile(eq("owner"), eq("repo"), eq("main"), eq(123L), eq(TalliWebsiteSchemaAdapter.SCHEMA_PATH)))
                .thenReturn("schema".getBytes(StandardCharsets.UTF_8));
        when(github.readFile(eq("owner"), eq("repo"), eq("main"), eq(123L), eq("content/home.json")))
                .thenReturn("home".getBytes(StandardCharsets.UTF_8));

        WebsiteProjectService service = new WebsiteProjectService(projects, github, adapters);

        service.connect(project.getId());

        assertThat(project.getWebsiteType()).isEqualTo(TalliWebsiteSchemaAdapter.TYPE);
        verify(github, times(1)).readFile("owner", "repo", "main", 123L, TalliWebsiteSchemaAdapter.SCHEMA_PATH);
        verify(github).readFile("owner", "repo", "main", 123L, "content/home.json");
    }

    @Test
    void connectFallsBackToLegacyAdapterWhenTalliSchemaIsMissing() {
        Project project = ProjectFactory.configuredWebsiteProject();
        ProjectRepository projects = mock(ProjectRepository.class);
        GithubRepositoryClient github = mock(GithubRepositoryClient.class);
        WebsiteContentAdapters adapters = mock(WebsiteContentAdapters.class);
        WebsiteContentAdapter adapter = mock(WebsiteContentAdapter.class);

        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(github.findInstallationId("owner", "repo")).thenReturn(123L);
        when(github.branchHeadSha("owner", "repo", "main", 123L)).thenReturn("sha");
        when(adapters.require(NorthlightWebsiteAdapter.TYPE)).thenReturn(adapter);
        when(github.readFile(eq("owner"), eq("repo"), eq("main"), eq(123L), eq(TalliWebsiteSchemaAdapter.SCHEMA_PATH)))
                .thenThrow(new GithubApiException(404, "Not Found"));
        when(adapter.expectedPaths()).thenReturn(List.of("content/home.json"));
        when(github.readFile(eq("owner"), eq("repo"), eq("main"), eq(123L), eq("content/home.json")))
                .thenReturn("home".getBytes(StandardCharsets.UTF_8));

        WebsiteProjectService service = new WebsiteProjectService(projects, github, adapters);

        service.connect(project.getId());

        assertThat(project.getWebsiteType()).isEqualTo(NorthlightWebsiteAdapter.TYPE);
        verify(github).readFile("owner", "repo", "main", 123L, TalliWebsiteSchemaAdapter.SCHEMA_PATH);
        verify(github).readFile("owner", "repo", "main", 123L, "content/home.json");
    }

    @Test
    void connectExplainsMissingTalliSchemaForTalliProjects() {
        Project project = ProjectFactory.configuredWebsiteProject();
        project.setWebsiteType(TalliWebsiteSchemaAdapter.TYPE);
        ProjectRepository projects = mock(ProjectRepository.class);
        GithubRepositoryClient github = mock(GithubRepositoryClient.class);
        WebsiteContentAdapters adapters = mock(WebsiteContentAdapters.class);
        WebsiteContentAdapter adapter = mock(WebsiteContentAdapter.class);

        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(github.findInstallationId("owner", "repo")).thenReturn(123L);
        when(github.branchHeadSha("owner", "repo", "main", 123L)).thenReturn("sha");
        when(adapters.require(TalliWebsiteSchemaAdapter.TYPE)).thenReturn(adapter);
        when(github.readFile(eq("owner"), eq("repo"), eq("main"), eq(123L), eq(TalliWebsiteSchemaAdapter.SCHEMA_PATH)))
                .thenThrow(new GithubApiException(404, "Not Found"));

        WebsiteProjectService service = new WebsiteProjectService(projects, github, adapters);

        assertThatThrownBy(() -> service.connect(project.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Talli website schema is missing")
                .hasMessageContaining(TalliWebsiteSchemaAdapter.SCHEMA_PATH);
    }

}
