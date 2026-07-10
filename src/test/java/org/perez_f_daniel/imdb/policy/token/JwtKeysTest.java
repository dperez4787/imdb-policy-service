package org.perez_f_daniel.imdb.policy.token;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtKeysTest {

    @Test
    void ephemeralKeyServesSigningKeyAndPublicJwks() throws Exception {
        JwtKeys keys = new JwtKeys("");

        assertThat(keys.signingKey().isPrivate()).isTrue();
        assertThat(keys.signingKey().getKeyID()).isNotBlank();
        assertThat(keys.publicJwks().getKeys()).hasSize(1);
        assertThat(keys.publicJwks().getKeys().get(0).isPrivate()).isFalse();
        assertThat(keys.publicJwks().getKeys().get(0).getKeyID())
                .isEqualTo(keys.signingKey().getKeyID());
    }

    @Test
    void pemKeyIsStableAcrossRestarts() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        JwtKeys first = new JwtKeys(pem);
        JwtKeys second = new JwtKeys(pem);

        // Same key material -> same RFC 7638 thumbprint kid: tokens minted
        // before a restart still validate against the republished JWKS.
        assertThat(first.signingKey().getKeyID()).isEqualTo(second.signingKey().getKeyID());
        assertThat(first.publicJwks().toJSONObject()).isEqualTo(second.publicJwks().toJSONObject());
    }
}
