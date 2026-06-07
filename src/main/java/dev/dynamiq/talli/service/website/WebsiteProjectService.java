package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.github.GithubApiException;
import dev.dynamiq.talli.service.github.GithubRepo;
import dev.dynamiq.talli.service.github.GithubRepositoryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebsiteProjectService {

    private static final Pattern HTTPS = Pattern.compile("^https://github\\.com/([^/]+)/([^/.]+)(?:\\.git)?/?$");
    private static final Pattern SSH = Pattern.compile("^git@github\\.com:([^/]+)/([^/.]+)(?:\\.git)?$");
    private static final Pattern SHORT = Pattern.compile("^([^/\\s]+)/([^/\\s.]+)$");

    private final ProjectRepository projectRepository;
    private final GithubRepositoryClient github;
    private final WebsiteContentAdapters adapters;

    public WebsiteProjectService(ProjectRepository projectRepository,
                                 GithubRepositoryClient github,
                                 WebsiteContentAdapters adapters) {
        this.projectRepository = projectRepository;
        this.github = github;
        this.adapters = adapters;
    }

    public void applySettings(Project target, Project submitted, String githubRepoUrl) {
        boolean enabled = Boolean.TRUE.equals(submitted.getWebsiteEnabled());
        target.setWebsiteEnabled(enabled);
        target.setWebsitePublicUrl(blankToNull(submitted.getWebsitePublicUrl()));
        target.setWebsiteType(blankToDefault(submitted.getWebsiteType(), adapters.defaultType()));
        target.setGithubBranch(blankToDefault(submitted.getGithubBranch(), "main"));

        if (githubRepoUrl != null && !githubRepoUrl.isBlank()) {
            GithubRepo repo = parseRepoUrl(githubRepoUrl);
            boolean repoChanged = !repo.owner().equals(target.getGithubOwner()) || !repo.repo().equals(target.getGithubRepo());
            target.setGithubOwner(repo.owner());
            target.setGithubRepo(repo.repo());
            if (repoChanged) {
                target.setGithubInstallationId(null);
            }
        } else {
            target.setGithubOwner(null);
            target.setGithubRepo(null);
            target.setGithubInstallationId(null);
        }
    }

    @Transactional
    public void connect(Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        ensureWebsiteConfigured(project);

        Long installationId;
        try {
            installationId = github.findInstallationId(project.getGithubOwner(), project.getGithubRepo());
        } catch (GithubApiException e) {
            if (e.statusCode() == 404) {
                throw new IllegalStateException("GitHub App is not installed on "
                        + displayRepoUrl(project)
                        + ", or Railway is using credentials for a different app. Install the app on this repo and verify GITHUB_APP_ID/GITHUB_APP_PRIVATE_KEY.", e);
            }
            throw e;
        }
        project.setGithubInstallationId(installationId);

        github.branchHeadSha(project.getGithubOwner(), project.getGithubRepo(), project.getGithubBranch(), installationId);
        WebsiteContentAdapter adapter = adapters.require(project.getWebsiteType());
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String path : adapter.expectedPaths()) {
            files.put(path, github.readFile(project.getGithubOwner(), project.getGithubRepo(), project.getGithubBranch(),
                    installationId, path));
        }
        List<String> additionalPaths = adapter.additionalPaths(files);
        if (additionalPaths != null) {
            for (String path : additionalPaths) {
                files.computeIfAbsent(path, key -> github.readFile(project.getGithubOwner(), project.getGithubRepo(),
                        project.getGithubBranch(), installationId, key));
            }
        }
    }

    public void ensureWebsiteConfigured(Project project) {
        if (!Boolean.TRUE.equals(project.getWebsiteEnabled())) {
            throw new IllegalStateException("Website editing is not enabled for this project.");
        }
        adapters.require(project.getWebsiteType());
        if (project.getGithubOwner() == null || project.getGithubRepo() == null) {
            throw new IllegalStateException("Repository URL is missing.");
        }
        if (project.getGithubBranch() == null || project.getGithubBranch().isBlank()) {
            throw new IllegalStateException("GitHub branch is missing.");
        }
    }

    public void ensureConnected(Project project) {
        ensureWebsiteConfigured(project);
        if (project.getGithubInstallationId() == null) {
            throw new IllegalStateException("Website project is not connected to GitHub yet.");
        }
    }

    public static GithubRepo parseRepoUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        for (Pattern pattern : new Pattern[] {HTTPS, SSH, SHORT}) {
            Matcher matcher = pattern.matcher(trimmed);
            if (matcher.matches()) {
                return new GithubRepo(matcher.group(1), matcher.group(2));
            }
        }
        throw new IllegalArgumentException("Enter a GitHub repo URL like https://github.com/owner/repo.");
    }

    public String displayRepoUrl(Project project) {
        if (project.getGithubOwner() == null || project.getGithubRepo() == null) {
            return "";
        }
        return "https://github.com/" + project.getGithubOwner() + "/" + project.getGithubRepo();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
