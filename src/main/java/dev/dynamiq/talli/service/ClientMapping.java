package dev.dynamiq.talli.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes raw spreadsheet client identifiers into clean Talli client names.
 * Rules are evaluated top-to-bottom; first match wins.
 */
public final class ClientMapping {

    private ClientMapping() {}

    private record Rule(Pattern pattern, String replacement) {}

    private static final List<Rule> RULES = List.of(
            // Known client aliases
            new Rule(Pattern.compile("(?i).*zalo.*|.*zcdzia.*|.*elidzialo.*"), "Zalo"),
            new Rule(Pattern.compile("(?i).*pledgepro.*|.*binet.*|.*amslangsam.*"), "PledgePro Inc"),
            new Rule(Pattern.compile("(?i).*dijy.*"), "Dijy"),
            new Rule(Pattern.compile("(?i).*ari\\s*braun.*"), "Ari Braun"),
            new Rule(Pattern.compile("(?i).*owal.*"), "Owal"),
            new Rule(Pattern.compile("(?i).*hybiz.*|.*yoni.*"), "Hybiz"),
            new Rule(Pattern.compile("(?i).*dudy.*korn.*"), "Dudy Korn"),
            new Rule(Pattern.compile("(?i).*chezky.*kohn.*"), "Chezky Kohn"),
            new Rule(Pattern.compile("(?i).*zvi.*rand.*"), "Zvi Rand"),
            new Rule(Pattern.compile("(?i).*moishy.*eisenstein.*"), "Moishy Eisenstein"),
            // Generic structural rules (replacement=null → use capture group 1)
            new Rule(Pattern.compile("(.+?)\\s+-\\s+\\S+@\\S+"), null),
            new Rule(Pattern.compile("(.+?)\\s+\\S+@\\S+"), null),
            new Rule(Pattern.compile("@(\\w+)"), null),
            new Rule(Pattern.compile("(\\w+)@.+"), null),
            new Rule(Pattern.compile("(.+?)\\s+Website"), null)
    );

    public static Map<String, String> suggest(Set<String> rawNames) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String raw : rawNames) {
            mapping.put(raw, normalize(raw));
        }
        return mapping;
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String trimmed = raw.trim();

        for (Rule rule : RULES) {
            Matcher m = rule.pattern.matcher(trimmed);
            if (m.matches()) {
                if (rule.replacement != null) return rule.replacement;
                return capitalize(m.group(1).trim());
            }
        }

        return trimmed;
    }

    /**
     * Extract a project name embedded in the raw client string, if any.
     * "zcdzia@gmail.com Invitation feature" → "Invitation feature"
     * "zalo - fix window size bug" → "fix window size bug"
     * "ari@dijy.com" → null (no project embedded)
     */
    public static String extractProject(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();

        // "email@domain.com Some Project" → "Some Project"
        Matcher emailPrefix = Pattern.compile("\\S+@\\S+\\s+(.+)").matcher(trimmed);
        if (emailPrefix.matches()) {
            return capitalize(emailPrefix.group(1).trim());
        }

        // "Client - description" (but NOT "Client - email") → "description"
        Matcher dashSuffix = Pattern.compile(".+?\\s+-\\s+(?!\\S+@\\S+)(.+)").matcher(trimmed);
        if (dashSuffix.matches()) {
            return capitalize(dashSuffix.group(1).trim());
        }

        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
