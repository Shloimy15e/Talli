package dev.dynamiq.talli.service.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.service.github.GithubFileChange;
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

class TalliWebsiteSchemaAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TalliWebsiteSchemaAdapter adapter = new TalliWebsiteSchemaAdapter(objectMapper);

    @Test
    void additionalPathsReadsContentFilesFromSchema() {
        assertThat(adapter.expectedPaths()).containsExactly(TalliWebsiteSchemaAdapter.SCHEMA_PATH);
        assertThat(adapter.additionalPaths(filesWithSchemaOnly()))
                .containsExactly("content/home.json", "content/contact.json", "content/global.json");
    }

    @Test
    void toEditorFormBuildsFieldsAndRepeatBlocksFromTalliSchema() {
        WebsiteEditorForm form = adapter.toEditorForm(files());

        assertThat(form.sections()).hasSize(2);
        WebsiteEditorSection home = form.sections().get(0);
        assertThat(home.label()).isEqualTo("Home page");

        WebsiteEditorField headline = home.blocks().get(0).fields().get(0);
        assertThat(headline.name()).isEqualTo("homeHeroHeadline");
        assertThat(headline.kind()).isEqualTo("richText");
        assertThat(headline.value()).isEqualTo("Old **headline**");
        assertThat(headline.marks()).containsExactly("accent");
        assertThat(headline.supportsMark("accent")).isTrue();

        WebsiteEditorField color = form.sections().get(1).blocks().get(0).fields().get(1);
        assertThat(color.kind()).isEqualTo("color");
        assertThat(color.value()).isEqualTo("#D2A84F");
        assertThat(color.invalidMessage()).isEqualTo("Accent color must be a hex color like #D2A84F.");

        WebsiteEditorBlock pillars = home.blocks().get(1);
        assertThat(pillars.repeat().itemLabel()).isEqualTo("Pillar");
        assertThat(pillars.repeat().templateFields())
                .extracting(WebsiteEditorField::name)
                .containsExactly("homePillars__title", "homePillars__description");
        assertThat(pillars.repeat().items().get(0).fields())
                .extracting(WebsiteEditorField::value)
                .containsExactly("Understanding", "Listen first");
    }

    @Test
    void schemaCanDescribeArbitrarySectionsAndContentFiles() throws Exception {
        assertThat(adapter.additionalPaths(Map.of(TalliWebsiteSchemaAdapter.SCHEMA_PATH, bytes(arbitrarySchema()))))
                .containsExactly("content/landing.json", "content/workshops.json", "content/legal/privacy.json");

        WebsiteEditorForm form = adapter.toEditorForm(arbitraryFiles());

        assertThat(form.sections().stream().map(WebsiteEditorSection::key).toList())
                .containsExactly("landing", "workshops", "privacy");
        assertThat(form.sections().stream().map(WebsiteEditorSection::label).toList())
                .containsExactly("Landing", "Workshops", "Privacy notice");
        assertThat(form.sections().get(0).blocks().get(0).fields().get(0).value())
                .isEqualTo("A different kind of site");
        assertThat(form.sections().get(1).blocks().get(0).repeat().items().get(0).fields().get(0).value())
                .isEqualTo("Owner training");

        List<GithubFileChange> changes = adapter.apply(12L, arbitraryFiles(), Map.ofEntries(
                Map.entry("landingHeadline", one("Updated landing")),
                Map.entry("workshopSessions__name_0", one("First workshop")),
                Map.entry("privacyNotice", one("Updated privacy notice"))
        ), new LinkedMultiValueMap<>());
        Map<String, byte[]> byPath = changes.stream()
                .collect(Collectors.toMap(GithubFileChange::path, GithubFileChange::content));

        assertThat(objectMapper.readTree(byPath.get("content/landing.json")).path("hero").path("headline").asText())
                .isEqualTo("Updated landing");
        assertThat(objectMapper.readTree(byPath.get("content/workshops.json")).path("sessions").get(0).path("name").asText())
                .isEqualTo("First workshop");
        assertThat(objectMapper.readTree(byPath.get("content/legal/privacy.json")).path("notice").asText())
                .isEqualTo("Updated privacy notice");
    }

    @Test
    void applyUpdatesContentWithComputedRepeatValuesAndUploads() throws Exception {
        Map<String, String[]> params = Map.ofEntries(
                Map.entry("homeHeroHeadline", one("New **headline**")),
                Map.entry("homeIntroParagraphs", one("First paragraph\n\nSecond paragraph")),
                Map.entry("homePillars__title_0", one("Understanding")),
                Map.entry("homePillars__description_0", one("Updated description")),
                Map.entry("homePillars__title_1", one("Execution")),
                Map.entry("homePillars__description_1", one("Move carefully")),
                Map.entry("contactEmail", one("hello@example.com")),
                Map.entry("brandAccentColor", one("#d2a84f"))
        );
        LinkedMultiValueMap<String, MultipartFile> uploads = new LinkedMultiValueMap<>();
        uploads.add("homeHeroImage", new MockMultipartFile(
                "homeHeroImage", "hero source.png", "image/png", "image".getBytes(StandardCharsets.UTF_8)));

        List<GithubFileChange> changes = adapter.apply(7L, files(), params, uploads);
        Map<String, byte[]> byPath = changes.stream()
                .collect(Collectors.toMap(GithubFileChange::path, GithubFileChange::content));

        JsonNode home = objectMapper.readTree(byPath.get("content/home.json"));
        JsonNode contact = objectMapper.readTree(byPath.get("content/contact.json"));
        JsonNode global = objectMapper.readTree(byPath.get("content/global.json"));

        assertThat(home.path("hero").path("headline").asText()).isEqualTo("New **headline**");
        assertThat(home.path("hero").path("image").path("src").asText()).isEqualTo("/images/cms/7/home-hero.png");
        assertThat(home.path("intro").path("paragraphs")).hasSize(2);
        assertThat(home.path("approach").path("pillars")).hasSize(2);
        assertThat(home.path("approach").path("pillars").get(0).path("number").asText()).isEqualTo("01");
        assertThat(home.path("approach").path("pillars").get(1).path("number").asText()).isEqualTo("02");
        assertThat(home.path("approach").path("pillars").get(1).path("title").asText()).isEqualTo("Execution");
        assertThat(contact.path("footer").path("email").asText()).isEqualTo("hello@example.com");
        assertThat(global.path("brand").path("accentColor").asText()).isEqualTo("#D2A84F");
        assertThat(byPath).containsKey("public/images/cms/7/home-hero.png");
    }

    @Test
    void applyRejectsInvalidColorValues() {
        assertThatThrownBy(() -> adapter.apply(7L, files(), Map.ofEntries(
                Map.entry("brandAccentColor", one("gold"))
        ), new LinkedMultiValueMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hex color");
    }

    private Map<String, byte[]> filesWithSchemaOnly() {
        return Map.of(TalliWebsiteSchemaAdapter.SCHEMA_PATH, bytes(schema()));
    }

    private Map<String, byte[]> files() {
        return Map.of(
                TalliWebsiteSchemaAdapter.SCHEMA_PATH, bytes(schema()),
                "content/home.json", bytes("""
                        {
                          "hero": {
                            "headline": "Old **headline**",
                            "text": "Old text",
                            "image": {
                              "src": "/images/old.jpg"
                            }
                          },
                          "intro": {
                            "paragraphs": ["Old paragraph"]
                          },
                          "approach": {
                            "pillars": [
                              {
                                "number": "01",
                                "title": "Understanding",
                                "description": "Listen first",
                                "unknown": "keep"
                              }
                            ]
                          }
                        }
                        """),
                "content/contact.json", bytes("""
                        {
                          "footer": {
                            "email": "old@example.com"
                          }
                        }
                        """),
                "content/global.json", bytes("""
                        {
                          "brand": {
                            "accentColor": "#D2A84F"
                          }
                        }
                        """)
        );
    }

    private Map<String, byte[]> arbitraryFiles() {
        return Map.of(
                TalliWebsiteSchemaAdapter.SCHEMA_PATH, bytes(arbitrarySchema()),
                "content/landing.json", bytes("""
                        {
                          "hero": {
                            "headline": "A different kind of site"
                          }
                        }
                        """),
                "content/workshops.json", bytes("""
                        {
                          "sessions": [
                            {
                              "name": "Owner training"
                            }
                          ]
                        }
                        """),
                "content/legal/privacy.json", bytes("""
                        {
                          "notice": "Old privacy notice"
                        }
                        """)
        );
    }

    private String schema() {
        return """
                {
                  "version": "talli-editor/v1",
                  "contentFiles": ["content/home.json", "content/contact.json", "content/global.json"],
                  "sections": [
                    {
                      "id": "home",
                      "label": "Home page",
                      "title": "First impression",
                      "icon": "home",
                      "blocks": [
                        {
                          "type": "fields",
                          "layout": "two-column",
                          "fields": [
                            {
                              "id": "homeHeroHeadline",
                              "type": "richText",
                              "label": "Main headline",
                              "marks": ["accent"],
                              "source": {
                                "file": "content/home.json",
                                "path": "/hero/headline"
                              },
                              "required": "Home page main headline is empty."
                            },
                            {
                              "id": "homeIntroParagraphs",
                              "type": "textarea",
                              "label": "Intro paragraphs",
                              "rows": 5,
                              "source": {
                                "file": "content/home.json",
                                "path": "/intro/paragraphs"
                              },
                              "transform": "paragraphs"
                            },
                            {
                              "id": "homeHeroImage",
                              "type": "image",
                              "label": "Hero image",
                              "source": {
                                "file": "content/home.json",
                                "path": "/hero/image/src"
                              },
                              "upload": {
                                "directory": "public/images/cms/{projectId}",
                                "filename": "home-hero",
                                "publicPath": "/images/cms/{projectId}/{filename}.{extension}"
                              }
                            }
                          ]
                        },
                        {
                          "type": "repeat",
                          "id": "homePillars",
                          "title": "Approach pillars",
                          "itemLabel": "Pillar",
                          "addLabel": "Add pillar",
                          "source": {
                            "file": "content/home.json",
                            "path": "/approach/pillars"
                          },
                          "computed": [
                            {
                              "path": "/number",
                              "value": "index",
                              "start": 1,
                              "format": "2-digit"
                            }
                          ],
                          "fields": [
                            {
                              "id": "title",
                              "type": "text",
                              "label": "Title",
                              "path": "/title",
                              "required": "Pillar {number} needs a title."
                            },
                            {
                              "id": "description",
                              "type": "textarea",
                              "label": "Description",
                              "path": "/description"
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "id": "contact",
                      "label": "Contact",
                      "title": "Contact details",
                      "icon": "mail",
                      "blocks": [
                        {
                          "type": "fields",
                          "fields": [
                            {
                              "id": "contactEmail",
                              "type": "email",
                              "label": "Email address",
                              "source": {
                                "file": "content/contact.json",
                                "path": "/footer/email"
                              },
                              "required": "Contact email is empty."
                            },
                            {
                              "id": "brandAccentColor",
                              "type": "color",
                              "label": "Accent color",
                              "source": {
                                "file": "content/global.json",
                                "path": "/brand/accentColor"
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String arbitrarySchema() {
        return """
                {
                  "version": "talli-editor/v1",
                  "contentFiles": [
                    "content/landing.json",
                    "content/workshops.json",
                    "content/legal/privacy.json"
                  ],
                  "sections": [
                    {
                      "id": "landing",
                      "label": "Landing",
                      "blocks": [
                        {
                          "type": "fields",
                          "fields": [
                            {
                              "id": "landingHeadline",
                              "type": "text",
                              "label": "Headline",
                              "source": {
                                "file": "content/landing.json",
                                "path": "/hero/headline"
                              }
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "id": "workshops",
                      "label": "Workshops",
                      "blocks": [
                        {
                          "type": "repeat",
                          "id": "workshopSessions",
                          "title": "Workshop sessions",
                          "itemLabel": "Session",
                          "source": {
                            "file": "content/workshops.json",
                            "path": "/sessions"
                          },
                          "fields": [
                            {
                              "id": "name",
                              "type": "text",
                              "label": "Name",
                              "path": "/name"
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "id": "privacy",
                      "label": "Privacy notice",
                      "blocks": [
                        {
                          "type": "fields",
                          "fields": [
                            {
                              "id": "privacyNotice",
                              "type": "textarea",
                              "label": "Notice",
                              "source": {
                                "file": "content/legal/privacy.json",
                                "path": "/notice"
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String[] one(String value) {
        return new String[] {value};
    }
}
