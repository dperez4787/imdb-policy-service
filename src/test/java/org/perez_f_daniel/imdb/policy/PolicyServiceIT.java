package org.perez_f_daniel.imdb.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * One ordered flow over a real Mongo: seed -> bundle/ETag -> persona token
 * verified against the served JWKS -> registration lifecycle (add, idempotent
 * re-PUT, orphan) -> admin policy lifecycle behind the admin token -> persona
 * subjects in the principals map -> audit trail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "policy.admin.token=test-admin-secret")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PolicyServiceIT {

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

    // ---- helpers ---------------------------------------------------------

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-admin-secret");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Actor", "it-test");
        return headers;
    }

    private JsonNode bundle() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/v1/bundle", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    // ---- seed + bundle ---------------------------------------------------

    @Test
    @Order(1)
    void seededBundleGovernsDemoFields() {
        JsonNode bundle = bundle();

        assertThat(bundle.get("revision").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(bundle.get("defaultPosture").asText()).isEqualTo("allow-unless-governed");

        JsonNode numVotes = bundle.get("fields").get("Rating.numVotes");
        assertThat(numVotes.get("subgraph").asText()).isEqualTo("ratings");
        assertThat(numVotes.get("allowedRoles").toString()).contains("analyst");

        // Governed but never granted: present, deny-everyone.
        JsonNode deathYear = bundle.get("fields").get("Name.deathYear");
        assertThat(deathYear.get("allowedRoles")).isEmpty();
    }

    @Test
    @Order(2)
    void bundleReturns304ForCurrentEtag() {
        ResponseEntity<JsonNode> first = rest.getForEntity("/v1/bundle", JsonNode.class);
        String etag = first.getHeaders().getETag();
        assertThat(etag).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setIfNoneMatch(etag);
        ResponseEntity<Void> second = rest.exchange(
                "/v1/bundle", HttpMethod.GET, new HttpEntity<>(headers), Void.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    // ---- persona tokens + JWKS -------------------------------------------

    @Test
    @Order(3)
    void mintedPersonaTokenVerifiesAgainstServedJwks() throws Exception {
        ResponseEntity<JsonNode> minted = rest.postForEntity(
                "/v1/token", Map.of("persona", "analyst"), JsonNode.class);
        assertThat(minted.getStatusCode()).isEqualTo(HttpStatus.OK);

        SignedJWT jwt = SignedJWT.parse(minted.getBody().get("token").asText());

        String jwksJson = rest.getForObject("/.well-known/jwks.json", String.class);
        RSAKey publicKey = (RSAKey) JWKSet.parse(jwksJson)
                .getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(publicKey).isNotNull();
        assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();

        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).contains("analyst", "public");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("imdb-router");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("persona:analyst");
    }

    @Test
    @Order(4)
    void unknownPersonaIs404() {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/v1/token", Map.of("persona", "nobody"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- registration lifecycle ------------------------------------------

    @Test
    @Order(5)
    void registrationAddsNewGovernedFieldDenyByDefault() {
        long before = bundle().get("revision").asLong();

        ResponseEntity<JsonNode> response = rest.exchange(
                "/v1/registrations/ratings", HttpMethod.PUT,
                new HttpEntity<>(registration("Rating.numVotes", "Rating.votesByRegion"), jsonHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("added").asInt()).isEqualTo(1);
        assertThat(response.getBody().get("changed").asBoolean()).isTrue();

        JsonNode bundle = bundle();
        assertThat(bundle.get("revision").asLong()).isGreaterThan(before);
        // Newly declared field is born locked: governed, no grants.
        assertThat(bundle.get("fields").get("Rating.votesByRegion").get("allowedRoles")).isEmpty();
        // Pre-existing grant survives the seed->registration source flip.
        assertThat(bundle.get("fields").get("Rating.numVotes").get("allowedRoles").toString())
                .contains("analyst");
    }

    @Test
    @Order(6)
    void reRegisteringTheSameSetIsANoOp() {
        ResponseEntity<JsonNode> first = rest.exchange(
                "/v1/registrations/ratings", HttpMethod.PUT,
                new HttpEntity<>(registration("Rating.numVotes", "Rating.votesByRegion"), jsonHeaders()),
                JsonNode.class);
        assertThat(first.getBody().get("changed").asBoolean()).isFalse();

        long revision = first.getBody().get("revision").asLong();
        assertThat(bundle().get("revision").asLong()).isEqualTo(revision);
    }

    @Test
    @Order(7)
    void undeclaredFieldIsOrphanedOutOfTheBundle() {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/v1/registrations/ratings", HttpMethod.PUT,
                new HttpEntity<>(registration("Rating.numVotes"), jsonHeaders()),
                JsonNode.class);
        assertThat(response.getBody().get("orphaned").asInt()).isEqualTo(1);

        assertThat(bundle().get("fields").has("Rating.votesByRegion")).isFalse();
        assertThat(bundle().get("fields").has("Rating.numVotes")).isTrue();
    }

    // ---- admin plane -------------------------------------------------------

    @Test
    @Order(8)
    void adminEndpointsRequireTheAdminToken() {
        ResponseEntity<JsonNode> unauthenticated = rest.exchange(
                "/v1/admin/policies/Name.deathYear", HttpMethod.PUT,
                new HttpEntity<>(Map.of("allowedRoles", List.of("analyst")), jsonHeaders()),
                JsonNode.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(9)
    void grantingAndRevokingAPolicyFlowsIntoTheBundle() {
        ResponseEntity<JsonNode> granted = rest.exchange(
                "/v1/admin/policies/Name.deathYear", HttpMethod.PUT,
                new HttpEntity<>(Map.of("allowedRoles", List.of("analyst")), adminHeaders()),
                JsonNode.class);
        assertThat(granted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bundle().get("fields").get("Name.deathYear").get("allowedRoles").toString())
                .contains("analyst");

        ResponseEntity<JsonNode> revoked = rest.exchange(
                "/v1/admin/policies/Name.deathYear", HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), JsonNode.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bundle().get("fields").get("Name.deathYear").get("allowedRoles")).isEmpty();
    }

    @Test
    @Order(10)
    void policiesCanOnlyTargetDeclaredCoordinates() {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/v1/admin/policies/Title.primaryTitle", HttpMethod.PUT,
                new HttpEntity<>(Map.of("allowedRoles", List.of("analyst")), adminHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(11)
    void personaSubjectsAppearInThePrincipalsMap() {
        ResponseEntity<JsonNode> updated = rest.exchange(
                "/v1/admin/personas/analyst", HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "displayName", "Ratings analyst",
                        "roles", List.of("analyst", "public"),
                        "subjects", List.of("perez.f.danny@gmail.com")), adminHeaders()),
                JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode principals = bundle().get("principals");
        assertThat(principals.get("perez.f.danny@gmail.com").toString()).contains("analyst");
    }

    @Test
    @Order(12)
    void auditTrailRecordsEveryMutation() {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/v1/admin/audit", HttpMethod.GET, new HttpEntity<>(adminHeaders()), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String actions = response.getBody().toString();
        assertThat(actions).contains("seed.applied");
        assertThat(actions).contains("registration.applied");
        assertThat(actions).contains("policy.updated");
        assertThat(actions).contains("policy.deleted");
        assertThat(actions).contains("persona.updated");
    }

    // ---- UI support surfaces ----------------------------------------------

    @Test
    @Order(13)
    void publicPersonaListExposesRolesButNeverSubjects() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/v1/personas", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody().toString();
        assertThat(body).contains("analyst");
        assertThat(body).doesNotContain("subjects");
        assertThat(body).doesNotContain("perez.f.danny@gmail.com");
    }

    @Test
    @Order(14)
    void uiConfigAndStaticShellAreServed() {
        assertThat(rest.getForEntity("/v1/ui-config", JsonNode.class).getBody().get("routerUrl").asText())
                .startsWith("https://");
        ResponseEntity<String> index = rest.getForEntity("/", String.class);
        assertThat(index.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(index.getBody()).contains("IMDb Graph Governance");
    }

    // ---- request builders --------------------------------------------------

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static Map<String, Object> registration(String... coordinates) {
        return Map.of("fields", List.of(coordinates).stream()
                .map(coordinate -> Map.of("coordinate", coordinate, "reason", "it-test"))
                .toList());
    }
}
