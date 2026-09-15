package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.service.EmailSenderProfileService;
import dev.dynamiq.talli.service.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequestMapping("/emails/senders")
public class EmailSenderProfileController {

    private final EmailSenderProfileService profiles;

    public EmailSenderProfileController(EmailSenderProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public String index(Model model) {
        var allProfiles = profiles.allProfiles();
        model.addAttribute("profiles", allProfiles);
        model.addAttribute("activeProfileCount", allProfiles.stream()
                .filter(profile -> profile.isActive())
                .count());
        return "emails/senders/index";
    }

    @PostMapping
    public String create(@RequestParam String address,
                         @RequestParam String name,
                         @RequestParam(required = false) String signatureHtml,
                         @RequestParam(defaultValue = "text") String signatureFormat,
                         @RequestParam(defaultValue = "false") boolean defaultSender,
                         RedirectAttributes flash) {
        try {
            profiles.create(address, name, signatureHtml(signatureHtml, signatureFormat), defaultSender);
            flash.addFlashAttribute("success", "Sender profile added.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
            flash.addFlashAttribute("draft", new Draft(
                    address, name, signatureHtml, signatureFormat, defaultSender, true));
        }
        return "redirect:/emails/senders";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("profile", profiles.findRequired(id));
        return "emails/senders/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String address,
                         @RequestParam String name,
                         @RequestParam(required = false) String signatureHtml,
                         @RequestParam(defaultValue = "html") String signatureFormat,
                         @RequestParam(defaultValue = "false") boolean defaultSender,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes flash) {
        try {
            profiles.update(id, address, name, signatureHtml(signatureHtml, signatureFormat),
                    defaultSender, active);
            flash.addFlashAttribute("success", "Sender profile updated.");
            return "redirect:/emails/senders";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
            flash.addFlashAttribute("draft", new Draft(
                    address, name, signatureHtml, signatureFormat, defaultSender, active));
            return "redirect:/emails/senders/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/disable")
    public String disable(@PathVariable Long id, RedirectAttributes flash) {
        try {
            profiles.disable(id);
            flash.addFlashAttribute("success", "Sender profile disabled.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/emails/senders";
    }

    private static String signatureHtml(String signature, String format) {
        if (signature == null || signature.isBlank()) return signature;
        return switch (format == null ? "" : format.trim().toLowerCase(Locale.ROOT)) {
            case "text" -> EmailService.plainToHtml(signature);
            case "html" -> signature;
            default -> throw new IllegalArgumentException("Choose Text or HTML for the signature format.");
        };
    }

    public record Draft(String address, String name, String signatureHtml,
                        String signatureFormat, boolean defaultSender, boolean active) {}
}
