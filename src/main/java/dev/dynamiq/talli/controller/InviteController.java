package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/invite")
public class InviteController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public InviteController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/{token}")
    public String showForm(@PathVariable String token, Model model) {
        User user = userRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite link."));
        model.addAttribute("token", token);
        model.addAttribute("name", user.getName());
        model.addAttribute("email", user.getEmail());
        return "invite/accept";
    }

    @PostMapping("/{token}")
    public String accept(@PathVariable String token,
                         @RequestParam String password,
                         @RequestParam String passwordConfirmation,
                         RedirectAttributes flash) {
        User user = userRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite link."));

        if (password.length() < 8) {
            flash.addFlashAttribute("error", "Password must be at least 8 characters.");
            return "redirect:/invite/" + token;
        }
        if (!password.equals(passwordConfirmation)) {
            flash.addFlashAttribute("error", "Passwords do not match.");
            return "redirect:/invite/" + token;
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setInviteToken(null);
        userRepository.save(user);

        flash.addFlashAttribute("success", "Password set. You can now log in.");
        return "redirect:/login";
    }
}
