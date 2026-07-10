package org.perez_f_daniel.imdb.policy.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * The RS256 signing key behind persona tokens and the JWKS endpoint.
 *
 * Production: set POLICY_JWT_PRIVATE_KEY to a PKCS#8 PEM (from Secret
 * Manager) so the key survives restarts and scale-out. Without it, an
 * ephemeral keypair is generated at boot — fine for local dev, but minted
 * tokens die with the instance and multi-instance deployments would publish
 * conflicting JWKS.
 */
@Component
public class JwtKeys {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);

    private final RSAKey rsaKey;

    public JwtKeys(@Value("${policy.jwt.private-key-pem:}") String privateKeyPem) throws Exception {
        KeyPair keyPair = privateKeyPem == null || privateKeyPem.isBlank()
                ? generateEphemeral()
                : fromPkcs8Pem(privateKeyPem);
        RSAKey base = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        this.rsaKey = new RSAKey.Builder(base)
                .keyID(base.computeThumbprint().toString())
                .build();
    }

    private static KeyPair generateEphemeral() throws Exception {
        log.warn("POLICY_JWT_PRIVATE_KEY not set: generated an EPHEMERAL signing key; "
                + "persona tokens will not survive a restart");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static KeyPair fromPkcs8Pem(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey =
                (RSAPrivateCrtKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }

    /** Full key including the private part — for signing only. */
    public RSAKey signingKey() {
        return rsaKey;
    }

    /** What /.well-known/jwks.json serves. */
    public JWKSet publicJwks() {
        return new JWKSet(rsaKey.toPublicJWK());
    }
}
