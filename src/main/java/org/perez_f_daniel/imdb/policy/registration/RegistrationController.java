package org.perez_f_daniel.imdb.policy.registration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Subgraphs call this at startup with the full set of @governed declarations
 * from their schema. Idempotent: re-registering an unchanged set is a no-op
 * (no revision bump, no audit entry).
 */
@RestController
public class RegistrationController {

    /** GraphQL Name.Name, e.g. "Rating.numVotes". */
    private static final String COORDINATE_PATTERN = "[_A-Za-z][_0-9A-Za-z]*\\.[_A-Za-z][_0-9A-Za-z]*";

    private final RegistrationService registrations;

    public RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    public record DeclaredField(
            @NotNull @Pattern(regexp = COORDINATE_PATTERN) String coordinate,
            String reason) {
    }

    public record RegistrationRequest(@NotNull @Valid List<DeclaredField> fields) {
    }

    public record RegistrationResult(
            String subgraph, int added, int updated, int orphaned, boolean changed, long revision) {
    }

    @PutMapping("/v1/registrations/{subgraph}")
    public RegistrationResult register(
            @PathVariable @Pattern(regexp = "[a-z][a-z0-9-]*") String subgraph,
            @RequestHeader(value = "X-Registered-By", required = false) String registeredBy,
            @Valid @RequestBody RegistrationRequest request) {
        String actor = registeredBy != null ? registeredBy : "subgraph:" + subgraph;
        return registrations.apply(subgraph, request.fields(), actor);
    }
}
