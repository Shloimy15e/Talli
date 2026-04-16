package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Role;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.RoleRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public AdminUserController(UserRepository userRepository, RoleRepository roleRepository,
                               ClientRepository clientRepository, PasswordEncoder passwordEncoder,
                               EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("users", userRepository.findAllByOrderByCreatedAtDesc());
        return "admin/users/index";
    }

    @GetMapping("/invite")
    public String inviteForm(Model model) {
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("clients", clientRepository.findAll());
        return "admin/users/_invite :: form";
    }

    @PostMapping("/invite")
    public String invite(@RequestParam String email,
                         @RequestParam String name,
                         @RequestParam List<Long> roleIds,
                         @RequestParam(required = false) Long clientId,
                         @RequestParam(required = false) String newClientName,
                         @RequestParam(required = false) String newClientEmail,
                         @RequestParam(required = false) String newClientPhone,
                         @RequestParam(required = false) String newClientBillingAddress,
                         RedirectAttributes flash) {
        if (userRepository.existsByEmail(email)) {
            flash.addFlashAttribute("error", "A user with this email already exists.");
            return "redirect:/admin/users";
        }

        // Build user with a random placeholder password (they'll set it via invite link).
        String token = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(true);
        user.setInviteToken(token);
        user.setInviteSentAt(LocalDateTime.now());

        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        user.setRoles(roles);

        if (clientId != null) {
            clientRepository.findById(clientId).ifPresent(user::setClient);
        } else if (newClientName != null && !newClientName.isBlank()) {
            // Create a new client and link it to this user.
            dev.dynamiq.talli.model.Client client = new dev.dynamiq.talli.model.Client();
            client.setName(newClientName.trim());
            client.setEmail(newClientEmail != null && !newClientEmail.isBlank() ? newClientEmail.trim() : null);
            client.setPhone(newClientPhone != null && !newClientPhone.isBlank() ? newClientPhone.trim() : null);
            client.setBillingAddress(newClientBillingAddress != null && !newClientBillingAddress.isBlank() ? newClientBillingAddress.trim() : null);
            client = clientRepository.save(client);
            user.setClient(client);
        }

        userRepository.save(user);

        // Send invite email.
        String inviteUrl = baseUrl + "/invite/" + token;
        try {
            emailService.sendTemplate(email, "You've been invited to Talli", "invite",
                    Map.of("name", name, "inviteUrl", inviteUrl, "roles", roles));
            flash.addFlashAttribute("success", "Invite sent to " + email);
        } catch (Exception e) {
            flash.addFlashAttribute("error", "User created but invite email failed: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userRepository.findById(id).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("allRoles", roleRepository.findAll());
        model.addAttribute("clients", clientRepository.findAll());
        return "admin/users/_edit :: form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam List<Long> roleIds,
                         @RequestParam(required = false) Long clientId,
                         RedirectAttributes flash) {
        User user = userRepository.findById(id).orElseThrow();
        user.setName(name);
        user.setRoles(new HashSet<>(roleRepository.findAllById(roleIds)));
        user.setClient(clientId != null ? clientRepository.findById(clientId).orElse(null) : null);
        userRepository.save(user);
        flash.addFlashAttribute("success", "User updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/resend-invite")
    public String resendInvite(@PathVariable Long id, RedirectAttributes flash) {
        User user = userRepository.findById(id).orElseThrow();
        String token = UUID.randomUUID().toString();
        user.setInviteToken(token);
        user.setInviteSentAt(LocalDateTime.now());
        userRepository.save(user);

        String inviteUrl = baseUrl + "/invite/" + token;
        try {
            emailService.sendTemplate(user.getEmail(), "You've been invited to Talli", "invite",
                    Map.of("name", user.getName(), "inviteUrl", inviteUrl, "roles", user.getRoles()));
            flash.addFlashAttribute("success", "Invite resent to " + user.getEmail());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Resend failed: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes flash) {
        User user = userRepository.findById(id).orElseThrow();
        user.setEnabled(!user.getEnabled());
        userRepository.save(user);
        flash.addFlashAttribute("success", user.getName() + " " + (user.getEnabled() ? "enabled" : "disabled") + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        userRepository.deleteById(id);
        flash.addFlashAttribute("success", "User deleted.");
        return "redirect:/admin/users";
    }
}
