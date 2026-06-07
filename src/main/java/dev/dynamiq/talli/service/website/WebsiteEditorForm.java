package dev.dynamiq.talli.service.website;

import java.util.List;

public record WebsiteEditorForm(List<WebsiteEditorSection> sections) {

    public WebsiteEditorForm {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
