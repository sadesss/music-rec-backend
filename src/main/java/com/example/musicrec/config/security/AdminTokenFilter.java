package com.example.musicrec.config.security;

import com.example.musicrec.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AdminTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Admin-Token";
    private final AppProperties props;

    public AdminTokenFilter(AppProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.getSecurity().isAdminTokenEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String expected = props.getSecurity().getAdminToken();
        String actual = request.getHeader(HEADER);

        if (expected == null || expected.isBlank()) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.getWriter().write("Admin token is enabled but not configured.");
            return;
        }

        if (actual == null || !expected.equals(actual)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Missing/invalid X-Admin-Token.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
