package dev.dynamiq.talli.service.website;

import java.util.List;

public record WebsiteEditorSection(
        String key,
        String label,
        String title,
        String icon,
        List<WebsiteEditorBlock> blocks
) {

    public WebsiteEditorSection {
        icon = icon == null || icon.isBlank() ? "file-text" : icon;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
