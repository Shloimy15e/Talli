package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateCatalogTest {

    private static final EmailSender PREVIEW_SENDER =
            new EmailSender("billing@dynamiq.dev", "Dynamiq Solutions", "");
    private static final Path PREVIEW_DIRECTORY = Path.of("docs", "email-templates");
    private static final Path PREVIEW_GALLERY = PREVIEW_DIRECTORY.resolve("preview.html");
    private static final Path ROLE_SIGNATURE =
            Path.of("docs", "email-signatures", "role.html");
    private static final String INSTALLABLE_START = "<!-- installable-signature:start -->";
    private static final String INSTALLABLE_END = "<!-- installable-signature:end -->";
    private static final String FIXTURES_START = "  <!-- email-template-fixtures:start -->";
    private static final String FIXTURES_END = "  <!-- email-template-fixtures:end -->";
    private static final String PREVIEW_MESSAGE = """
            <!-- Illustrative fixture only; not template content. -->
            <p style="margin:0 0 18px;">Hello,</p>
            <p style="margin:0 0 18px;">This illustrative message shows the template's spacing, type, links, and current role signature with ordinary correspondence.</p>
            <p style="margin:0 0 26px;"><a href="https://example.com" style="color:#ea7c28; text-decoration:underline; text-underline-offset:3px;">View an example link</a></p>
            """;

    private final EmailTemplateCatalog catalog = new EmailTemplateCatalog(null);

    @Test
    void keepsStableIdsAndLiteralBodyMarkers() {
        List<EmailTemplateCatalog.Template> templates = catalog.all(PREVIEW_SENDER);

        assertThat(templates).extracting(EmailTemplateCatalog.Template::id)
                .containsExactly("branded", "branded-notice", "formal", "minimal");
        assertThat(templates).allSatisfy(template -> assertThat(template.html())
                .contains("{{body}}")
                .doesNotContain("{{fromName}}", "{{fromAddress}}"));
    }

    @Test
    void escapesEverySenderIdentityAndInsertsBodyHtmlUnchanged() {
        EmailSender sender = new EmailSender(
                "billing+ops'@dynamiq.dev", "Dynamiq & <Billing>", "");

        assertThat(catalog.all(sender))
                .filteredOn(template -> List.of("branded", "formal").contains(template.id()))
                .allSatisfy(template -> assertThat(template.html())
                .contains("Dynamiq &amp; &lt;Billing&gt;")
                .contains("billing+ops&#39;@dynamiq.dev")
                .doesNotContain("Dynamiq & <Billing>"));

        assertThat(catalog.all(sender))
                .filteredOn(template -> List.of("branded-notice", "minimal").contains(template.id()))
                .allSatisfy(template -> assertThat(template.html())
                        .doesNotContain("Dynamiq &amp; &lt;Billing&gt;",
                                "billing+ops&#39;@dynamiq.dev"));

        String bodyIdentity = "<p>Dynamiq &amp; &lt;Billing&gt; · billing+ops&#39;@dynamiq.dev</p>";
        for (String templateId : List.of("branded-notice", "minimal")) {
            assertThat(catalog.wrap(templateId, bodyIdentity, sender))
                    .containsOnlyOnce(bodyIdentity);
        }

        String body = "<p data-preview-body>Prepared &amp; ready.</p>";
        assertThat(catalog.wrap("  BRANDED  ", body, sender))
                .contains(body)
                .doesNotContain("{{body}}")
                .contains("mailto:billing+ops&#39;@dynamiq.dev");
    }

    @Test
    void rejectsUnknownTemplateIdsWithTheStableChoices() {
        assertThatThrownBy(() -> catalog.wrap("newsletter", "Body", PREVIEW_SENDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown email template. Use branded, branded-notice, formal, or minimal.");
    }

    @Test
    void usesResponsiveEmailSafeMarkupWithoutRemoteAssets() {
        assertThat(catalog.all(PREVIEW_SENDER)).allSatisfy(template -> assertThat(template.html())
                .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .contains("@media only screen and (max-width:480px)")
                .contains(".dynamiq-email-signature__contact")
                .contains("role=\"presentation\"")
                .contains("width=\"100%\"")
                .contains("max-width:")
                .doesNotContain("<script", "<link", "<img"));
    }

    @Test
    void wrapsBareMessagesWithTheSharedResponsiveSignatureContract() {
        String body = "<p>Message</p><div class=\"dynamiq-email-signature\">Signature</div>";

        assertThat(catalog.wrapBare(body))
                .contains("@media only screen and (max-width:480px)")
                .contains("width:auto !important", "margin:14px 0 0 10px !important")
                .contains("border-left:0 !important", "border-top:1px solid #ea7c28 !important")
                .contains("font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.65")
                .containsOnlyOnce(body);
    }

    @Test
    void previewArtifactsAreRenderedThroughTheCatalogWrapPath() throws IOException {
        List<EmailTemplateCatalog.Template> templates = catalog.all(PREVIEW_SENDER);
        String previewBody = PREVIEW_MESSAGE + installableSignature(Files.readString(ROLE_SIGNATURE))
                .replace("{{role}}", EmailService.plainToHtml("Billing"))
                .replace("{{email}}", EmailService.plainToHtml(PREVIEW_SENDER.address()));
        if (Boolean.getBoolean("emailTemplatePreviews.update")) {
            Files.createDirectories(PREVIEW_DIRECTORY);
            for (EmailTemplateCatalog.Template template : templates) {
                Files.writeString(PREVIEW_DIRECTORY.resolve(template.id() + ".html"),
                        catalog.wrap(template.id(), previewBody, PREVIEW_SENDER));
            }
            Files.writeString(PREVIEW_GALLERY,
                    withEmbeddedPreviews(Files.readString(PREVIEW_GALLERY), templates, previewBody));
        }

        String gallery = Files.readString(PREVIEW_GALLERY);
        for (EmailTemplateCatalog.Template template : templates) {
            Path artifact = PREVIEW_DIRECTORY.resolve(template.id() + ".html");
            String expected = catalog.wrap(template.id(), previewBody, PREVIEW_SENDER);
            assertThat(artifact).exists();
            assertThat(Files.readString(artifact)).isEqualTo(expected);
            assertThat(embeddedPreview(gallery, template.id())).isEqualTo(expected);
        }
    }

    private String withEmbeddedPreviews(String gallery,
                                        List<EmailTemplateCatalog.Template> templates,
                                        String previewBody) {
        int start = gallery.indexOf(FIXTURES_START);
        int end = gallery.indexOf(FIXTURES_END);
        if (start < 0 || end < start) {
            throw new IllegalStateException("Email template gallery fixture markers are missing.");
        }

        StringBuilder fixtures = new StringBuilder(FIXTURES_START).append('\n');
        for (EmailTemplateCatalog.Template template : templates) {
            fixtures.append("<script type=\"text/plain\" id=\"preview-source-")
                    .append(template.id())
                    .append("\">")
                    .append(catalog.wrap(template.id(), previewBody, PREVIEW_SENDER))
                    .append("</script>\n");
        }
        fixtures.append(FIXTURES_END);
        return gallery.substring(0, start) + fixtures
                + gallery.substring(end + FIXTURES_END.length());
    }

    private static String embeddedPreview(String gallery, String templateId) {
        String openingTag = "<script type=\"text/plain\" id=\"preview-source-"
                + templateId + "\">";
        int start = gallery.indexOf(openingTag);
        if (start < 0) return "";
        start += openingTag.length();
        int end = gallery.indexOf("</script>", start);
        return end < 0 ? "" : gallery.substring(start, end);
    }

    private static String installableSignature(String standaloneSignature) {
        int start = standaloneSignature.indexOf(INSTALLABLE_START);
        int end = standaloneSignature.indexOf(INSTALLABLE_END, start + INSTALLABLE_START.length());
        if (start < 0 || end < start) return "";
        return standaloneSignature.substring(start + INSTALLABLE_START.length(), end).trim();
    }
}
