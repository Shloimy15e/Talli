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
    void toEditorFormLoadsExpectedNorthlightFields() {
        WebsiteEditorForm form = adapter.toEditorForm(NorthlightWebsiteFactory.repoFiles());

        assertThat(fieldValue(form, "homeHeroHeadline")).isEqualTo("Old headline");
        assertThat(fieldValue(form, "homePillars__title")).isEqualTo("Understand");
        assertThat(fieldValue(form, "aboutFounderName")).isEqualTo("Harrison Rand");
        assertThat(fieldValue(form, "services__title")).isEqualTo("Advisory");
        assertThat(fieldValues(form, "transactions__images")).containsExactly("/images/transactions/a.jpg");
        assertThat(fieldValue(form, "contactEmail")).isEqualTo("info@example.com");
    }

    @Test
    void toEditorFormDoesNotExposePillarNumberField() {
        WebsiteEditorForm form = adapter.toEditorForm(NorthlightWebsiteFactory.repoFiles());

        List<String> fieldNames = form.sections().stream()
                .flatMap(section -> section.blocks().stream())
                .flatMap(block -> block.kind().equals("repeat")
                        ? block.repeat().items().stream().flatMap(item -> item.fields().stream())
                        : block.fields().stream())
                .map(WebsiteEditorField::name)
                .toList();

        assertThat(fieldNames).contains("homePillars__title", "homePillars__description");
        assertThat(fieldNames).doesNotContain("homePillars__number");
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
    void applyAddsNewRepeatableRows() throws Exception {
        Map<String, String[]> params = NorthlightWebsiteFactory.formParams();
        params.put("homePillars__title_1", NorthlightWebsiteFactory.one("Execute"));
        params.put("homePillars__description_1", NorthlightWebsiteFactory.one("Move decisively"));
        params.put("homeExpertiseCategories__title_1", NorthlightWebsiteFactory.one("Retail"));
        params.put("aboutValues__name_1", NorthlightWebsiteFactory.one("Clarity"));
        params.put("aboutValues__description_1", NorthlightWebsiteFactory.one("Plain-spoken advice"));
        params.put("services__title_1", NorthlightWebsiteFactory.one("Capital Markets"));
        params.put("services__description_1", NorthlightWebsiteFactory.one("Debt and equity strategy"));
        params.put("transactions__location_1", NorthlightWebsiteFactory.one("Brooklyn, NY"));
        params.put("transactions__units_1", NorthlightWebsiteFactory.one("84 Units"));
        params.put("transactions__imagesTouched_1", NorthlightWebsiteFactory.one("true"));
        params.put("transactions__imagesExisting_1_0", NorthlightWebsiteFactory.one("/images/transactions/b.jpg"));

        List<GithubFileChange> changes = adapter.apply(NorthlightWebsiteFactory.PROJECT_ID,
                NorthlightWebsiteFactory.repoFiles(), params,
                new LinkedMultiValueMap<String, MultipartFile>());
        Map<String, byte[]> byPath = changes.stream()
                .collect(Collectors.toMap(GithubFileChange::path, GithubFileChange::content));

        JsonNode home = objectMapper.readTree(byPath.get("content/home.json"));
        JsonNode about = objectMapper.readTree(byPath.get("content/about.json"));
        JsonNode services = objectMapper.readTree(byPath.get("content/services.json"));
        JsonNode transactions = objectMapper.readTree(byPath.get("content/transactions.json"));

        assertThat(home.path("approach").path("pillars").size()).isEqualTo(2);
        assertThat(home.path("approach").path("pillars").get(0).path("number").asText()).isEqualTo("1");
        assertThat(home.path("approach").path("pillars").get(1).path("number").asText()).isEqualTo("2");
        assertThat(home.path("expertise").path("categories").size()).isEqualTo(2);
        assertThat(about.path("values").path("list").size()).isEqualTo(2);
        assertThat(services.path("services").size()).isEqualTo(2);
        assertThat(services.path("services").get(1).path("title").asText()).isEqualTo("Capital Markets");
        assertThat(transactions.path("transactions").size()).isEqualTo(2);
        assertThat(transactions.path("transactions").get(1).path("images").get(0).asText())
                .isEqualTo("/images/transactions/b.jpg");
    }

    @Test
    void applyUploadsImageForNewServiceRow() throws Exception {
        Map<String, String[]> params = NorthlightWebsiteFactory.formParams();
        params.put("services__title_1", NorthlightWebsiteFactory.one("Capital Markets"));
        params.put("services__description_1", NorthlightWebsiteFactory.one("Debt and equity strategy"));
        LinkedMultiValueMap<String, MultipartFile> uploads = new LinkedMultiValueMap<>();
        uploads.add("services__image_1", new MockMultipartFile(
                "services__image_1", "capital.webp", "image/webp", "service-image".getBytes(StandardCharsets.UTF_8)));

        List<GithubFileChange> changes = adapter.apply(NorthlightWebsiteFactory.PROJECT_ID,
                NorthlightWebsiteFactory.repoFiles(), params, uploads);
        Map<String, byte[]> byPath = changes.stream()
                .collect(Collectors.toMap(GithubFileChange::path, GithubFileChange::content));

        JsonNode services = objectMapper.readTree(byPath.get("content/services.json"));
        String expectedImagePath = "/images/cms/" + NorthlightWebsiteFactory.PROJECT_ID + "/service-2.webp";

        assertThat(services.path("serviceImages").get(1).path("src").asText()).isEqualTo(expectedImagePath);
        assertThat(services.path("services").get(1).path("image").isMissingNode()).isTrue();
        assertThat(byPath).containsKey("public" + expectedImagePath);
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

    private String fieldValue(WebsiteEditorForm form, String name) {
        return form.sections().stream()
                .flatMap(section -> section.blocks().stream())
                .flatMap(block -> block.kind().equals("repeat")
                        ? block.repeat().items().stream().flatMap(item -> item.fields().stream())
                        : block.fields().stream())
                .filter(field -> field.name().equals(name))
                .findFirst()
                .map(WebsiteEditorField::value)
                .orElse("");
    }

    private List<String> fieldValues(WebsiteEditorForm form, String name) {
        return form.sections().stream()
                .flatMap(section -> section.blocks().stream())
                .flatMap(block -> block.kind().equals("repeat")
                        ? block.repeat().items().stream().flatMap(item -> item.fields().stream())
                        : block.fields().stream())
                .filter(field -> field.name().equals(name))
                .findFirst()
                .map(WebsiteEditorField::values)
                .orElse(List.of());
    }
}
