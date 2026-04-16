package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.model.Expense;
import dev.dynamiq.talli.repository.ExpenseRepository;
import dev.dynamiq.talli.service.ImportService;
import dev.dynamiq.talli.service.ImportService.ParsedFile;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Controller
@RequestMapping("/admin/import")
public class ImportController {

    private static final String SESSION_KEY = "importParsed";

    /** The Expense fields a user can map file columns to. */
    static final List<String> EXPENSE_FIELDS = List.of(
            "", "date", "amount", "currency", "category", "vendor", "description", "paymentMethod"
    );

    private static final Set<String> VALID_CATEGORIES = Set.copyOf(Expense.CATEGORIES);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy")
    );

    private final ImportService importService;
    private final ExpenseRepository expenseRepository;

    public ImportController(ImportService importService,
                            ExpenseRepository expenseRepository) {
        this.importService = importService;
        this.expenseRepository = expenseRepository;
    }

    /** Show the upload form. */
    @GetMapping
    public String index() {
        return "admin/import/index";
    }

    /** Parse the uploaded file, store in session, redirect to preview. */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         HttpSession session,
                         RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select a file.");
            return "redirect:/admin/import";
        }
        try {
            ParsedFile parsed = importService.parse(file);
            if (parsed.rows().isEmpty()) {
                ra.addFlashAttribute("error", "File is empty or has no data rows.");
                return "redirect:/admin/import";
            }
            session.setAttribute(SESSION_KEY, parsed);
            ra.addFlashAttribute("filename", file.getOriginalFilename());
            return "redirect:/admin/import/preview";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to parse file: " + e.getMessage());
            return "redirect:/admin/import";
        }
    }

    /** Show column-mapping preview with first 10 rows. */
    @GetMapping("/preview")
    public String preview(HttpSession session, Model model) {
        ParsedFile parsed = getParsed(session);
        if (parsed == null) return "redirect:/admin/import";

        model.addAttribute("headers", parsed.headers());
        model.addAttribute("previewRows", parsed.rows().subList(0, Math.min(10, parsed.rows().size())));
        model.addAttribute("totalRows", parsed.rows().size());
        model.addAttribute("fields", EXPENSE_FIELDS);
        model.addAttribute("autoMapping", autoMap(parsed.headers()));
        return "admin/import/preview";
    }

    /** Execute the import with user-chosen column mapping. */
    @PostMapping("/confirm")
    public String confirm(HttpSession session,
                          @RequestParam Map<String, String> params,
                          RedirectAttributes ra) {
        ParsedFile parsed = getParsed(session);
        if (parsed == null) return "redirect:/admin/import";

        // Build mapping: file header → expense field
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String header : parsed.headers()) {
            String field = params.get("map_" + header);
            if (field != null && !field.isBlank()) {
                mapping.put(header, field);
            }
        }

        if (!mapping.containsValue("date") || !mapping.containsValue("amount")) {
            ra.addFlashAttribute("error", "You must map at least 'date' and 'amount' columns.");
            return "redirect:/admin/import/preview";
        }

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < parsed.rows().size(); i++) {
            Map<String, String> row = parsed.rows().get(i);
            try {
                Expense e = buildExpense(row, mapping);
                if (e != null) {
                    expenseRepository.save(e);
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                skipped++;
                if (errors.size() < 20) {
                    errors.add("Row " + (i + 2) + ": " + ex.getMessage());
                }
            }
        }

        session.removeAttribute(SESSION_KEY);

        ra.addFlashAttribute("importedCount", imported);
        ra.addFlashAttribute("skippedCount", skipped);
        ra.addFlashAttribute("importErrors", errors);
        return "redirect:/admin/import/results";
    }

    @GetMapping("/results")
    public String results() {
        return "admin/import/results";
    }

    // ── helpers ──

    private ParsedFile getParsed(HttpSession session) {
        Object obj = session.getAttribute(SESSION_KEY);
        return obj instanceof ParsedFile pf ? pf : null;
    }

    /** Auto-map headers to fields based on common names. */
    private Map<String, String> autoMap(List<String> headers) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String h : headers) {
            String lower = h.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (lower.contains("amount") || lower.contains("debit") || lower.contains("sum") || lower.contains("total")) {
                map.put(h, "amount");
            } else if (lower.contains("date") || lower.contains("posted") || lower.equals("trans") || lower.equals("transaction")) {
                map.put(h, "date");
            } else if (lower.contains("currency") || lower.equals("cur") || lower.equals("ccy")) {
                map.put(h, "currency");
            } else if (lower.contains("category") || lower.contains("type")) {
                map.put(h, "category");
            } else if (lower.contains("vendor") || lower.contains("payee") || lower.contains("merchant") || lower.contains("name")) {
                map.put(h, "vendor");
            } else if (lower.contains("description") || lower.contains("memo") || lower.contains("note") || lower.contains("detail")) {
                map.put(h, "description");
            } else if (lower.contains("method") || lower.contains("payment")) {
                map.put(h, "paymentMethod");
            } else {
                map.put(h, "");
            }
        }
        return map;
    }

    private Expense buildExpense(Map<String, String> row, Map<String, String> mapping) {
        String dateStr = getMappedValue(row, mapping, "date");
        String amountStr = getMappedValue(row, mapping, "amount");

        if (dateStr == null || dateStr.isBlank() || amountStr == null || amountStr.isBlank()) {
            return null; // skip blank rows
        }

        Expense e = new Expense();
        e.setIncurredOn(parseDate(dateStr));
        e.setAmount(parseAmount(amountStr));
        e.setCurrency(getOrDefault(row, mapping, "currency", "USD"));
        String rawCategory = getOrDefault(row, mapping, "category", "other");
        e.setCategory(VALID_CATEGORIES.contains(rawCategory.toLowerCase()) ? rawCategory.toLowerCase() : "other");
        e.setVendor(getMappedValue(row, mapping, "vendor"));
        e.setDescription(getMappedValue(row, mapping, "description"));
        e.setPaymentMethod(getMappedValue(row, mapping, "paymentMethod"));
        e.setBillable(false);
        e.setBilled(false);
        return e;
    }

    private String getMappedValue(Map<String, String> row, Map<String, String> mapping, String field) {
        for (var entry : mapping.entrySet()) {
            if (field.equals(entry.getValue())) {
                String val = row.get(entry.getKey());
                return (val != null && !val.isBlank()) ? val.trim() : null;
            }
        }
        return null;
    }

    private String getOrDefault(Map<String, String> row, Map<String, String> mapping, String field, String def) {
        String val = getMappedValue(row, mapping, field);
        return val != null ? val : def;
    }

    private LocalDate parseDate(String s) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(s.trim(), fmt);
            } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Cannot parse date: " + s);
    }

    private BigDecimal parseAmount(String s) {
        // Strip currency symbols, thousands separators, whitespace
        String cleaned = s.trim()
                .replaceAll("[^\\d.\\-]", "");
        if (cleaned.isBlank()) throw new IllegalArgumentException("Empty amount");
        BigDecimal val = new BigDecimal(cleaned);
        return val.abs(); // expenses are always positive
    }
}
