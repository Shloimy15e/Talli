package dev.dynamiq.talli.controller;

import dev.dynamiq.talli.config.McpOAuthProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class McpOAuthMetadataController {

    private final McpOAuthProperties properties;

    public McpOAuthMetadataController(McpOAuthProperties properties) {
        this.properties = properties;
    }

    @GetMapping({
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/mcp"
    })
    public Map<String, Object> protectedResourceMetadata() {
        return Map.of(
                "resource", properties.resource(),
                "authorization_servers", List.of(properties.issuer()),
                "scopes_supported", List.of("mcp", "offline_access"),
                "bearer_methods_supported", List.of("header"));
    }
}
