package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.ApiTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiTokenService apiTokenService;

    public ProfileController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                             ApiTokenService apiTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.apiTokenService = apiTokenService;
    }

    @GetMapping
    public String profile(Authentication auth, Model model) {
        User user = currentUser(auth);
        model.addAttribute("user", user);
        model.addAttribute("tokens", apiTokenService.tokensForUser(user.getId()));
        return "profile/index";
    }

    @PostMapping
    public String updateProfile(Authentication auth,
                                @RequestParam("name") String name,
                                @RequestParam("email") String email,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(auth);
        user.setName(name);
        user.setEmail(email);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("message", "Profile updated.");
        return "redirect:/profile";
    }

    @PostMapping("/signature")
    public String updateSignature(Authentication auth,
                                  @RequestParam(value = "signature", required = false) String signature,
                                  RedirectAttributes redirectAttributes) {
        User user = currentUser(auth);
        user.setSignature((signature == null || signature.isBlank()) ? null : signature);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("message", "Signature saved.");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String changePassword(Authentication auth,
                                 @RequestParam("currentPassword") String currentPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUser(auth);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Current password is incorrect.");
            return "redirect:/profile";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords don't match.");
            return "redirect:/profile";
        }
        if (newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters.");
            return "redirect:/profile";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("message", "Password changed.");
        return "redirect:/profile";
    }

    @PostMapping("/tokens")
    public String generateToken(Authentication auth,
                                @RequestParam("tokenName") String tokenName,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(auth);
        String rawToken = apiTokenService.generate(user, tokenName);
        redirectAttributes.addFlashAttribute("newToken", rawToken);
        return "redirect:/profile";
    }

    @PostMapping("/tokens/{id}/revoke")
    public String revokeToken(Authentication auth, @PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        User user = currentUser(auth);
        apiTokenService.revoke(id, user);
        redirectAttributes.addFlashAttribute("message", "Token revoked.");
        return "redirect:/profile";
    }

    private User currentUser(Authentication auth) {
        // auth.getName() returns the principal's username (= email in our setup)
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}
