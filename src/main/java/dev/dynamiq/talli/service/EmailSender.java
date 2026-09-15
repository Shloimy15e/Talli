package dev.dynamiq.talli.service;

import java.util.Objects;

/** A validated outbound email identity and the signature owned by that identity. */
public record EmailSender(String address, String name, String signatureHtml) {

    private static final String PHRASE_SPECIALS = "()<>@,;:\\\".[]";

    public EmailSender {
        Objects.requireNonNull(address, "Sender address is required.");
        Objects.requireNonNull(name, "Sender name is required.");
        Objects.requireNonNull(signatureHtml, "Sender signature is required.");
    }

    public String formatted() {
        String formattedName = name;
        if (name.chars().anyMatch(character -> PHRASE_SPECIALS.indexOf(character) >= 0)) {
            formattedName = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return formattedName + " <" + address + ">";
    }

    public static String basicSignatureHtml(String address, String name) {
        String safeName = escapeHtml(name);
        String safeAddress = escapeHtml(address);
        return "<strong>" + safeName + "</strong><br>"
                + "<a href=\"mailto:" + safeAddress + "\">" + safeAddress + "</a>";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
