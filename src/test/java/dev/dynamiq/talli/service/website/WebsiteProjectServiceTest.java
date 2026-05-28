package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.github.GithubApiException;
import dev.dynamiq.talli.service.github.GithubRepo;
import dev.dynamiq.talli.service.github.GithubRepositoryClient;
import dev.dynamiq.talli.support.factory.ProjectFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
        WebsiteProjectService service = new WebsiteProjectService(null, null, null);
        Project project = ProjectFactory.configuredWebsiteProject();
        project.setWebsiteType("unknown");

        assertThatThrownBy(() -> service.ensureWebsiteConfigured(project))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported website type");
    }

    @Test
    void ensureConnectedRequiresInstallationId() {
        WebsiteProjectService service = new WebsiteProjectService(null, null, null);
        Project project = ProjectFactory.configuredWebsiteProject();

        assertThatThrownBy(() -> service.ensureConnected(project))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void connectExplainsMissingGithubAppInstallation() {
        Project project = ProjectFactory.configuredWebsiteProject();
        ProjectRepository projects = mock(ProjectRepository.class);
        GithubRepositoryClient github = mock(GithubRepositoryClient.class);

        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(github.findInstallationId("owner", "repo")).thenThrow(new GithubApiException(404, "Not Found"));

        WebsiteProjectService service = new WebsiteProjectService(projects, github, null);

        assertThatThrownBy(() -> service.connect(project.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub App is not installed")
                .hasMessageContaining("https://github.com/owner/repo")
                .hasMessageContaining("GITHUB_APP_ID");
    }

}
