package dev.dynamiq.talli.service.website;

import java.util.List;

public record WebsiteEditorRepeatItem(List<WebsiteEditorField> fields) {

    public WebsiteEditorRepeatItem {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
