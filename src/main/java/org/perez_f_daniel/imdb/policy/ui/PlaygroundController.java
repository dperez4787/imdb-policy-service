package org.perez_f_daniel.imdb.policy.ui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * UI support: config discovery and the playground proxy. The proxy forwards
 * a GraphQL operation to the ONE configured router (never a caller-supplied
 * URL) with the caller's bearer token, and returns the router's response
 * plus its X-Imdb-Policy-Revision header — same-origin for the UI, so the
 * demo doesn't depend on router CORS settings.
 */
@RestController
public class PlaygroundController {

    private final String routerUrl;
    private final RestClient router;

    public PlaygroundController(@Value("${policy.ui.router-url}") String routerUrl) {
        this.routerUrl = routerUrl;
        this.router = RestClient.builder().baseUrl(routerUrl).build();
    }

    @GetMapping("/v1/ui-config")
    public Map<String, String> config() {
        return Map.of("routerUrl", routerUrl);
    }

    public record PlaygroundRequest(String token, String query, Map<String, Object> variables) {
    }

    @PostMapping("/v1/playground/query")
    public ResponseEntity<String> run(@RequestBody PlaygroundRequest request) {
        try {
            ResponseEntity<String> upstream = router.post()
                    .uri("/graphql")
                    .header("Authorization", "Bearer " + (request.token() == null ? "" : request.token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "query", request.query() == null ? "" : request.query(),
                            "variables", request.variables() == null ? Map.of() : request.variables()))
                    .retrieve()
                    .toEntity(String.class);
            return withRevision(upstream.getStatusCode().value(), upstream.getBody(),
                    upstream.getHeaders().getFirst("X-Imdb-Policy-Revision"));
        } catch (RestClientResponseException e) {
            // 401/403 from the router are the demo's point — pass them through.
            return withRevision(e.getStatusCode().value(), e.getResponseBodyAsString(),
                    e.getResponseHeaders() == null ? null
                            : e.getResponseHeaders().getFirst("X-Imdb-Policy-Revision"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"errors\":[{\"message\":\"router unreachable: " + e.getMessage() + "\"}]}");
        }
    }

    private static ResponseEntity<String> withRevision(int status, String body, String revision) {
        var builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (revision != null) {
            builder = builder.header("X-Imdb-Policy-Revision", revision);
        }
        return builder.body(body == null ? "{}" : body);
    }
}
