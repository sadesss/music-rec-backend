package com.example.musicrec.config.security;

import com.example.musicrec.domain.User;
import com.example.musicrec.domain.UserSession;
import com.example.musicrec.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String SESSION_HEADER = "X-Session-Token";

    private final UserSessionRepository userSessionRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.equals("/")
                || path.equals("/auth")
                || path.equals("/auth.html")
                || path.equals("/player")
                || path.equals("/player.html")
                || path.equals("/demo.html")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(SESSION_HEADER);

        if (token != null && !token.isBlank()) {
            UserSession session = userSessionRepository.findBySessionToken(token).orElse(null);

            if (session != null && session.getExpiresAt().isAfter(Instant.now())) {
                User user = session.getUser();

                AppPrincipal principal = new AppPrincipal(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRole()
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}