package dev.dynamiq.talli.config;

import dev.dynamiq.talli.model.PersonalAccessToken;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.service.ApiTokenService;
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

/**
 * Authenticates API requests (paths starting with /api/) using Bearer tokens.
 * Builds the same authorities (roles + permissions) as the session-based
 * UserDetailsService so that @PreAuthorize / hasAuthority checks work identically.
 */
@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private final ApiTokenService apiTokenService;

    public ApiTokenAuthenticationFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String rawToken = authHeader.substring(7);
        var maybePat = apiTokenService.authenticate(rawToken);

        if (maybePat.isEmpty()) {
            sendUnauthorized(response, "Invalid API token");
            return;
        }

        PersonalAccessToken pat = maybePat.get();
        User user = pat.getUser();

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

    /** Only apply this filter to /api/** paths, and skip CORS preflight (OPTIONS) requests. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
