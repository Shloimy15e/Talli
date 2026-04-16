package dev.dynamiq.talli.config;

import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/logo.svg", "/favicon.ico", "/css/**", "/js/**", "/login", "/invite/**").permitAll()
                // Permission-based access — matches the seeded permission names.
                .requestMatchers("/portal/**").hasAuthority("portal-access")
                .requestMatchers("/admin/users/**").hasAuthority("manage-users")
                .anyRequest().hasAuthority("view-dashboard")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logged-out")
                .permitAll()
            )
            .rememberMe(rm -> rm.key("talli-remember-me").tokenValiditySeconds(60 * 60 * 24 * 30));
        return http.build();
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
