package org.perez_f_daniel.imdb.policy.token;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.validation.constraints.NotBlank;
import org.perez_f_daniel.imdb.policy.domain.Persona;
import org.perez_f_daniel.imdb.policy.domain.PersonaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Mints short-lived persona JWTs for demos and the future admin-UI
 * playground. The router trusts these via a JWKS provider entry pointing at
 * this service's /.well-known/jwks.json with the configured audience.
 */
@RestController
public class TokenController {

    private final PersonaRepository personas;
    private final JwtKeys keys;
    private final String issuer;
    private final String audience;
    private final Duration ttl;

    public TokenController(PersonaRepository personas, JwtKeys keys,
                           @Value("${policy.jwt.issuer}") String issuer,
                           @Value("${policy.jwt.audience}") String audience,
                           @Value("${policy.jwt.ttl}") Duration ttl) {
        this.personas = personas;
        this.keys = keys;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    public record TokenRequest(@NotBlank String persona) {
    }

    public record TokenResponse(String token, String persona, List<String> roles, Instant expiresAt) {
    }

    @PostMapping("/v1/token")
    public TokenResponse mint(@RequestBody TokenRequest request) throws Exception {
        Persona persona = personas.findById(request.persona()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "unknown persona: " + request.persona()));

        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("persona:" + persona.getId())
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .claim("roles", persona.getRoles())
                .claim("persona", persona.getId())
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(keys.signingKey().getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(keys.signingKey()));
        return new TokenResponse(jwt.serialize(), persona.getId(), persona.getRoles(), expiry);
    }
}
