package dev.dynamiq.talli.service.website;

import java.util.List;

public record WebsiteEditorRepeat(
        String itemLabel,
        String addLabel,
        List<WebsiteEditorField> templateFields,
        List<WebsiteEditorRepeatItem> items
) {

    public WebsiteEditorRepeat {
        templateFields = templateFields == null ? List.of() : List.copyOf(templateFields);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
