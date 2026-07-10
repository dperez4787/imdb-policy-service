package org.perez_f_daniel.imdb.policy.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With an allowlist configured, registration requires a verifiable
 * Google-signed ID token: no token and garbage tokens are rejected before
 * any state changes. (The accept path runs against real Google-signed
 * tokens in the deploy smoke test — it can't be minted offline.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "policy.registration.allowed-emails=imdbfed-run@example.iam.gserviceaccount.com")
class RegistrationAuthIT {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("imdb_governance"));
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void registrationWithoutTokenIs401() {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/v1/registrations/ratings", HttpMethod.PUT,
                new HttpEntity<>(payload(), jsonHeaders(null)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registrationWithGarbageTokenIs401() {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/v1/registrations/ratings", HttpMethod.PUT,
                new HttpEntity<>(payload(), jsonHeaders("not-a-jwt")), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void readSurfacesStayOpen() {
        assertThat(rest.getForEntity("/v1/bundle", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private static Map<String, Object> payload() {
        return Map.of("fields", List.of(Map.of("coordinate", "Rating.numVotes")));
    }

    private static HttpHeaders jsonHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return headers;
    }
}
