package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.service.ImportService;
import dev.dynamiq.talli.service.ImportService.ParsedWorkbook;
import dev.dynamiq.talli.service.MigrationService;
import dev.dynamiq.talli.service.MigrationService.MigrationPreview;
import dev.dynamiq.talli.service.MigrationService.MigrationResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/migration")
public class MigrationController {

    private static final String SESSION_KEY = "migrationWorkbook";

    private final ImportService importService;
    private final MigrationService migrationService;

    public MigrationController(ImportService importService, MigrationService migrationService) {
        this.importService = importService;
        this.migrationService = migrationService;
    }

    @GetMapping
    public String index() {
        return "admin/migration/index";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         HttpSession session,
                         RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select a file.");
            return "redirect:/admin/migration";
        }
        try {
            ParsedWorkbook workbook = importService.parseAllSheets(file);
            session.setAttribute(SESSION_KEY, workbook);
            ra.addFlashAttribute("filename", file.getOriginalFilename());
            return "redirect:/admin/migration/preview";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to parse file: " + e.getMessage());
            return "redirect:/admin/migration";
        }
    }

    @GetMapping("/preview")
    public String preview(HttpSession session, Model model) {
        ParsedWorkbook workbook = getWorkbook(session);
        if (workbook == null) return "redirect:/admin/migration";

        MigrationPreview preview = migrationService.buildPreview(workbook);
        model.addAttribute("preview", preview);
        model.addAttribute("sheetNames", workbook.sheets().keySet());
        model.addAttribute("sheetSizes", workbook.sheets().entrySet().stream()
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue().rows().size()),
                        Map::putAll));
        return "admin/migration/preview";
    }

    @PostMapping("/confirm")
    public String confirm(HttpSession session,
                          @RequestParam Map<String, String> params,
                          RedirectAttributes ra) {
        ParsedWorkbook workbook = getWorkbook(session);
        if (workbook == null) return "redirect:/admin/migration";

        // Extract client mapping from index-based form params (rawName_0/mappedName_0)
        Map<String, String> clientMapping = new LinkedHashMap<>();
        for (int i = 0; params.containsKey("rawName_" + i); i++) {
            String rawName = params.get("rawName_" + i);
            String mappedName = params.getOrDefault("mappedName_" + i, "").trim();
            if (!rawName.isBlank() && !mappedName.isBlank()) {
                clientMapping.put(rawName, mappedName);
            }
        }

        try {
            MigrationResult result = migrationService.executeImport(workbook, clientMapping);
            session.removeAttribute(SESSION_KEY);
            ra.addFlashAttribute("result", result);
            return "redirect:/admin/migration/results";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Migration failed: " + e.getMessage());
            return "redirect:/admin/migration/preview";
        }
    }

    @GetMapping("/results")
    public String results() {
        return "admin/migration/results";
    }

    @PostMapping("/backfill-rates")
    public String backfillRates(RedirectAttributes ra) {
        int[] counts = migrationService.backfillExchangeRates();
        ra.addFlashAttribute("backfillInvoices", counts[0]);
        ra.addFlashAttribute("backfillPayments", counts[1]);
        return "redirect:/admin/migration";
    }

    private ParsedWorkbook getWorkbook(HttpSession session) {
        Object obj = session.getAttribute(SESSION_KEY);
        return obj instanceof ParsedWorkbook wb ? wb : null;
    }
}
