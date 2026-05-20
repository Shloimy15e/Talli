package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class InviteControllerTest {

    private static final String INVALID_INVITE_MESSAGE =
            "This invite link is invalid, expired, or has already been used.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new InviteController(userRepository, passwordEncoder)).build();
    }

    @Test
    void showFormReturnsInvalidInvitePageWhenTokenIsMissing() throws Exception {
        when(userRepository.findByInviteToken("missing-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/invite/{token}", "missing-token"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("invite/invalid"))
                .andExpect(model().attribute("error", INVALID_INVITE_MESSAGE));
    }

    @Test
    void acceptReturnsInvalidInvitePageWhenTokenIsMissing() throws Exception {
        when(userRepository.findByInviteToken("missing-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/invite/{token}", "missing-token")
                        .param("password", "new-password")
                        .param("passwordConfirmation", "new-password"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("invite/invalid"))
                .andExpect(model().attribute("error", INVALID_INVITE_MESSAGE));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void acceptSetsPasswordClearsInviteTokenAndRedirectsToLogin() throws Exception {
        User user = new User();
        user.setEmail("invitee@example.com");
        user.setName("Invitee");
        user.setPassword("old-password");
        user.setInviteToken("valid-token");
        user.setInviteSentAt(LocalDateTime.now());

        when(userRepository.findByInviteToken("valid-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        mockMvc.perform(post("/invite/{token}", "valid-token")
                        .param("password", "new-password")
                        .param("passwordConfirmation", "new-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("success", "Password set. You can now log in."));

        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.getInviteToken()).isNull();
        assertThat(user.getInviteSentAt()).isNull();
        verify(userRepository).save(user);
    }
}
