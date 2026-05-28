package dev.dynamiq.talli.service.website;

import dev.dynamiq.talli.model.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebsiteAssetUrlResolverTest {

    private final WebsiteAssetUrlResolver resolver = new WebsiteAssetUrlResolver();

    @Test
    void srcPrefixesRootRelativePathsWithWebsitePublicUrl() {
        Project project = project("https://northlight.example");

        String src = resolver.src(project, "/images/home/highlights.png");

        assertThat(src).isEqualTo("https://northlight.example/images/home/highlights.png");
        assertThat(src).doesNotContain("//images");
    }

    @Test
    void srcAvoidsDoubleSlashWhenWebsitePublicUrlHasTrailingSlash() {
        Project project = project("https://northlight.example/");

        String src = resolver.src(project, "/images/home/highlights.png");

        assertThat(src).isEqualTo("https://northlight.example/images/home/highlights.png");
    }

    @Test
    void srcLeavesAbsoluteUrlsUnchanged() {
        Project project = project("https://northlight.example");

        String src = resolver.src(project, "https://cdn.example/highlights.png");

        assertThat(src).isEqualTo("https://cdn.example/highlights.png");
    }

    @Test
    void srcFallsBackToOriginalPathWhenWebsitePublicUrlIsMissing() {
        Project project = project(null);

        String src = resolver.src(project, "/images/home/highlights.png");

        assertThat(src).isEqualTo("/images/home/highlights.png");
    }

    private Project project(String websitePublicUrl) {
        Project project = new Project();
        project.setWebsitePublicUrl(websitePublicUrl);
        return project;
    }
}
