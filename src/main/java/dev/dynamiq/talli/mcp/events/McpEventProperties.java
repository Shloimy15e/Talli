package dev.dynamiq.talli.mcp.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mcp.events")
public class McpEventProperties {
    private boolean enabled = false;
    private String allowedRecipient = "theo@dynamiq.dev";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAllowedRecipient() { return allowedRecipient; }
    public void setAllowedRecipient(String recipient) { this.allowedRecipient = recipient; }
}
