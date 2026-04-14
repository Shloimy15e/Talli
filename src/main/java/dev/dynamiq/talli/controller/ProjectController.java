package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;

    public ProjectController(ProjectRepository projectRepository, ClientRepository clientRepository) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
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
        model.addAttribute("action", "/projects/" + id);
        model.addAttribute("title", "Edit Project");
        return "projects/_form :: form";
    }

    @PostMapping
    public String create(@ModelAttribute Project project, @RequestParam Long clientId) {
        // The form sends `clientId` as a separate param; look up the Client entity and
        // attach it.
        // This is how @ManyToOne relationships get saved — attach a managed entity, not
        // just an ID.
        Client client = clientRepository.findById(clientId).orElseThrow();
        project.setClient(client);
        projectRepository.save(project);
        return "redirect:/projects";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Project project, @RequestParam Long clientId) {
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
        return "redirect:/projects";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        projectRepository.deleteById(id);
        return "redirect:/projects";
    }
}
