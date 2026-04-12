package com.example.musicrec.config.security;

import com.example.musicrec.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties props;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // ⚠️ Placeholder configuration:
        // - Disable CSRF for simplicity (especially for file upload / dev usage)
        // - Permit all user endpoints
        // - Optionally gate /api/admin/** via a custom filter (X-Admin-Token)
        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(auth -> auth
                // Swagger/OpenAPI endpoints
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Static demo files
                .requestMatchers("/", "/demo.html", "/js/**").permitAll()
                // Health
                .requestMatchers("/actuator/health").permitAll()
                // Everything else permitted (admin is guarded by filter if enabled)
                .requestMatchers("/", "/demo.html", "/auth.html", "/user.html", "/js/**", "/css/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().permitAll()
        );

        // Add admin token filter (only active if app.security.admin-token-enabled=true)
        http.addFilterBefore(new AdminTokenFilter(props), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        // No auth mechanisms configured (placeholder)
        http.httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
