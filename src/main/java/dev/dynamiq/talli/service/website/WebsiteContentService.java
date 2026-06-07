package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.github.GithubApiException;
import dev.dynamiq.talli.service.github.GithubCommitResult;
import dev.dynamiq.talli.service.github.GithubFileChange;
import dev.dynamiq.talli.service.github.GithubRepositoryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebsiteContentService {

    private final ProjectRepository projectRepository;
    private final WebsiteProjectService websiteProjectService;
    private final GithubRepositoryClient github;
    private final WebsiteContentAdapters adapters;

    public WebsiteContentService(ProjectRepository projectRepository,
                                 WebsiteProjectService websiteProjectService,
                                 GithubRepositoryClient github,
                                 WebsiteContentAdapters adapters) {
        this.projectRepository = projectRepository;
        this.websiteProjectService = websiteProjectService;
        this.github = github;
        this.adapters = adapters;
    }

    public WebsiteEditorForm load(Project project) {
        websiteProjectService.ensureConnected(project);
        WebsiteContentAdapter adapter = adapters.require(project.getWebsiteType());
        return adapter.toEditorForm(readFiles(project, adapter));
    }

    @Transactional
    public WebsiteSaveResult save(Project project,
                                  Map<String, String[]> params,
                                  MultiValueMap<String, MultipartFile> uploads) {
        websiteProjectService.ensureConnected(project);

        WebsiteContentAdapter adapter = adapters.require(project.getWebsiteType());
        Map<String, byte[]> files = readFiles(project, adapter);
        List<GithubFileChange> changes = adapter.apply(project.getId(), files, params, uploads);
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

    private Map<String, byte[]> readFiles(Project project, WebsiteContentAdapter adapter) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String path : adapter.expectedPaths()) {
            files.put(path, github.readFile(project.getGithubOwner(), project.getGithubRepo(),
                    project.getGithubBranch(), project.getGithubInstallationId(), path));
        }
        List<String> additionalPaths = adapter.additionalPaths(files);
        if (additionalPaths != null) {
            for (String path : additionalPaths) {
                files.computeIfAbsent(path, key -> github.readFile(project.getGithubOwner(), project.getGithubRepo(),
                        project.getGithubBranch(), project.getGithubInstallationId(), key));
            }
        }
        return files;
    }
}
