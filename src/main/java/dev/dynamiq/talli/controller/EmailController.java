package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailAttachmentPolicy;
import dev.dynamiq.talli.service.EmailTemplateCatalog;
import dev.dynamiq.talli.service.EmailService;
import dev.dynamiq.talli.service.EmailSender;
import dev.dynamiq.talli.service.EmailSenderProfileService;
import dev.dynamiq.talli.service.EmailThreadService;
import dev.dynamiq.talli.service.MailboxService;
import dev.dynamiq.talli.service.MediaService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

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
    private final EmailSenderProfileService senders;
    private final EmailThreadService threads;
    private final MailboxService mailbox;

    public EmailController(EmailRepository emailRepository,
                           ClientRepository clientRepository,
                           EmailService emailService,
                           UserRepository userRepository,
                           MediaService mediaService,
                           EmailAttachmentPolicy attachmentPolicy,
                           EmailTemplateCatalog emailTemplates,
                           EmailSenderProfileService senders,
                           EmailThreadService threads,
                           MailboxService mailbox) {
        this.emailRepository = emailRepository;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.mediaService = mediaService;
        this.attachmentPolicy = attachmentPolicy;
        this.emailTemplates = emailTemplates;
        this.senders = senders;
        this.threads = threads;
        this.mailbox = mailbox;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "all") String folder,
                        @RequestParam(required = false) String flow,
                        @RequestParam(required = false) List<String> status,
                        @RequestParam(required = false) String search,
                        Authentication auth,
                        Model model) {
        var user = mailbox.currentUser(auth);
        addMailboxModel(model, mailbox.mailbox(user, folder, search, page, flow, status));
        model.addAttribute("selectedEmail", null);
        model.addAttribute("selectedRootId", null);
        return "emails/index";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "all") String folder,
                       @RequestParam(required = false) String flow,
                       @RequestParam(required = false) List<String> status,
                       @RequestParam(required = false) String search,
                       Authentication auth,
                       Model model) {
        var user = mailbox.currentUser(auth);
        var selected = mailbox.conversation(user, id, true);
        addMailboxModel(model, mailbox.mailbox(user, folder, search, page, flow, status));

        LinkedHashMap<Long, List<dev.dynamiq.talli.model.Media>> attachmentsByEmailId = new LinkedHashMap<>();
        LinkedHashMap<Long, Boolean> canReplyByEmailId = new LinkedHashMap<>();
        Long replyId = null;
        for (Email message : selected.conversation()) {
            attachmentsByEmailId.put(message.getId(), mediaService.forOwner(message, "attachments"));
            try {
                threads.replyContext(message.getId());
                canReplyByEmailId.put(message.getId(), true);
                replyId = message.getId();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                canReplyByEmailId.put(message.getId(), false);
            }
        }

        model.addAttribute("selectedEmail", selected.selectedEmail());
        model.addAttribute("selectedRootId", selected.rootId());
        model.addAttribute("conversation", selected.conversation());
        model.addAttribute("selectedStarred", selected.starred());
        model.addAttribute("selectedArchived", selected.archived());
        model.addAttribute("attachmentsByEmailId", attachmentsByEmailId);
        model.addAttribute("canReplyByEmailId", canReplyByEmailId);
        model.addAttribute("replyId", replyId);
        if (replyId == null) {
            model.addAttribute("replyUnavailableReason",
                    "This saved conversation does not have a valid reply recipient.");
        }
        return "emails/index";
    }

    @PostMapping("/{id}/mailbox")
    public String updateMailbox(@PathVariable Long id,
                                @RequestParam String action,
                                @RequestParam(defaultValue = "all") String folder,
                                @RequestParam(required = false) String search,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "false") boolean returnToConversation,
                                Authentication auth) {
        mailbox.update(mailbox.currentUser(auth), id, action);
        String path = returnToConversation ? "/emails/{id}" : "/emails";
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath(path)
                .queryParam("folder", MailboxService.normalizeFolder(folder))
                .queryParam("page", Math.max(page, 0));
        String normalizedSearch = MailboxService.normalizeSearch(search);
        if (!normalizedSearch.isEmpty()) redirect.queryParam("search", "{search}");
        return "redirect:" + redirect.encode().buildAndExpand(java.util.Map.of("id", id, "search", normalizedSearch)).toUriString();
    }

    @GetMapping("/new")
    public String newForm(Authentication auth, @RequestParam(required = false) Long replyToEmailId, Model model) {
        Email draft = new Email();
        EmailSender sender = senders.resolve(null);
        boolean providerThreadedReply = false;
        if (replyToEmailId != null) {
            var reply = threads.replyContext(replyToEmailId);
            draft.setToAddress(reply.recipientAddress());
            draft.setCc(reply.ccAddresses());
            draft.setBcc(reply.bccAddresses());
            draft.setSubject(reply.subject());
            draft.setClient(emailRepository.findById(replyToEmailId).orElseThrow().getClient());
            providerThreadedReply = reply.providerThreaded();
            try { sender = senders.resolve(reply.senderAddressHint()); }
            catch (IllegalArgumentException ignored) { /* A retired sender can be replaced by an active profile. */ }
        }
        model.addAttribute("email", draft);
        model.addAttribute("replyToEmailId", replyToEmailId);
        model.addAttribute("providerThreadedReply", providerThreadedReply);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("users", userRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("signature", sender.signatureHtml());
        model.addAttribute("senderProfiles", senders.options());
        model.addAttribute("senderEmail", sender.address());
        model.addAttribute("emailTemplates", emailTemplates.all(sender));
        model.addAttribute("maxAttachmentFileBytes", attachmentPolicy.maxFileBytes());
        model.addAttribute("maxAttachmentTotalBytes", attachmentPolicy.maxTotalBytes());
        model.addAttribute("attachmentLimitDescription", attachmentPolicy.limitDescription());
        return "emails/_form :: form";
    }

    @GetMapping("/sender-templates")
    @ResponseBody
    public List<EmailTemplateCatalog.Template> senderTemplates(@RequestParam String senderEmail) {
        return emailTemplates.all(senders.resolve(senderEmail));
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
                       @RequestParam(value = "senderEmail", required = false) String senderEmail,
                       @RequestParam(value = "replyToEmailId", required = false) Long replyToEmailId,
                       RedirectAttributes redirectAttributes) {
        var attachmentError = attachmentPolicy.validationError(attachments);
        if (attachmentError.isPresent()) {
            redirectAttributes.addFlashAttribute("error", attachmentError.get());
            return "redirect:/emails";
        }

        EmailSender sender;
        EmailThreadService.ReplyContext reply;
        try {
            sender = senders.resolve(senderEmail);
            reply = replyToEmailId == null ? null : threads.replyContext(replyToEmailId);
            if (reply != null && reply.providerThreaded()
                    && !reply.recipientAddress().equalsIgnoreCase(toAddress.trim())) {
                throw new IllegalArgumentException("Reply recipient must match the original conversation.");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/emails";
        }

        Email email = new Email();
        email.setFromAddress(sender.address());
        if (reply != null) {
            email.setClient(emailRepository.findById(replyToEmailId).orElseThrow().getClient());
            email.setThreadRootId(reply.threadRootId());
            email.setInReplyTo(reply.inReplyTo());
            email.setReferencesHeader(reply.referencesHeader());
            if (reply.providerThreaded()) subject = reply.subject();
        } else if (clientId != null) {
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
            String signature = sender.signatureHtml();
            if (signature != null && !signature.isBlank()) {
                htmlToSend = "<div>" + EmailService.plainToHtml(body) + "</div>"
                           + "<br><div>" + signature + "</div>";
                htmlToSend = emailTemplates.wrapBare(htmlToSend);
            }
        }
        if (htmlToSend != null) email.setBodyHtml(htmlToSend);

        email = emailRepository.save(email);

        try {
            List<EmailService.Attachment> outboundAttachments = storeAttachments(email, attachments);
            EmailService.Result result = emailService.sendMessage(sender, toAddress, cc, bcc,
                    subject, body, htmlToSend, outboundAttachments,
                    reply == null || !reply.providerThreaded() ? java.util.Map.of()
                            : java.util.Map.of("In-Reply-To", reply.inReplyTo(), "References", reply.referencesHeader()));
            email.setResendId(result.resendId());
            email.setMessageId(result.messageId());
            email.setStatus("sent");
            email.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            email.setStatus("failed");
            email.setErrorMessage(e.getMessage());
        }

        if (email.getThreadRootId() == null) email.setThreadRootId(email.getId());
        emailRepository.save(email);
        if ("sent".equals(email.getStatus())) {
            redirectAttributes.addFlashAttribute("success", "Message sent.");
            if (auth != null) {
                redirectAttributes.addFlashAttribute("sentDraftKey", "talli:mail-draft:v1:"
                        + auth.getName() + ":" + (replyToEmailId == null ? "new" : replyToEmailId));
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "This message wasn't sent. Your draft is still available.");
        }
        return reply == null ? "redirect:/emails" : "redirect:/emails/" + email.getId();
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

    private static void addMailboxModel(Model model, MailboxService.MailboxView mailbox) {
        model.addAttribute("mailRows", mailbox.mailRows());
        model.addAttribute("folderCounts", mailbox.folderCounts());
        model.addAttribute("folder", mailbox.folder());
        model.addAttribute("search", mailbox.search());
        model.addAttribute("page", mailbox.mailRows().getNumber());
    }

}
