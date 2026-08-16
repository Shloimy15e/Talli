package dev.dynamiq.talli.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Shared catalog for the optional presentation templates used by composed emails. */
@Service
public class EmailTemplateCatalog {

    private final String fromName;
    private final String fromAddress;

    public EmailTemplateCatalog(@Value("${app.mail.from-name:}") String fromName,
                                @Value("${app.mail.from:}") String fromAddress) {
        this.fromName = fromName;
        this.fromAddress = fromAddress;
    }

    /** Templates retain the body marker because the browser composer replaces it client-side. */
    public List<Template> all() {
        return TEMPLATES.stream()
                .map(template -> withSender(template, fromName, fromAddress))
                .toList();
    }

    /** Wrap escaped, composed body HTML in one of the known templates. */
    public String wrap(String templateId, String bodyHtml) {
        return wrap(templateId, bodyHtml, fromName, fromAddress);
    }

    /** Wrap body HTML using the sender selected for this specific email. */
    String wrap(String templateId, String bodyHtml, EmailSender sender) {
        return wrap(templateId, bodyHtml, sender.name(), sender.address());
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
                .replace("{{fromName}}", senderName == null ? "" : senderName)
                .replace("{{fromAddress}}", senderAddress == null ? "" : senderAddress));
    }

    /** A full email-safe HTML document with a literal {@code {{body}}} insertion point. */
    public record Template(String id, String name, String html) {}

    // Brand: navy #161f30 header bar + orange #ea7c28 accents, matching invoice/reminder/invite.
    private static final List<Template> TEMPLATES = List.of(
            new Template("branded", "Branded — standard", """
                    <!doctype html>
                    <html><head><meta charset="utf-8"></head>
                    <body style="margin:0; padding:0; background:#f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; color:#1a1a1a;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f1f5f9;">
                        <tr><td align="center" style="padding:32px 16px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="600" style="max-width:600px; width:100%; background:#ffffff; border-radius:8px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,0.05);">
                            <tr><td style="background:#161f30; padding:20px 32px;">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
                                <tr><td style="color:#ffffff; font-size:18px; font-weight:600; letter-spacing:-0.01em;">{{fromName}}</td></tr>
                              </table>
                            </td></tr>
                            <tr><td style="padding:32px; font-size:15px; line-height:1.6; color:#1a1a1a;">
                              {{body}}
                            </td></tr>
                            <tr><td style="padding:20px 32px; background:#f8fafc; border-top:1px solid #e2e8f0; text-align:center;">
                              <p style="margin:0; font-size:12px; color:#94a3b8; line-height:1.5;">
                                {{fromName}} <span style="color:#cbd5e1;">&middot;</span>
                                <a href="mailto:{{fromAddress}}" style="color:#94a3b8; text-decoration:none;">{{fromAddress}}</a>
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """),
            new Template("branded-notice", "Branded — notice label", """
                    <!doctype html>
                    <html><head><meta charset="utf-8"></head>
                    <body style="margin:0; padding:0; background:#f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; color:#1a1a1a;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background:#f1f5f9;">
                        <tr><td align="center" style="padding:32px 16px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="600" style="max-width:600px; width:100%; background:#ffffff; border-radius:8px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,0.05);">
                            <tr><td style="background:#161f30; padding:20px 32px;">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
                                <tr>
                                  <td style="color:#ffffff; font-size:18px; font-weight:600; letter-spacing:-0.01em;">{{fromName}}</td>
                                  <td align="right" style="color:#ea7c28; font-size:12px; text-transform:uppercase; letter-spacing:0.08em; font-weight:600;">Notice</td>
                                </tr>
                              </table>
                            </td></tr>
                            <tr><td style="padding:32px; font-size:15px; line-height:1.6; color:#1a1a1a;">
                              {{body}}
                            </td></tr>
                            <tr><td style="padding:20px 32px; background:#f8fafc; border-top:1px solid #e2e8f0; text-align:center;">
                              <p style="margin:0; font-size:12px; color:#94a3b8; line-height:1.5;">
                                {{fromName}} <span style="color:#cbd5e1;">&middot;</span>
                                <a href="mailto:{{fromAddress}}" style="color:#94a3b8; text-decoration:none;">{{fromAddress}}</a>
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """),
            new Template("formal", "Formal letter", """
                    <!doctype html>
                    <html><head><meta charset="utf-8"></head>
                    <body style="margin:0; padding:0; background:#ffffff; font-family: Georgia, 'Times New Roman', serif; color:#1a1a1a;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="padding:56px 16px;">
                        <tr><td align="center">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="620" style="max-width:620px; width:100%;">
                            <tr><td style="border-bottom:3px solid #161f30; padding:0 0 14px;">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
                                <tr>
                                  <td style="color:#161f30; font-size:22px; font-weight:600; letter-spacing:-0.01em;">{{fromName}}</td>
                                  <td align="right" style="color:#ea7c28; font-size:11px; text-transform:uppercase; letter-spacing:0.08em; font-weight:600;">{{fromAddress}}</td>
                                </tr>
                              </table>
                            </td></tr>
                            <tr><td style="padding:36px 0 0; font-size:15px; line-height:1.85; color:#1f2937;">
                              {{body}}
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """),
            new Template("minimal", "Minimal — small footer", """
                    <!doctype html>
                    <html><head><meta charset="utf-8"></head>
                    <body style="margin:0; padding:0; background:#ffffff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif; color:#1a1a1a;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="padding:48px 16px;">
                        <tr><td align="center">
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="560" style="max-width:560px; width:100%;">
                            <tr><td style="font-size:15px; line-height:1.75; color:#1f2937;">
                              {{body}}
                            </td></tr>
                            <tr><td style="padding:28px 0 0; border-top:1px solid #e2e8f0;">
                              <p style="margin:16px 0 0; font-size:12px; color:#94a3b8; line-height:1.5;">
                                {{fromName}} <span style="color:#cbd5e1;">&middot;</span>
                                <a href="mailto:{{fromAddress}}" style="color:#94a3b8; text-decoration:none;">{{fromAddress}}</a>
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body></html>
                    """)
    );
}
