package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.TimeEntry;
import dev.dynamiq.talli.repository.ProjectRepository;
import dev.dynamiq.talli.repository.TimeEntryRepository;
import dev.dynamiq.talli.service.TimeEntryService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/time")
public class TimeEntryController {

    private final TimeEntryRepository timeEntryRepository;
    private final TimeEntryService timeEntryService;
    private final ProjectRepository projectRepository;

    public TimeEntryController(TimeEntryRepository timeEntryRepository, TimeEntryService timeEntryService, ProjectRepository projectRepository) {
        this.timeEntryRepository = timeEntryRepository;
        this.projectRepository = projectRepository;
        this.timeEntryService = timeEntryService;
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
        timeEntryService.create(projectId, parseDateTime(startedAt), parseDateTime(endedAt), description, billable);
        return "redirect:/time";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id,
            @RequestParam("projectId") Long projectId,
            @RequestParam("startedAt") String startedAt,
            @RequestParam(value = "endedAt", required = false) String endedAt,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "billable", defaultValue = "false") Boolean billable) {
        timeEntryService.update(id, projectId, parseDateTime(startedAt), parseDateTime(endedAt), description, billable);
        return "redirect:/time";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        timeEntryService.delete(id);
        return "redirect:/time";
    }

    // Quick-start a running timer (no end time yet)
    @PostMapping("/start")
    public String start(@RequestParam("projectId") Long projectId,
            @RequestParam(value = "description", required = false) String description) {
        timeEntryService.startTimer(projectId, description);
        return "redirect:/time";
    }

    // Stop the running timer
    @PostMapping("/{id}/stop")
    public String stop(@PathVariable("id") Long id) {
        timeEntryService.endTimer(id);
        return "redirect:/time";
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank())
            return null;
        // Browser's datetime-local input sends "2026-04-14T22:30" — ISO without seconds
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
