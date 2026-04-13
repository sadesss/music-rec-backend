package com.example.musicrec.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final SessionAuthenticationFilter sessionAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/auth",
                        "/auth.html",
                        "/player",
                        "/player.html",
                        "/admin",
                        "/admin",
                        "/demo.html",
                        "/css/**",
                        "/js/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        "/favicon.ico"
                ).permitAll()

                .requestMatchers("/api/v1/auth/**").permitAll()

                // Важно: stream через <audio src="..."> не может отправить X-Session-Token.
                // Поэтому GET-треки/стрим пока оставляем открытыми.
                .requestMatchers(HttpMethod.GET, "/api/v1/tracks/**").permitAll()

                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                .requestMatchers("/api/v1/interactions/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/v1/ratings/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/v1/recommendations/**").permitAll()

                // Старый create-user API пользователю больше не нужен
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")

                .anyRequest().authenticated()
        );

        http.addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    /**
     * Нужен только чтобы Spring не создавал дефолтного in-memory пользователя
     * и не печатал generated security password.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Username/password auth is not used directly");
        };
    }
}