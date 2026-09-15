package dev.dynamiq.talli.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Shared catalog for the optional presentation templates used by composed emails. */
@Service
public class EmailTemplateCatalog {

    private final EmailSenderProfileService senders;

    public EmailTemplateCatalog(EmailSenderProfileService senders) {
        this.senders = senders;
    }

    /** Templates retain the body marker because the browser composer replaces it client-side. */
    public List<Template> all() {
        return all(senders.resolve(null));
    }

    public List<Template> all(EmailSender sender) {
        return TEMPLATES.stream()
                .map(template -> withSender(template, sender.name(), sender.address()))
                .toList();
    }

    /** Wrap escaped, composed body HTML in one of the known templates. */
    public String wrap(String templateId, String bodyHtml) {
        return wrap(templateId, bodyHtml, senders.resolve(null));
    }

    /** Wrap body HTML using the sender selected for this specific email. */
    public String wrap(String templateId, String bodyHtml, EmailSender sender) {
        return wrap(templateId, bodyHtml, sender.name(), sender.address());
    }

    /** Wrap a message without a presentation template while retaining shared email behavior. */
    public String wrapBare(String bodyHtml) {
        return "<style>" + signatureStyles() + "</style>\n"
                + "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.65\">"
                + bodyHtml + "</div>";
    }

    private String wrap(String templateId, String bodyHtml,
                        String senderName, String senderAddress) {
        String id = templateId == null ? "" : templateId.trim().toLowerCase(Locale.ROOT);
        Template template = TEMPLATES.stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown email template. Use branded, branded-notice, formal, or minimal."));
        return withSender(template, senderName, senderAddress).html()
                .replace("{{body}}", bodyHtml);
    }

    private static Template withSender(Template template, String senderName,
                                       String senderAddress) {
        return new Template(template.id(), template.name(), template.html()
                .replace("{{fromName}}", EmailService.plainToHtml(senderName))
                .replace("{{fromAddress}}", EmailService.plainToHtml(senderAddress)));
    }

    /** A full email-safe HTML document with a literal {@code {{body}}} insertion point. */
    public record Template(String id, String name, String html) {}

    private static final String RESPONSIVE_SIGNATURE_CSS = """
            @media only screen and (max-width:480px) {
              .dynamiq-email-signature .dynamiq-email-signature__logo { display:block !important; width:100% !important; max-width:100% !important; height:44px !important; overflow:hidden !important; }
              .dynamiq-email-signature .dynamiq-email-signature__logo img { margin-top:-36px !important; }
              .dynamiq-email-signature .dynamiq-email-signature__spacer { display:none !important; width:0 !important; height:0 !important; }
              .dynamiq-email-signature .dynamiq-email-signature__contact { display:inline-block !important; width:auto !important; max-width:100% !important; margin:14px 0 0 10px !important; padding:13px 0 0 !important; border-left:0 !important; border-top:1px solid #ea7c28 !important; }
            }
            """.strip();

    public static String signatureStyles() {
        return RESPONSIVE_SIGNATURE_CSS;
    }

    private static Template template(String id, String name, String html) {
        return new Template(id, name,
                html.replace("{{signatureResponsiveStyles}}",
                        "<style>" + signatureStyles() + "</style>"));
    }

    // Quiet, email-safe correspondence layouts shared by the browser and MCP composers.
    private static final List<Template> TEMPLATES = List.of(
            template("branded", "Branded correspondence", """
                    <!doctype html>
                    <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">{{signatureResponsiveStyles}}</head>
                    <body style="margin:0; padding:0; background:#f5f6f8; font-family:Arial, Helvetica, sans-serif; color:#202936;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; background:#f5f6f8;">
                        <tr><td align="center" style="padding:40px 12px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; max-width:600px; background:#ffffff; border:1px solid #cfd5dc; border-radius:10px; overflow:hidden;">
                            <tr><td style="padding:29px 32px 24px;">
                              <p style="margin:0; color:#141c34; font-size:19px; font-weight:600; line-height:1.35; letter-spacing:-0.02em;">{{fromName}}</p>
                              <p style="margin:7px 0 0; color:#637083; font-size:13px; line-height:1.5; word-break:break-word;">
                                <a href="mailto:{{fromAddress}}" style="color:#637083; text-decoration:none;">{{fromAddress}}</a>
                              </p>
                            </td></tr>
                            <tr><td style="padding:30px 32px 34px; border-top:1px solid #e5e9ee; color:#202936; font-size:15px; line-height:1.75; word-break:break-word;">
                              {{body}}
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """),
            template("branded-notice", "Branded notice", """
                    <!doctype html>
                    <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">{{signatureResponsiveStyles}}</head>
                    <body style="margin:0; padding:0; background:#f5f6f8; font-family:Arial, Helvetica, sans-serif; color:#202936;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; background:#f5f6f8;">
                        <tr><td align="center" style="padding:40px 12px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; max-width:600px; background:#ffffff; border:1px solid #cfd5dc; border-radius:10px; overflow:hidden;">
                            <tr><td style="padding:32px 32px 34px; color:#202936; font-size:15px; line-height:1.75; word-break:break-word;">
                              {{body}}
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """),
            template("formal", "Formal letter", """
                    <!doctype html>
                    <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">{{signatureResponsiveStyles}}</head>
                    <body style="margin:0; padding:0; background:#ffffff; font-family:Arial, Helvetica, sans-serif; color:#202936;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; background:#ffffff;">
                        <tr><td align="center" style="padding:52px 16px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; max-width:620px;">
                            <tr><td style="padding:0 0 24px; border-bottom:1px solid #cfd5dc;">
                              <p style="margin:0; color:#141c34; font-size:21px; font-weight:600; line-height:1.35; letter-spacing:-0.025em;">{{fromName}}</p>
                              <p style="margin:7px 0 0; color:#637083; font-size:13px; line-height:1.5; word-break:break-word;">
                                <a href="mailto:{{fromAddress}}" style="color:#637083; text-decoration:none;">{{fromAddress}}</a>
                              </p>
                            </td></tr>
                            <tr><td style="padding:36px 0 0; color:#202936; font-size:16px; line-height:1.8; word-break:break-word;">
                              {{body}}
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """),
            template("minimal", "Minimal", """
                    <!doctype html>
                    <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">{{signatureResponsiveStyles}}</head>
                    <body style="margin:0; padding:0; background:#ffffff; font-family:Arial, Helvetica, sans-serif; color:#202936;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; background:#ffffff;">
                        <tr><td align="center" style="padding:44px 16px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%; max-width:560px;">
                            <tr><td style="color:#202936; font-size:15px; line-height:1.75; word-break:break-word;">
                              {{body}}
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """)
    );
}
