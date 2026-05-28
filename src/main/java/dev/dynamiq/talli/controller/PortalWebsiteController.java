package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.UserRepository;
import dev.dynamiq.talli.service.website.WebsiteContentService;
import dev.dynamiq.talli.service.website.WebsiteSaveResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/portal/projects/{projectId}/website")
public class PortalWebsiteController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final WebsiteContentService websiteContentService;

    public PortalWebsiteController(UserRepository userRepository,
                                   ProjectRepository projectRepository,
                                   WebsiteContentService websiteContentService) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.websiteContentService = websiteContentService;
    }

    @GetMapping
    public String edit(@PathVariable Long projectId, Authentication auth, Model model) {
        Project project = resolvePortalProject(projectId, auth);
        if (project == null) {
            model.addAttribute("error", "Website project not found.");
            return "portal/error";
        }

        model.addAttribute("project", project);
        try {
            model.addAttribute("form", websiteContentService.load(project));
        } catch (IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            return "portal/error";
        }
        return "portal/website";
    }

    @PostMapping
    public String update(@PathVariable Long projectId,
                         Authentication auth,
                         HttpServletRequest request,
                         RedirectAttributes flash) {
        Project project = resolvePortalProject(projectId, auth);
        if (project == null) {
            flash.addFlashAttribute("error", "Website project not found.");
            return "redirect:/portal";
        }

        try {
            WebsiteSaveResult result = websiteContentService.save(project, request.getParameterMap(), uploads(request));
            flash.addFlashAttribute("success", result.changed()
                    ? "Website changes published."
                    : "No website changes to publish.");
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Website publish failed: " + e.getMessage());
        }

        return "redirect:/portal/projects/" + projectId + "/website";
    }

    private Project resolvePortalProject(Long projectId, Authentication auth) {
        if (auth == null) {
            return null;
        }

        Client client = userRepository.findByEmail(auth.getName())
                .map(User::getClient)
                .orElse(null);
        Project project = projectRepository.findById(projectId).orElse(null);
        if (client == null || project == null || project.getClient() == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(project.getWebsiteEnabled())) {
            return null;
        }
        return project.getClient().getId().equals(client.getId()) ? project : null;
    }

    private MultiValueMap<String, MultipartFile> uploads(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest multipartRequest)) {
            return new LinkedMultiValueMap<>();
        }

        MultiValueMap<String, MultipartFile> uploads = new LinkedMultiValueMap<>();
        multipartRequest.getMultiFileMap().forEach(uploads::put);
        return uploads;
    }
}
