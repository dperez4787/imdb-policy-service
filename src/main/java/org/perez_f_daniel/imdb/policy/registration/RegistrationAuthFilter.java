package org.perez_f_daniel.imdb.policy.registration;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.perez_f_daniel.imdb.policy.core.GoogleIdentityVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards /v1/registrations/** by verifying Google-signed ID tokens — the
 * subgraphs register as their Cloud Run runtime SA with zero shared secrets.
 * The allowlist (POLICY_REGISTRATION_ALLOWED_EMAILS, CSV) names the SA
 * emails permitted to register; empty means open (local dev), warned at
 * startup. An open registration surface would let anyone orphan governed
 * fields and so strip enforcement — never leave it open in production.
 */
@Component
public class RegistrationAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RegistrationAuthFilter.class);

    private final Set<String> allowedEmails;
    private final GoogleIdentityVerifier verifier;

    public RegistrationAuthFilter(
            @Value("${policy.registration.allowed-emails:}") String allowedEmailsCsv,
            GoogleIdentityVerifier verifier) {
        this.allowedEmails = Arrays.stream(allowedEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.verifier = verifier;
    }

    @PostConstruct
    void warnIfOpen() {
        if (allowedEmails.isEmpty()) {
            log.warn("POLICY_REGISTRATION_ALLOWED_EMAILS not set: /v1/registrations/** is UNPROTECTED");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/registrations/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean allowed = allowedEmails.isEmpty()
                || verifier.verifiedEmail(request).map(allowedEmails::contains).orElse(false);
        if (allowed) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"a Google-signed ID token from an allowed identity is required\"}");
    }
}
