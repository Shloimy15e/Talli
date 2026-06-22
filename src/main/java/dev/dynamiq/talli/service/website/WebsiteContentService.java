package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.github.GithubCommitResult;
import dev.dynamiq.talli.service.github.GithubFileChange;
import dev.dynamiq.talli.service.github.GithubRepositoryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WebsiteContentService {

    private final ProjectRepository projectRepository;
    private final WebsiteProjectService websiteProjectService;
    private final GithubRepositoryClient github;

    public WebsiteContentService(ProjectRepository projectRepository,
                                 WebsiteProjectService websiteProjectService,
                                 GithubRepositoryClient github) {
        this.projectRepository = projectRepository;
        this.websiteProjectService = websiteProjectService;
        this.github = github;
    }

    public WebsiteEditorForm load(Project project) {
        websiteProjectService.ensureConnected(project);
        WebsiteProjectService.WebsiteContentSnapshot content = websiteProjectService.readContent(project);
        return content.adapter().toEditorForm(content.files());
    }

    @Transactional
    public WebsiteSaveResult save(Project project,
                                  Map<String, String[]> params,
                                  MultiValueMap<String, MultipartFile> uploads) {
        websiteProjectService.ensureConnected(project);

        WebsiteProjectService.WebsiteContentSnapshot content = websiteProjectService.readContent(project);
        List<GithubFileChange> changes = content.adapter().apply(project.getId(), content.files(), params, uploads);
        if (changes.isEmpty()) {
            return new WebsiteSaveResult(false, project.getLastPublishSha());
        }

        GithubCommitResult result = github.commitFiles(
                project.getGithubOwner(),
                project.getGithubRepo(),
                project.getGithubBranch(),
                project.getGithubInstallationId(),
                "Update website content for " + project.getName(),
                changes
        );

        Project managed = projectRepository.findById(project.getId()).orElseThrow();
        managed.setLastPublishSha(result.sha());
        managed.setLastPublishAt(LocalDateTime.now());
        return new WebsiteSaveResult(true, result.sha());
    }
}
