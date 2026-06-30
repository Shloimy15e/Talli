package dev.dynamiq.talli.service.website;

import java.util.List;

public record WebsiteEditorField(
        String kind,
        String name,
        String label,
        String value,
        String placeholder,
        int rows,
        String width,
        String help,
        String existingName,
        String touchedName,
        List<String> values,
        List<String> marks,
        String requiredMessage,
        String invalidMessage
) {

    public WebsiteEditorField {
        value = value == null ? "" : value;
        placeholder = placeholder == null ? "" : placeholder;
        width = width == null ? "half" : width;
        help = help == null ? "" : help;
        values = values == null ? List.of() : List.copyOf(values);
        marks = marks == null ? List.of() : List.copyOf(marks);
    }

    public static WebsiteEditorField text(String name, String label, String value, String placeholder, String width) {
        return new WebsiteEditorField("text", name, label, value, placeholder, 0, width, "", null, null, List.of(), List.of(), null, null);
    }

    public static WebsiteEditorField email(String name, String label, String value, String placeholder, String width) {
        return new WebsiteEditorField("email", name, label, value, placeholder, 0, width, "", null, null, List.of(), List.of(), null,
                label + " is not a valid email address.");
    }

    public static WebsiteEditorField textarea(String name, String label, String value, String placeholder, int rows, String width) {
        return new WebsiteEditorField("textarea", name, label, value, placeholder, rows, width, "", null, null, List.of(), List.of(), null, null);
    }

    public static WebsiteEditorField richText(String name, String label, String value, String placeholder, String width, List<String> marks) {
        return new WebsiteEditorField("richText", name, label, value, placeholder, 0, width, "", null, null, List.of(), marks, null, null);
    }

    public static WebsiteEditorField color(String name, String label, String value, String placeholder, String width) {
        return new WebsiteEditorField("color", name, label, value, placeholder, 0, width, "", null, null, List.of(), List.of(), null,
                label + " must be a hex color like #D2A84F.");
    }

    public static WebsiteEditorField image(String name, String label, String value, String width, String help) {
        return new WebsiteEditorField("image", name, label, value, "", 0, width, help, null, null, List.of(), List.of(), null, null);
    }

    public static WebsiteEditorField repeatImage(String name, String existingName, String label, String value) {
        return new WebsiteEditorField("image", name, label, value, "", 0, "half", "", existingName, null, List.of(), List.of(), null, null);
    }

    public static WebsiteEditorField imageList(String name, String existingName, String touchedName, String label, List<String> values) {
        return new WebsiteEditorField("imageList", name, label, "", "", 0, "full", "", existingName, touchedName, values, List.of(), null, null);
    }

    public WebsiteEditorField required(String message) {
        return new WebsiteEditorField(kind, name, label, value, placeholder, rows, width, help, existingName, touchedName, values, marks, message, invalidMessage);
    }

    public boolean supportsMark(String mark) {
        return marks.contains(mark);
    }

    public String emailMessage() {
        return invalidMessage;
    }
}
