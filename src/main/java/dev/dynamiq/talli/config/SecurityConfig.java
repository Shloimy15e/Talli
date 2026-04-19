package dev.dynamiq.talli.config;

import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.web.cors.CorsConfiguration;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;

    public SecurityConfig(ApiTokenAuthenticationFilter apiTokenAuthenticationFilter) {
        this.apiTokenAuthenticationFilter = apiTokenAuthenticationFilter;
    }

    /**
     * API filter chain — stateless, no CSRF, Bearer token auth.
     * Matches /api/** paths before the web chain gets a chance.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .cors(cors -> cors.configurationSource(request -> {
                var config = new CorsConfiguration();
                config.addAllowedOriginPattern("chrome-extension://*");
                config.addAllowedOriginPattern("http://localhost:*");
                config.addAllowedMethod("*");
                config.addAllowedHeader("*");
                config.setMaxAge(3600L);
                return config;
            }))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,  "/api/v1/projects").hasAuthority("view-projects")
                .requestMatchers(HttpMethod.POST, "/api/v1/projects").hasAuthority("manage-projects")
                .requestMatchers(HttpMethod.GET,  "/api/v1/time/**").hasAuthority("view-time")
                .requestMatchers(HttpMethod.POST, "/api/v1/time/**").hasAuthority("manage-time")
                .requestMatchers(HttpMethod.POST, "/api/v1/expenses").hasAuthority("manage-expenses")
                .requestMatchers(HttpMethod.GET,  "/api/v1/clients/**").hasAuthority("view-clients")
                .requestMatchers(HttpMethod.POST, "/api/v1/clients").hasAuthority("manage-clients")
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
            );
        return http.build();
    }

    /** Web filter chain — session-based, CSRF enabled, form login. */
    /**
     * Webhook filter chain — no session, no CSRF, no auth (handlers verify signatures themselves).
     * Sits between the API and web chains so /webhooks/** doesn't hit form login.
     */
    @Bean
    @Order(2)
    SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/webhooks/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain filterChain(HttpSecurity http, PersistentTokenRepository persistentTokenRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/logo.svg", "/favicon.ico", "/css/**", "/js/**", "/login", "/invite/**").permitAll()

                // Portal — client-facing
                .requestMatchers("/portal/**").hasAuthority("portal-access")

                // Admin
                .requestMatchers("/admin/users/**").hasAuthority("manage-users")
                .requestMatchers("/admin/import/**").hasAuthority("manage-users")
                .requestMatchers("/admin/migration/**").hasAuthority("manage-users")

                // Clients — POST = write, GET = read
                .requestMatchers(HttpMethod.POST, "/clients/*/send-reminder").hasAuthority("send-emails")
                .requestMatchers(HttpMethod.POST, "/clients", "/clients/*/delete", "/clients/*").hasAuthority("manage-clients")
                .requestMatchers(HttpMethod.GET, "/clients/**").hasAuthority("view-clients")

                // Projects
                .requestMatchers(HttpMethod.POST, "/projects", "/projects/*/delete", "/projects/*", "/projects/*/change-contract").hasAuthority("manage-projects")
                .requestMatchers(HttpMethod.GET, "/projects/**").hasAuthority("view-projects")

                // Time entries
                .requestMatchers(HttpMethod.POST, "/time", "/time/*/delete", "/time/*", "/time/*/stop").hasAuthority("manage-time")
                .requestMatchers(HttpMethod.GET, "/time/**").hasAuthority("view-time")

                // Expenses
                .requestMatchers(HttpMethod.POST, "/expenses", "/expenses/*/delete", "/expenses/*").hasAuthority("manage-expenses")
                .requestMatchers(HttpMethod.GET, "/expenses/**").hasAuthority("view-expenses")

                // Invoices — generate, void, delete, create, email, pdf
                .requestMatchers(HttpMethod.POST, "/invoices", "/invoices/generate", "/invoices/generate-fixed", "/invoices/*/void", "/invoices/*/delete", "/invoices/*/pdf", "/invoices/*/attachments").hasAuthority("manage-invoices")
                .requestMatchers(HttpMethod.POST, "/invoices/*/email").hasAuthority("send-emails")
                .requestMatchers(HttpMethod.GET, "/invoices/**").hasAuthority("view-invoices")

                // Payments
                .requestMatchers(HttpMethod.POST, "/invoices/*/payments", "/invoices/*/payments/*/delete", "/invoices/*/apply-credit").hasAuthority("manage-payments")

                // Client credits
                .requestMatchers(HttpMethod.POST, "/clients/*/credits", "/clients/*/credits/*/delete").hasAuthority("manage-payments")

                // Subscriptions
                .requestMatchers(HttpMethod.POST, "/subscriptions", "/subscriptions/*/delete", "/subscriptions/*").hasAuthority("manage-expenses")
                .requestMatchers(HttpMethod.GET, "/subscriptions/**").hasAuthority("view-expenses")

                // Emails — standalone compose page is admin-only; invoice-specific send uses send-emails
                .requestMatchers("/emails/**").hasRole("admin")

                // Reports
                .requestMatchers("/reports/**").hasAuthority("view-reports")

                // Dashboard, profile, media — any authenticated user with dashboard access
                .requestMatchers("/dashboard").hasAuthority("view-dashboard")
                .requestMatchers("/profile", "/profile/**").authenticated()
                .requestMatchers("/media/**").authenticated()

                .anyRequest().hasAuthority("view-dashboard")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    // Route client-role users to portal, everyone else to dashboard.
                    boolean isClientOnly = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("portal-access"))
                            && authentication.getAuthorities().stream()
                            .noneMatch(a -> a.getAuthority().equals("view-dashboard"));
                    response.sendRedirect(isClientOnly ? "/portal" : "/dashboard");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logged-out")
                .permitAll()
            )
            .rememberMe(rm -> rm
                .key("talli-remember-me")
                .tokenRepository(persistentTokenRepository)
                .tokenValiditySeconds(60 * 60 * 24 * 30));
        return http.build();
    }

    /**
     * Persistent remember-me tokens — stored in `persistent_logins` so individual
     * devices can be revoked (delete the row) without invalidating everyone.
     */
    @Bean
    PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        return repo;
    }

    /**
     * Build Spring Security authorities from both roles (ROLE_admin, ROLE_bookkeeper)
     * and permissions (view-dashboard, manage-invoices, etc.) — like Spatie's dual
     * approach where you can check either hasRole or hasAuthority.
     */
    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmail(email)
            .map(u -> {
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                // Roles — prefixed with ROLE_ for Spring's hasRole() checks.
                u.roleNames().forEach(r ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                // Permissions — raw names for hasAuthority() checks.
                u.allPermissions().forEach(p ->
                    authorities.add(new SimpleGrantedAuthority(p)));

                return User.withUsername(u.getEmail())
                    .password(u.getPassword())
                    .authorities(authorities)
                    .disabled(!u.getEnabled())
                    .build();
            })
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
