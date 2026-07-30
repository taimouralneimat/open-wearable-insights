package com.openwearableinsights.api.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces the single-user local API token (ADR-0008) on every request under
 * {@code /api/v1/**}.
 *
 * <p>Requests must carry the {@code X-Local-Api-Token} header with a value
 * matching the token persisted by {@link LocalApiTokenStore}; otherwise the
 * request is rejected with 401 and a small JSON error body. Paths outside
 * {@code /api/v1/**} (health/info actuator endpoints, Swagger UI, OpenAPI
 * docs) carry no personal data and are left open — see
 * {@link #shouldNotFilter(HttpServletRequest)}.
 */
@Component
public class LocalApiTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Local-Api-Token";
    private static final String PROTECTED_PREFIX = "/api/v1/";

    private final LocalApiTokenStore tokenStore;

    public LocalApiTokenAuthFilter(LocalApiTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER_NAME);
        if (!tokenStore.matches(header)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"Missing or invalid X-Local-Api-Token header\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
