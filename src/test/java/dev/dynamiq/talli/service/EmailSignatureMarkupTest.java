package dev.dynamiq.talli.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSignatureMarkupTest {

    private static final Path SIGNATURE_DIRECTORY = Path.of("docs", "email-signatures");
    private static final Path PERSONAL_SIGNATURE = SIGNATURE_DIRECTORY.resolve("signature.html");
    private static final Path ROLE_SIGNATURE = SIGNATURE_DIRECTORY.resolve("role.html");
    private static final Path PREVIEW = SIGNATURE_DIRECTORY.resolve("preview.html");
    private static final Path INVOICE_TEMPLATE = Path.of("src", "main", "resources", "templates", "emails", "invoice.html");
    private static final Path REMINDER_TEMPLATE = Path.of("src", "main", "resources", "templates", "emails", "reminder.html");
    private static final String LOGO_TEMPLATE_START = "  <template id=\"logo-source\">";
    private static final String LOGO_TEMPLATE_END = "  </template>";
    private static final String INSTALLABLE_START = "<!-- installable-signature:start -->";
    private static final String INSTALLABLE_END = "<!-- installable-signature:end -->";
    private static final String FALLBACK_START = "<!-- billing-role-fallback:start -->";
    private static final String FALLBACK_END = "<!-- billing-role-fallback:end -->";

    @Test
    void personalAndRoleSignaturesStackWithoutNarrowingTheContactBlock() throws IOException {
        for (Path signature : new Path[]{PERSONAL_SIGNATURE, ROLE_SIGNATURE}) {
            assertThat(Files.readString(signature))
                    .contains("width:100%;max-width:460px")
                    .contains("padding:0;font-size:0;line-height:0;text-align:left")
                    .contains("display:inline-block;box-sizing:border-box;width:170px;max-width:100%")
                    .contains("display:inline-block;width:14px")
                    .contains("display:inline-block;box-sizing:border-box;width:245px;max-width:100%;padding-left:14px;border-left:1px solid #ea7c28")
                    .contains("class=\"dynamiq-email-signature\"")
                    .contains("class=\"dynamiq-email-signature__contact\"")
                    .contains("display:inline-block !important; width:auto !important; max-width:100% !important")
                    .contains("margin:14px 0 0 10px !important")
                    .contains("border-left:0 !important; border-top:1px solid #ea7c28 !important")
                    .contains("white-space:nowrap")
                    .contains("color:#ea7c28")
                    .doesNotContain("table-layout:fixed", "word-break:");

            assertThat(installableSignature(Files.readString(signature)))
                    .startsWith("<table class=\"dynamiq-email-signature\"")
                    .doesNotContain("<style>", INSTALLABLE_START, INSTALLABLE_END);
        }
    }

    @Test
    void previewEmbedsTheExactPersonalSignatureFragment() throws IOException {
        String signature = Files.readString(PERSONAL_SIGNATURE).trim();
        if (Boolean.getBoolean("emailSignaturePreviews.update")) {
            Files.writeString(PREVIEW, withPersonalSignature(Files.readString(PREVIEW), signature));
        }

        assertThat(embeddedPersonalSignature(Files.readString(PREVIEW))).isEqualTo(signature);
    }

    @Test
    void transactionalNaturalPreviewsUseTheInstallableBillingRoleFragment() throws IOException {
        String billing = installableSignature(Files.readString(ROLE_SIGNATURE))
                .replace("{{role}}", EmailService.plainToHtml("Billing"))
                .replace("{{email}}", EmailService.plainToHtml("billing@dynamiq.dev"));

        for (Path template : new Path[]{INVOICE_TEMPLATE, REMINDER_TEMPLATE}) {
            if (Boolean.getBoolean("emailSignaturePreviews.update")) {
                Files.writeString(template, withBillingFallback(Files.readString(template), billing));
            }
            String templateHtml = Files.readString(template);
            assertThat(embeddedBillingFallback(templateHtml)).isEqualTo(billing);
            assertThat(templateHtml)
                    .contains("@media only screen and (max-width:480px)")
                    .contains("width:auto !important", "margin:14px 0 0 10px !important")
                    .contains("border-left:0 !important; border-top:1px solid #ea7c28 !important");
        }
    }

    private static String withPersonalSignature(String preview, String signature) {
        int start = preview.indexOf(LOGO_TEMPLATE_START);
        int end = preview.indexOf(LOGO_TEMPLATE_END, start + LOGO_TEMPLATE_START.length());
        if (start < 0 || end < start) {
            throw new IllegalStateException("Personal signature preview markers are missing.");
        }
        int contentStart = start + LOGO_TEMPLATE_START.length();
        return preview.substring(0, contentStart) + '\n' + signature + '\n'
                + preview.substring(end);
    }

    private static String embeddedPersonalSignature(String preview) {
        int start = preview.indexOf(LOGO_TEMPLATE_START);
        int end = preview.indexOf(LOGO_TEMPLATE_END, start + LOGO_TEMPLATE_START.length());
        if (start < 0 || end < start) return "";
        return preview.substring(start + LOGO_TEMPLATE_START.length(), end).trim();
    }

    private static String installableSignature(String standaloneSignature) {
        int start = standaloneSignature.indexOf(INSTALLABLE_START);
        int end = standaloneSignature.indexOf(INSTALLABLE_END, start + INSTALLABLE_START.length());
        if (start < 0 || end < start) return "";
        return standaloneSignature.substring(start + INSTALLABLE_START.length(), end).trim();
    }

    private static String withBillingFallback(String template, String billing) {
        int start = template.indexOf(FALLBACK_START);
        int end = template.indexOf(FALLBACK_END, start + FALLBACK_START.length());
        if (start < 0 || end < start) {
            throw new IllegalStateException("Transactional email Billing fallback markers are missing.");
        }
        int contentStart = start + FALLBACK_START.length();
        return template.substring(0, contentStart) + '\n' + billing + '\n'
                + template.substring(end);
    }

    private static String embeddedBillingFallback(String template) {
        int start = template.indexOf(FALLBACK_START);
        int end = template.indexOf(FALLBACK_END, start + FALLBACK_START.length());
        if (start < 0 || end < start) return "";
        return template.substring(start + FALLBACK_START.length(), end).trim();
    }
}
