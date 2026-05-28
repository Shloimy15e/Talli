package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import org.springframework.stereotype.Component;

@Component("websiteAssetUrls")
public class WebsiteAssetUrlResolver {

    public String src(Project project, String path) {
        if (path == null || path.isBlank() || isAbsolute(path)) {
            return path;
        }

        String baseUrl = project == null ? null : project.getWebsitePublicUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return path;
        }

        String normalizedBase = baseUrl.trim().replaceAll("/+$", "");
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private boolean isAbsolute(String path) {
        String lower = path.toLowerCase();
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("//")
                || lower.startsWith("data:")
                || lower.startsWith("blob:");
    }
}
