package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.service.MediaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final MediaService mediaService;

    public ProjectController(ProjectRepository projectRepository,
                             ClientRepository clientRepository,
                             MediaService mediaService) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.mediaService = mediaService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("projects", projectRepository.findAll());
        return "projects/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("project", new Project());
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("action", "/projects");
        model.addAttribute("title", "New Project");
        return "projects/_form :: form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Project project = projectRepository.findById(id).orElseThrow();
        model.addAttribute("project", project);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("sows", mediaService.forOwner(project, "sow"));
        model.addAttribute("action", "/projects/" + id);
        model.addAttribute("title", "Edit Project");
        return "projects/_form :: form";
    }

    @PostMapping
    public String create(@ModelAttribute Project project,
                         @RequestParam Long clientId,
                         @RequestParam(value = "sow", required = false) MultipartFile sow) {
        Client client = clientRepository.findById(clientId).orElseThrow();
        project.setClient(client);
        project = projectRepository.save(project);
        attachSowIfPresent(project, sow);
        return "redirect:/projects";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Project project,
                         @RequestParam Long clientId,
                         @RequestParam(value = "sow", required = false) MultipartFile sow) {
        Project existing = projectRepository.findById(id).orElseThrow();
        existing.setName(project.getName());
        existing.setClient(clientRepository.findById(clientId).orElseThrow());
        existing.setRateType(project.getRateType());
        existing.setCurrentRate(project.getCurrentRate());
        existing.setBillingFrequency(project.getBillingFrequency());
        existing.setStatus(project.getStatus());
        existing.setNotes(project.getNotes());
        existing.setCurrency(project.getCurrency());
        projectRepository.save(existing);
        attachSowIfPresent(existing, sow);
        return "redirect:/projects";
    }

    private void attachSowIfPresent(Project project, MultipartFile sow) {
        if (sow != null && !sow.isEmpty()) {
            mediaService.attach(project, sow, "sow");
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        projectRepository.deleteById(id);
        return "redirect:/projects";
    }
}
