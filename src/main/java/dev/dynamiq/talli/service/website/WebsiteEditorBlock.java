package dev.dynamiq.talli.service.website;

import java.util.List;

public record WebsiteEditorBlock(
        String kind,
        String title,
        String help,
        String layout,
        List<WebsiteEditorField> fields,
        WebsiteEditorRepeat repeat
) {

    public WebsiteEditorBlock {
        help = help == null ? "" : help;
        layout = layout == null ? "single" : layout;
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static WebsiteEditorBlock fields(String layout, List<WebsiteEditorField> fields) {
        return new WebsiteEditorBlock("fields", null, "", layout, fields, null);
    }

    public static WebsiteEditorBlock repeat(String title, String help, WebsiteEditorRepeat repeat) {
        return new WebsiteEditorBlock("repeat", title, help, "single", List.of(), repeat);
    }
}
