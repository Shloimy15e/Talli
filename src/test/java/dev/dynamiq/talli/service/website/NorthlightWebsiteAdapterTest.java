package dev.dynamiq.talli.service.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.service.github.GithubFileChange;
import dev.dynamiq.talli.support.factory.NorthlightWebsiteFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NorthlightWebsiteAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NorthlightWebsiteAdapter adapter = new NorthlightWebsiteAdapter(objectMapper);

    @Test
    void toFormLoadsExpectedNorthlightFields() {
        NorthlightWebsiteForm form = adapter.toForm(NorthlightWebsiteFactory.repoFiles());

        assertThat(form.homeHeroHeadline()).isEqualTo("Old headline");
        assertThat(form.homePillars()).hasSize(1);
        assertThat(form.homePillars().get(0).title()).isEqualTo("Understand");
        assertThat(form.aboutFounderName()).isEqualTo("Harrison Rand");
        assertThat(form.services()).hasSize(1);
        assertThat(form.transactions().get(0).images()).containsExactly("/images/transactions/a.jpg");
        assertThat(form.contactEmail()).isEqualTo("info@example.com");
    }

    @Test
    void applyUpdatesTextAndPreservesUnknownKeys() throws Exception {
        Map<String, String[]> params = NorthlightWebsiteFactory.formParams();
        params.put("homeHeroHeadline", NorthlightWebsiteFactory.one("New headline"));
        params.put("contactEmail", NorthlightWebsiteFactory.one("hello@example.com"));

        List<GithubFileChange> changes = adapter.apply(NorthlightWebsiteFactory.PROJECT_ID,
                NorthlightWebsiteFactory.repoFiles(), params,
                new LinkedMultiValueMap<String, MultipartFile>());
        Map<String, byte[]> byPath = changes.stream()
                .collect(Collectors.toMap(GithubFileChange::path, GithubFileChange::content));

        JsonNode home = objectMapper.readTree(byPath.get("content/home.json"));
        JsonNode contact = objectMapper.readTree(byPath.get("content/contact.json"));

        assertThat(home.path("hero").path("headline").asText()).isEqualTo("New headline");
        assertThat(home.path("customUnknownKey").asText()).isEqualTo("keep me");
        assertThat(contact.path("footer").path("email").asText()).isEqualTo("hello@example.com");
    }

    @Test
    void applyCommitsUploadedImageAndUpdatesJsonPath() throws Exception {
        Map<String, String[]> params = NorthlightWebsiteFactory.formParams();
        LinkedMultiValueMap<String, MultipartFile> uploads = new LinkedMultiValueMap<>();
        uploads.add("homeHighlightsImage", new MockMultipartFile(
                "homeHighlightsImage", "hero source.png", "image/png", "fake-image".getBytes(StandardCharsets.UTF_8)));

        List<GithubFileChange> changes = adapter.apply(NorthlightWebsiteFactory.PROJECT_ID,
                NorthlightWebsiteFactory.repoFiles(), params, uploads);
        Map<String, byte[]> byPath = changes.stream()
                .collect(Collectors.toMap(GithubFileChange::path, GithubFileChange::content));

        JsonNode home = objectMapper.readTree(byPath.get("content/home.json"));

        String expectedImagePath = "/images/cms/" + NorthlightWebsiteFactory.PROJECT_ID + "/home-highlights.png";
        assertThat(home.path("highlightsImage").asText()).isEqualTo(expectedImagePath);
        assertThat(byPath).containsKey("public" + expectedImagePath);
        assertThat(new String(byPath.get("public" + expectedImagePath), StandardCharsets.UTF_8))
                .isEqualTo("fake-image");
    }

    @Test
    void applyRejectsUnsupportedImageType() {
        Map<String, String[]> params = NorthlightWebsiteFactory.formParams();
        LinkedMultiValueMap<String, MultipartFile> uploads = new LinkedMultiValueMap<>();
        uploads.add("homeHighlightsImage", new MockMultipartFile(
                "homeHighlightsImage", "file.gif", "image/gif", "fake".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> adapter.apply(NorthlightWebsiteFactory.PROJECT_ID,
                NorthlightWebsiteFactory.repoFiles(), params, uploads))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JPG, PNG, and WEBP");
    }
}
