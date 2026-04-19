package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.repository.ClientRepository;
import dev.dynamiq.talli.repository.InvoiceItemRepository;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.MediaService;
import dev.dynamiq.talli.service.ProjectService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final MediaService mediaService;
    private final TimeEntryRepository timeEntryRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ProjectService projectService;

    public ProjectController(ProjectRepository projectRepository,
                             ClientRepository clientRepository,
                             MediaService mediaService,
                             TimeEntryRepository timeEntryRepository,
                             InvoiceItemRepository invoiceItemRepository,
                             ProjectService projectService) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.mediaService = mediaService;
        this.timeEntryRepository = timeEntryRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.projectService = projectService;
    }

    public record ProjectRow(Project project, java.time.LocalDateTime lastActivity) {}

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) List<String> status,
                        @RequestParam(required = false) List<Long> clientId,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "activity") String sort,
                        @RequestParam(defaultValue = "desc") String direction,
                        Model model) {
        String normalizedSort = List.of("activity", "name", "client", "created").contains(sort) ? sort : "activity";
        String normalizedDir = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";

        var rawPage = projectRepository.findFilteredWithActivity(status, clientId,
                search != null ? search : "",
                normalizedSort, normalizedDir,
                org.springframework.data.domain.PageRequest.of(page, 25));
        var rows = rawPage.getContent().stream()
                .map(arr -> new ProjectRow((Project) arr[0], (java.time.LocalDateTime) arr[1]))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("page", rawPage);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("filterStatuses", status);
        model.addAttribute("filterClientIds", clientId);
        model.addAttribute("filterSearch", search);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("direction", normalizedDir);
        return "projects/index";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Project project = projectRepository.findById(id).orElseThrow();
        model.addAttribute("project", project);
        model.addAttribute("timeEntries", timeEntryRepository.findByProjectIdOrderByStartedAtDesc(id));
        model.addAttribute("invoices", invoiceItemRepository.findInvoicesByProjectId(id));
        model.addAttribute("sows", mediaService.forOwner(project, "sow"));
        model.addAttribute("summary", projectService.summary(id));
        return "projects/show";
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
        existing.setBillable(project.getBillable() != null ? project.getBillable() : true);
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

    @PostMapping("/{id}/change-contract")
    public String changeContract(@PathVariable Long id,
                                 @RequestParam BigDecimal newAmount,
                                 @RequestParam(required = false) String reason) {
        projectService.changeContractAmount(id, newAmount, reason);
        return "redirect:/projects/" + id;
    }
}
