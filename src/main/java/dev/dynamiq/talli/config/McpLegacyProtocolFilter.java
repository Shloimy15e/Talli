package dev.dynamiq.talli.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Makes the legacy MCP server fail modern discovery in the form expected by
 * clients that can fall back to the initialize handshake.
 */
public class McpLegacyProtocolFilter extends OncePerRequestFilter {

    private static final String DISCOVER_METHOD = "server/discover";
    private static final int METHOD_NOT_FOUND = -32601;

    private final ObjectMapper objectMapper;

    public McpLegacyProtocolFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        JsonNode message = parse(body);

        if (message != null && DISCOVER_METHOD.equals(message.path("method").asText())) {
            writeMethodNotFound(response, message.get("id"));
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

    private void writeMethodNotFound(HttpServletResponse response, JsonNode requestId) throws IOException {
        var error = objectMapper.createObjectNode();
        error.put("code", METHOD_NOT_FOUND);
        error.put("message", "Method not found");

        var payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.set("id", requestId == null ? objectMapper.nullNode() : requestId);
        payload.set("error", error);

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
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
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // The MCP WebMVC transport reads request bodies synchronously.
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
