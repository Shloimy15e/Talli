package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Project;
import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/time")
public class TimeEntryController {

    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;

    public TimeEntryController(TimeEntryRepository timeEntryRepository, ProjectRepository projectRepository) {
        this.timeEntryRepository = timeEntryRepository;
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("entries", timeEntryRepository.findAllByOrderByStartedAtDesc());
        model.addAttribute("running", timeEntryRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc().orElse(null));
        model.addAttribute("projects", projectRepository.findAll());
        return "time/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        TimeEntry entry = new TimeEntry();
        entry.setStartedAt(LocalDateTime.now().withSecond(0).withNano(0));
        model.addAttribute("entry", entry);
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("action", "/time");
        model.addAttribute("title", "Log Time");
        return "time/_form :: form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();
        model.addAttribute("entry", entry);
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("action", "/time/" + id);
        model.addAttribute("title", "Edit Time Entry");
        return "time/_form :: form";
    }

    @PostMapping
    public String create(@RequestParam("projectId") Long projectId,
                         @RequestParam("startedAt") String startedAt,
                         @RequestParam(value = "endedAt", required = false) String endedAt,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "billable", defaultValue = "false") Boolean billable) {
        TimeEntry entry = new TimeEntry();
        Project project = projectRepository.findById(projectId).orElseThrow();
        entry.setProject(project);
        entry.setStartedAt(parseDateTime(startedAt));
        entry.setEndedAt(parseDateTime(endedAt));
        entry.setDescription(description);
        entry.setBillable(billable);
        timeEntryRepository.save(entry);
        return "redirect:/time";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id,
                         @RequestParam("projectId") Long projectId,
                         @RequestParam("startedAt") String startedAt,
                         @RequestParam(value = "endedAt", required = false) String endedAt,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "billable", defaultValue = "false") Boolean billable) {
        TimeEntry existing = timeEntryRepository.findById(id).orElseThrow();
        existing.setProject(projectRepository.findById(projectId).orElseThrow());
        existing.setStartedAt(parseDateTime(startedAt));
        existing.setEndedAt(parseDateTime(endedAt));
        existing.setDescription(description);
        existing.setBillable(billable);
        timeEntryRepository.save(existing);
        return "redirect:/time";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        timeEntryRepository.deleteById(id);
        return "redirect:/time";
    }

    // Quick-start a running timer (no end time yet)
    @PostMapping("/start")
    public String start(@RequestParam("projectId") Long projectId,
                        @RequestParam(value = "description", required = false) String description) {
        TimeEntry entry = new TimeEntry();
        entry.setProject(projectRepository.findById(projectId).orElseThrow());
        entry.setStartedAt(LocalDateTime.now());
        entry.setDescription(description);
        entry.setBillable(true);
        timeEntryRepository.save(entry);
        return "redirect:/time";
    }

    // Stop the running timer
    @PostMapping("/{id}/stop")
    public String stop(@PathVariable("id") Long id) {
        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();
        entry.setEndedAt(LocalDateTime.now());
        timeEntryRepository.save(entry);
        return "redirect:/time";
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        // Browser's datetime-local input sends "2026-04-14T22:30" — ISO without seconds
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
