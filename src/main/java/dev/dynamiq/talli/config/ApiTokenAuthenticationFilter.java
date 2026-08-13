package dev.dynamiq.talli.config;

import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.service.ApiTokenService;
import dev.dynamiq.talli.service.OAuthAccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates API and MCP requests using Bearer tokens.
 * Builds the same authorities (roles + permissions) as the session-based
 * UserDetailsService so that @PreAuthorize / hasAuthority checks work identically.
 */
@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private final ApiTokenService apiTokenService;
    private final OAuthAccessTokenService oauthAccessTokenService;
    private final McpOAuthProperties oauthProperties;

    public ApiTokenAuthenticationFilter(
            ApiTokenService apiTokenService,
            OAuthAccessTokenService oauthAccessTokenService,
            McpOAuthProperties oauthProperties) {
        this.apiTokenService = apiTokenService;
        this.oauthAccessTokenService = oauthAccessTokenService;
        this.oauthProperties = oauthProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(request, response, "Missing or invalid Authorization header");
            return;
        }

        String rawToken = authHeader.substring(7);
        Optional<User> maybeUser = apiTokenService.authenticate(rawToken)
                .map(token -> token.getUser());
        if (maybeUser.isEmpty()) {
            maybeUser = oauthAccessTokenService.authenticate(rawToken);
        }

        if (maybeUser.isEmpty()) {
            sendUnauthorized(request, response, "Invalid bearer token");
            return;
        }

        User user = maybeUser.get();

        // Build the same authority list that SecurityConfig.userDetailsService() builds.
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        user.roleNames().forEach(r ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        user.allPermissions().forEach(p ->
                authorities.add(new SimpleGrantedAuthority(p)));

        var authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /** Only apply this filter to API/MCP paths, and skip CORS preflight requests. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean protectedPath = path.startsWith("/api/")
                || path.equals("/mcp")
                || path.startsWith("/mcp/");
        return !protectedPath || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private void sendUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            String message) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/mcp") || path.startsWith("/mcp/")) {
            response.setHeader("WWW-Authenticate",
                    "Bearer resource_metadata=\"" + oauthProperties.protectedResourceMetadataUrl()
                            + "\", scope=\"mcp\"");
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
