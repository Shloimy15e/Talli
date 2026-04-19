package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/emails")
public class EmailController {

    private final EmailRepository emailRepository;
    private final ClientRepository clientRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Value("${app.mail.from-name:}")
    private String fromName;

    @Value("${app.mail.from:}")
    private String fromAddress;

    public EmailController(EmailRepository emailRepository,
                           ClientRepository clientRepository,
                           EmailService emailService,
                           UserRepository userRepository) {
        this.emailRepository = emailRepository;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String flow,
                        @RequestParam(required = false) List<String> status,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "created") String sort,
                        @RequestParam(defaultValue = "desc") String direction,
                        Model model) {
        List<String> statuses = status == null ? List.of() : status;
        String q = (search == null) ? "" : search;
        String normalizedFlow = switch (flow == null ? "" : flow) {
            case "in", "out" -> flow;
            default -> "";
        };
        String normalizedSort = switch (sort) {
            case "sent", "subject", "status" -> sort;
            default -> "created";
        };
        String normalizedDir = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";

        var emailPage = emailRepository.findFiltered(
                normalizedFlow, statuses, q, normalizedSort, normalizedDir,
                org.springframework.data.domain.PageRequest.of(page, 25));

        model.addAttribute("emails", emailPage.getContent());
        model.addAttribute("page", emailPage);
        model.addAttribute("filterStatuses", statuses);
        model.addAttribute("filterSearch", search);
        model.addAttribute("filterFlow", normalizedFlow);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("direction", normalizedDir);
        return "emails/index";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Email email = emailRepository.findById(id).orElseThrow();
        model.addAttribute("email", email);
        return "emails/show";
    }

    @GetMapping("/new")
    public String newForm(Authentication auth, Model model) {
        model.addAttribute("email", new Email());
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("users", userRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("signature", currentUserSignature(auth));
        model.addAttribute("emailTemplates", renderedTemplates());
        return "emails/_form :: form";
    }

    /** Substitute the configured sender name/address into every template. */
    private List<ComposeTemplate> renderedTemplates() {
        String name = fromName == null ? "" : fromName;
        String addr = fromAddress == null ? "" : fromAddress;
        return COMPOSE_TEMPLATES.stream()
                .map(t -> new ComposeTemplate(
                        t.id(), t.name(),
                        t.html()
                                .replace("{{fromName}}", name)
                                .replace("{{fromAddress}}", addr)))
                .toList();
    }

    /**
     * An email template is a full HTML document (inline-styled, table-based for
     * email client compatibility) that contains a literal `{{body}}` marker
     * where the user's composed message is injected on preview/send. Templates
     * WRAP user content — they don't replace it.
     */
    public record ComposeTemplate(String id, String name, String html) {}

    // Brand: navy #161f30 header bar + orange #ea7c28 accents, matching invoice/reminder/invite.
    private static final List<ComposeTemplate> COMPOSE_TEMPLATES = List.of(
            new ComposeTemplate("branded", "Branded — standard", """
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
            new ComposeTemplate("branded-notice", "Branded — notice label", """
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
            new ComposeTemplate("formal", "Formal letter", """
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
            new ComposeTemplate("minimal", "Minimal — small footer", """
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

    @PostMapping
    public String send(Authentication auth,
                       @RequestParam(value = "clientId", required = false) Long clientId,
                       @RequestParam("toAddress") String toAddress,
                       @RequestParam("subject") String subject,
                       @RequestParam("body") String body,
                       @RequestParam(value = "bodyHtml", required = false) String bodyHtml,
                       @RequestParam(value = "bccUserId", required = false) List<Long> bccUserIds,
                       @RequestParam(value = "bccManual", required = false) String bccManual) {
        Email email = new Email();
        if (clientId != null) {
            Client client = clientRepository.findById(clientId).orElse(null);
            email.setClient(client);
        }
        email.setToAddress(toAddress);
        email.setSubject(subject);
        email.setBody(body);

        java.util.Set<String> bccSet = new java.util.LinkedHashSet<>();
        if (bccUserIds != null) {
            userRepository.findAllById(bccUserIds).forEach(u -> {
                if (u.getEmail() != null && !u.getEmail().isBlank()) bccSet.add(u.getEmail());
            });
        }
        if (bccManual != null && !bccManual.isBlank()) {
            for (String addr : bccManual.split("[,;\\s]+")) {
                if (!addr.isBlank()) bccSet.add(addr.trim());
            }
        }
        List<String> bcc = bccSet.stream()
                .filter(e -> !e.equalsIgnoreCase(toAddress))
                .toList();
        if (!bcc.isEmpty()) email.setBcc(String.join(", ", bcc));

        // The compose form sends pre-rendered HTML (including any signature
        // or template the user kept). If present, send as HTML. Otherwise
        // fall back to plain-text-with-signature server-side composition for
        // legacy callers that still POST only `body`.
        String htmlToSend = (bodyHtml != null && !bodyHtml.isBlank()) ? bodyHtml : null;
        if (htmlToSend == null) {
            String signature = currentUserSignature(auth);
            if (signature != null && !signature.isBlank()) {
                htmlToSend = "<div>" + EmailService.plainToHtml(body) + "</div>"
                           + "<br><div>" + signature + "</div>";
            }
        }
        if (htmlToSend != null) email.setBodyHtml(htmlToSend);

        try {
            EmailService.Result result = htmlToSend != null
                    ? emailService.sendHtml(toAddress, bcc, subject, body, htmlToSend)
                    : emailService.sendPlain(toAddress, bcc, subject, body);
            email.setResendId(result.resendId());
            email.setStatus("sent");
            email.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            email.setStatus("failed");
            email.setErrorMessage(e.getMessage());
        }

        emailRepository.save(email);
        return "redirect:/emails";
    }

    private String currentUserSignature(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName())
                .map(User::getSignature)
                .orElse(null);
    }
}
