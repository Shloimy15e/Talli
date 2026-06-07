package dev.dynamiq.talli.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.website.NorthlightWebsiteAdapter;
import dev.dynamiq.talli.service.website.WebsiteEditorForm;
import dev.dynamiq.talli.service.website.WebsiteContentService;
import dev.dynamiq.talli.service.website.WebsiteSaveResult;
import dev.dynamiq.talli.support.factory.NorthlightWebsiteFactory;
import dev.dynamiq.talli.support.factory.ProjectFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class PortalWebsiteControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WebsiteContentService websiteContentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new PortalWebsiteController(userRepository, projectRepository, websiteContentService))
                .build();
    }

    @Test
    void editShowsWebsiteFormForClientsOwnProject() throws Exception {
        Client client = client(10L);
        Project project = websiteProject(client);
        WebsiteEditorForm form = northlightForm();

        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user(client)));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(websiteContentService.load(project)).thenReturn(form);

        mockMvc.perform(get("/portal/projects/{projectId}/website", 1L).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(view().name("portal/website"))
                .andExpect(model().attribute("project", project))
                .andExpect(model().attribute("form", form));
    }

    @Test
    void editRejectsProjectFromAnotherClient() throws Exception {
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user(client(10L))));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(websiteProject(client(99L))));

        mockMvc.perform(get("/portal/projects/{projectId}/website", 1L).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(view().name("portal/error"))
                .andExpect(model().attribute("error", "Website project not found."));
    }

    @Test
    void editShowsPortalErrorWhenWebsiteProjectIsNotConnected() throws Exception {
        Client client = client(10L);
        Project project = ProjectFactory.configuredWebsiteProject();
        project.setClient(client);

        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user(client)));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(websiteContentService.load(project))
                .thenThrow(new IllegalStateException("Website project is not connected to GitHub yet."));

        mockMvc.perform(get("/portal/projects/{projectId}/website", 1L).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(view().name("portal/error"))
                .andExpect(model().attribute("error", "Website project is not connected to GitHub yet."));
    }

    @Test
    void updatePublishesWebsiteContentAndRedirectsBackToEditor() throws Exception {
        Client client = client(10L);
        Project project = websiteProject(client);
        MockMultipartFile image = new MockMultipartFile(
                "homeHighlightsImage", "hero.png", "image/png", "image".getBytes(StandardCharsets.UTF_8));

        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user(client)));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(websiteContentService.save(eq(project), any(), any()))
                .thenReturn(new WebsiteSaveResult(true, "abc123"));

        mockMvc.perform(multipart("/portal/projects/{projectId}/website", 1L)
                        .file(image)
                        .param("homeHeroHeadline", "New headline")
                        .principal(auth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portal/projects/1/website"))
                .andExpect(flash().attribute("success", "Website changes published. The live site can take a few minutes to update."));

        verify(websiteContentService).save(eq(project), any(), any());
    }

    private UsernamePasswordAuthenticationToken auth() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "client@example.com", "password", java.util.List.of());
    }

    private User user(Client client) {
        User user = new User();
        user.setEmail("client@example.com");
        user.setClient(client);
        return user;
    }

    private Client client(Long id) {
        Client client = new Client();
        client.setId(id);
        client.setName("Northlight");
        return client;
    }

    private Project websiteProject(Client client) {
        Project project = ProjectFactory.connectedWebsiteProject();
        project.setClient(client);
        return project;
    }

    private WebsiteEditorForm northlightForm() {
        return new NorthlightWebsiteAdapter(new ObjectMapper()).toEditorForm(NorthlightWebsiteFactory.repoFiles());
    }
}
