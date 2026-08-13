package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailAttachmentPolicy;
import dev.dynamiq.talli.service.EmailTemplateCatalog;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.service.MediaService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
@RequestMapping("/emails")
public class EmailController {

    private final EmailRepository emailRepository;
    private final ClientRepository clientRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final EmailAttachmentPolicy attachmentPolicy;
    private final EmailTemplateCatalog emailTemplates;

    public EmailController(EmailRepository emailRepository,
                           ClientRepository clientRepository,
                           EmailService emailService,
                           UserRepository userRepository,
                           MediaService mediaService,
                           EmailAttachmentPolicy attachmentPolicy,
                           EmailTemplateCatalog emailTemplates) {
        this.emailRepository = emailRepository;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.mediaService = mediaService;
        this.attachmentPolicy = attachmentPolicy;
        this.emailTemplates = emailTemplates;
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
        model.addAttribute("attachments", mediaService.forOwner(email, "attachments"));
        return "emails/show";
    }

    @GetMapping("/new")
    public String newForm(Authentication auth, Model model) {
        model.addAttribute("email", new Email());
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("users", userRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("signature", currentUserSignature(auth));
        model.addAttribute("emailTemplates", emailTemplates.all());
        model.addAttribute("maxAttachmentFileBytes", attachmentPolicy.maxFileBytes());
        model.addAttribute("maxAttachmentTotalBytes", attachmentPolicy.maxTotalBytes());
        model.addAttribute("attachmentLimitDescription", attachmentPolicy.limitDescription());
        return "emails/_form :: form";
    }

    @PostMapping
    public String send(Authentication auth,
                       @RequestParam(value = "clientId", required = false) Long clientId,
                       @RequestParam("toAddress") String toAddress,
                       @RequestParam("subject") String subject,
                       @RequestParam("body") String body,
                       @RequestParam(value = "bodyHtml", required = false) String bodyHtml,
                       @RequestParam(value = "ccUserId", required = false) List<Long> ccUserIds,
                       @RequestParam(value = "ccManual", required = false) String ccManual,
                       @RequestParam(value = "bccUserId", required = false) List<Long> bccUserIds,
                       @RequestParam(value = "bccManual", required = false) String bccManual,
                       @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments,
                       RedirectAttributes redirectAttributes) {
        var attachmentError = attachmentPolicy.validationError(attachments);
        if (attachmentError.isPresent()) {
            redirectAttributes.addFlashAttribute("error", attachmentError.get());
            return "redirect:/emails";
        }

        Email email = new Email();
        if (clientId != null) {
            Client client = clientRepository.findById(clientId).orElse(null);
            email.setClient(client);
        }
        email.setToAddress(toAddress);
        email.setSubject(subject);
        email.setBody(body);

        List<String> cc = recipients(ccUserIds, ccManual, toAddress, List.of());
        List<String> bcc = recipients(bccUserIds, bccManual, toAddress, cc);
        if (!cc.isEmpty()) email.setCc(String.join(", ", cc));
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

        email = emailRepository.save(email);

        try {
            List<EmailService.Attachment> outboundAttachments = storeAttachments(email, attachments);
            EmailService.Result result = htmlToSend != null
                    ? emailService.sendHtml(toAddress, cc, bcc, subject, body, htmlToSend, outboundAttachments)
                    : emailService.sendPlain(toAddress, cc, bcc, subject, body, outboundAttachments);
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

    private List<EmailService.Attachment> storeAttachments(Email email, List<MultipartFile> files) {
        if (files == null) return List.of();

        return files.stream()
                .filter(file -> !file.isEmpty())
                .map(file -> {
                    var media = mediaService.attach(email, file, "attachments");
                    return new EmailService.Attachment(
                            media.getFilename(), mediaService.loadBytes(media), media.getMimeType());
                })
                .toList();
    }

    private List<String> recipients(List<Long> userIds, String manual, String toAddress,
                                    List<String> excluded) {
        LinkedHashMap<String, String> addresses = new LinkedHashMap<>();
        if (userIds != null) {
            userRepository.findAllById(userIds).forEach(user -> addAddress(addresses, user.getEmail()));
        }
        if (manual != null && !manual.isBlank()) {
            for (String address : manual.split("[,;\\s]+")) addAddress(addresses, address);
        }
        addresses.remove(toAddress.toLowerCase(java.util.Locale.ROOT));
        excluded.forEach(address -> addresses.remove(address.toLowerCase(java.util.Locale.ROOT)));
        return List.copyOf(addresses.values());
    }

    private static void addAddress(LinkedHashMap<String, String> addresses, String address) {
        if (address == null || address.isBlank()) return;
        String trimmed = address.trim();
        addresses.putIfAbsent(trimmed.toLowerCase(java.util.Locale.ROOT), trimmed);
    }

    private String currentUserSignature(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName())
                .map(User::getSignature)
                .orElse(null);
    }
}
