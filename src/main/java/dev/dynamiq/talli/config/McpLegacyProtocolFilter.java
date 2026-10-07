package dev.dynamiq.talli.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dynamiq.talli.mcp.events.McpEventException;
import dev.dynamiq.talli.mcp.events.McpEventService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Adds MCP 2 discovery and webhook events to the authenticated stateless SDK
 * transport. Existing tools and the legacy initialize handshake stay with the SDK.
 */
public class McpLegacyProtocolFilter extends OncePerRequestFilter {

    private static final String DISCOVER_METHOD = "server/discover";
    public static final String PROTOCOL_VERSION = "2026-07-28";
    private final ObjectMapper objectMapper;
    private final McpEventService events;
    private final Environment environment;

    public McpLegacyProtocolFilter(ObjectMapper objectMapper, McpEventService events, Environment environment) {
        this.objectMapper = objectMapper;
        this.events = events;
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        JsonNode message = parse(body);
        String method = message == null ? "" : message.path("method").asText();
        if (DISCOVER_METHOD.equals(method) || method.startsWith("events/")) {
            handleExtension(response, message, method);
            return;
        }
        filterChain.doFilter(new RepeatableBodyRequest(request, body), response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/mcp".equals(path);
    }

    private JsonNode parse(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void handleExtension(HttpServletResponse response, JsonNode message, String method) throws IOException {
        JsonNode id = message.get("id");
        if (!"2.0".equals(message.path("jsonrpc").asText())
                || (id != null && !id.isNull() && !id.isTextual() && !id.isNumber())) {
            write(response, error(objectMapper.nullNode(), -32600, "Invalid Request", null));
            return;
        }
        // These methods are requests. Notifications cannot silently change subscriptions.
        if (id == null) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return;
        }
        JsonNode params = message.get("params");
        if (params != null && !params.isObject()) {
            write(response, error(id, -32602, "Invalid params", null));
            return;
        }
        if (params == null) {
            params = objectMapper.createObjectNode();
        }
        try {
            var principal = SecurityContextHolder.getContext().getAuthentication();
            JsonNode result = switch (method) {
                case DISCOVER_METHOD -> discovery();
                case "events/list" -> {
                    if (params.hasNonNull("cursor")) {
                        throw new IllegalArgumentException("The event catalog has no continuation cursor");
                    }
                    yield events.catalog(principal);
                }
                case "events/subscribe" -> events.subscribe(principal, params);
                case "events/unsubscribe" -> events.unsubscribe(principal, params);
                default -> null;
            };
            if (result == null) {
                write(response, error(id, -32601, "Method not found", null));
                return;
            }
            ObjectNode payload = envelope(id);
            payload.set("result", result);
            write(response, payload);
        } catch (McpEventException exception) {
            write(response, error(id, exception.getCode(), exception.getMessage(), exception.getReason()));
        } catch (AccessDeniedException exception) {
            write(response, error(id, -32001, "Access denied", null));
        } catch (IllegalArgumentException exception) {
            write(response, error(id, -32602, "Invalid params", null));
        } catch (RuntimeException exception) {
            // Callback destinations and signing secrets must never appear in errors.
            write(response, error(id, -32603, "Internal error", null));
        }
    }

    private ObjectNode discovery() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("resultType", "complete");
        result.putArray("supportedVersions").add(PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        if (events.isEnabled()) {
            capabilities.putObject("events");
        }
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", environment.getProperty("spring.ai.mcp.server.name", "talli"));
        serverInfo.put("version", environment.getProperty("spring.ai.mcp.server.version", "1.0.0"));
        serverInfo.put("title", "Talli");
        ObjectNode icon = serverInfo.putArray("icons").addObject();
        icon.put("src", environment.getRequiredProperty("app.base-url").replaceAll("/+$", "")
                + "/brand/talli-mcp-icon.png");
        icon.put("mimeType", "image/png");
        icon.putArray("sizes").add("512x512");
        result.put("instructions", environment.getProperty("spring.ai.mcp.server.instructions", ""));
        return result;
    }

    private ObjectNode envelope(JsonNode requestId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.set("id", requestId == null ? objectMapper.nullNode() : requestId);
        return payload;
    }

    private ObjectNode error(JsonNode id, int code, String message, String reason) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        if (reason != null) {
            error.putObject("data").put("reason", reason);
        }
        ObjectNode payload = envelope(id);
        payload.set("error", error);
        return payload;
    }

    private void write(HttpServletResponse response, JsonNode payload) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), payload);
    }

    private static final class RepeatableBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private RepeatableBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() { return input.available() == 0; }
                @Override
                public boolean isReady() { return true; }
                @Override
                public void setReadListener(ReadListener readListener) {
                    // The MCP WebMVC transport reads request bodies synchronously.
                }
                @Override
                public int read() { return input.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
