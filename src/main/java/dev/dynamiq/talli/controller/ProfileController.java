package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String profile(Authentication auth, Model model) {
        User user = currentUser(auth);
        model.addAttribute("user", user);
        return "profile/index";
    }

    @PostMapping
    public String updateProfile(Authentication auth,
                                @RequestParam("name") String name,
                                @RequestParam("email") String email,
                                Model model) {
        User user = currentUser(auth);
        user.setName(name);
        user.setEmail(email);
        userRepository.save(user);
        model.addAttribute("user", user);
        model.addAttribute("message", "Profile updated.");
        return "profile/index";
    }

    @PostMapping("/password")
    public String changePassword(Authentication auth,
                                 @RequestParam("currentPassword") String currentPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 Model model) {
        User user = currentUser(auth);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("user", user);
            model.addAttribute("error", "Current password is incorrect.");
            return "profile/index";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("user", user);
            model.addAttribute("error", "New passwords don't match.");
            return "profile/index";
        }
        if (newPassword.length() < 8) {
            model.addAttribute("user", user);
            model.addAttribute("error", "Password must be at least 8 characters.");
            return "profile/index";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        model.addAttribute("user", user);
        model.addAttribute("message", "Password changed.");
        return "profile/index";
    }

    private User currentUser(Authentication auth) {
        // auth.getName() returns the principal's username (= email in our setup)
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}
