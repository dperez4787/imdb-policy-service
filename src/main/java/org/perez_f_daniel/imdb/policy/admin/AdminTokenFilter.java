package org.perez_f_daniel.imdb.policy.admin;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Guards /v1/admin/** with a static bearer token (POLICY_ADMIN_TOKEN).
 * With no token configured the admin API is open — acceptable for local dev,
 * loudly warned about at startup. Production gets a real admin identity when
 * the UI lands (phase 4).
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

    private final String adminToken;

    public AdminTokenFilter(@Value("${policy.admin.token:}") String adminToken) {
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    @PostConstruct
    void warnIfOpen() {
        if (adminToken.isBlank()) {
            log.warn("POLICY_ADMIN_TOKEN not set: /v1/admin/** is UNPROTECTED");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (adminToken.isBlank() || presentedTokenMatches(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"admin token required\"}");
    }

    private boolean presentedTokenMatches(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String presented = header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length())
                : request.getHeader("X-Admin-Token");
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8));
    }
}
