package dev.dynamiq.talli.service.website;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebsiteContentAdapters {

    private final Map<String, WebsiteContentAdapter> adapters;
    private final String defaultType;

    public WebsiteContentAdapters(List<WebsiteContentAdapter> adapters) {
        Map<String, WebsiteContentAdapter> byType = new LinkedHashMap<>();
        for (WebsiteContentAdapter adapter : adapters) {
            byType.put(adapter.type(), adapter);
        }
        this.adapters = Map.copyOf(byType);
        this.defaultType = byType.keySet().stream().sorted().findFirst()
                .orElseThrow(() -> new IllegalStateException("No website content adapters are registered."));
    }

    public String defaultType() {
        return defaultType;
    }

    public WebsiteContentAdapter require(String type) {
        WebsiteContentAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalStateException("Unsupported website type: " + type);
        }
        return adapter;
    }
}
