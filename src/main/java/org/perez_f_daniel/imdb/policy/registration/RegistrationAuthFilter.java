package org.perez_f_daniel.imdb.policy.registration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
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
import java.net.URL;
import java.util.Arrays;
import java.util.List;
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
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private final Set<String> allowedEmails;
    private final String requiredAudience;
    private final ConfigurableJWTProcessor<SecurityContext> processor;

    public RegistrationAuthFilter(
            @Value("${policy.registration.allowed-emails:}") String allowedEmailsCsv,
            @Value("${policy.registration.audience:}") String requiredAudience) throws Exception {
        this.allowedEmails = Arrays.stream(allowedEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.requiredAudience = requiredAudience == null ? "" : requiredAudience.trim();

        JWKSource<SecurityContext> keySource = JWKSourceBuilder
                .create(new URL(GOOGLE_JWKS))
                .build();
        this.processor = new DefaultJWTProcessor<>();
        this.processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
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
        if (allowedEmails.isEmpty() || callerIsAllowed(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"a Google-signed ID token from an allowed identity is required\"}");
    }

    private boolean callerIsAllowed(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        try {
            JWTClaimsSet claims = processor.process(header.substring("Bearer ".length()), null);
            if (!GOOGLE_ISSUERS.contains(claims.getIssuer())) {
                return false;
            }
            if (!requiredAudience.isEmpty()) {
                List<String> audience = claims.getAudience();
                if (audience == null || !audience.contains(requiredAudience)) {
                    return false;
                }
            }
            String email = claims.getStringClaim("email");
            return email != null && allowedEmails.contains(email);
        } catch (Exception e) {
            log.info("registration token rejected: {}", e.getMessage());
            return false;
        }
    }
}
