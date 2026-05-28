package dev.dynamiq.talli.support.factory;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.service.website.NorthlightWebsiteAdapter;

public final class ProjectFactory {

    private ProjectFactory() {
    }

    public static Project connectedWebsiteProject() {
        Project project = configuredWebsiteProject();
        project.setGithubInstallationId(123L);
        project.setLastPublishSha("oldsha");
        return project;
    }

    public static Project configuredWebsiteProject() {
        Project project = new Project();
        project.setId(1L);
        project.setName("Northlight");
        project.setWebsiteEnabled(true);
        project.setWebsiteType(NorthlightWebsiteAdapter.TYPE);
        project.setGithubOwner("owner");
        project.setGithubRepo("repo");
        project.setGithubBranch("main");
        return project;
    }
}
