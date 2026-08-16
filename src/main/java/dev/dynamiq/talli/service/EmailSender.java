package dev.dynamiq.talli.service;

/** A validated outbound email identity. */
record EmailSender(String address, String name) {

    String formatted() {
        return name + " <" + address + ">";
    }

    String defaultSignatureHtml() {
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
