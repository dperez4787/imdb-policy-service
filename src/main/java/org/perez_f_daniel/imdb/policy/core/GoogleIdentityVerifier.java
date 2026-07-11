package org.perez_f_daniel.imdb.policy.core;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies Google-signed ID tokens (service accounts and gcloud users) and
 * returns the verified email claim. Shared by every surface that gates on a
 * Google identity: subgraph registrations and the bundle's principals map.
 */
@Component
public class GoogleIdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityVerifier.class);
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private final String requiredAudience;
    private final ConfigurableJWTProcessor<SecurityContext> processor;

    public GoogleIdentityVerifier(
            @Value("${policy.google-auth.audience:}") String requiredAudience) throws Exception {
        this.requiredAudience = requiredAudience == null ? "" : requiredAudience.trim();
        JWKSource<SecurityContext> keySource = JWKSourceBuilder
                .create(new URL(GOOGLE_JWKS))
                .build();
        this.processor = new DefaultJWTProcessor<>();
        this.processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
    }

    /** The verified Google email of the bearer, or empty if absent/invalid. */
    public Optional<String> verifiedEmail(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = processor.process(header.substring("Bearer ".length()), null);
            if (!GOOGLE_ISSUERS.contains(claims.getIssuer())) {
                return Optional.empty();
            }
            if (!requiredAudience.isEmpty()) {
                List<String> audience = claims.getAudience();
                if (audience == null || !audience.contains(requiredAudience)) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(claims.getStringClaim("email"));
        } catch (Exception e) {
            log.debug("Google ID token rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
