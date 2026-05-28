package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.service.github.GithubRepo;
import dev.dynamiq.talli.support.factory.ProjectFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

}
