package dev.dynamiq.talli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class OAuthResourceParameterFilter extends OncePerRequestFilter {

    private final McpOAuthProperties properties;
    private final ObjectMapper objectMapper;

    public OAuthResourceParameterFilter(McpOAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = path(request);
        boolean exactResource = hasExactResource(request);

        if ("/oauth/authorize".equals(path) && "code".equals(request.getParameter("response_type"))) {
            if (!exactResource) {
                sendInvalidTarget(response);
                return;
            }
        }

        if ("/oauth/token".equals(path)) {
            String grantType = request.getParameter("grant_type");
            boolean resourceRequired = "authorization_code".equals(grantType)
                    || "refresh_token".equals(grantType);
            boolean resourceProvided = request.getParameterValues("resource") != null;
            if ((resourceRequired && !exactResource) || (resourceProvided && !exactResource)) {
                sendInvalidTarget(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasExactResource(HttpServletRequest request) {
        String[] resources = request.getParameterValues("resource");
        return resources != null
                && resources.length == 1
                && properties.resource().equals(resources[0]);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !"/oauth/authorize".equals(path) && !"/oauth/token".equals(path);
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private void sendInvalidTarget(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "invalid_target",
                "error_description", "resource must identify the Talli MCP server"));
    }
}
