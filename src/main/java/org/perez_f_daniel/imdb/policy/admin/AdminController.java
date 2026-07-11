package org.perez_f_daniel.imdb.policy.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.perez_f_daniel.imdb.policy.core.AuditService;
import org.perez_f_daniel.imdb.policy.core.RevisionService;
import org.perez_f_daniel.imdb.policy.domain.AuditEntry;
import org.perez_f_daniel.imdb.policy.domain.AuditEntryRepository;
import org.perez_f_daniel.imdb.policy.domain.FieldPolicy;
import org.perez_f_daniel.imdb.policy.domain.FieldPolicyRepository;
import org.perez_f_daniel.imdb.policy.domain.GovernedField;
import org.perez_f_daniel.imdb.policy.domain.GovernedFieldRepository;
import org.perez_f_daniel.imdb.policy.domain.Persona;
import org.perez_f_daniel.imdb.policy.domain.PersonaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The admin plane the phase-4 UI will sit on. Policies can only target
 * coordinates a subgraph has declared — governance stays schema-driven;
 * the admin decides WHO, the schema decides WHAT.
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final GovernedFieldRepository governedFields;
    private final FieldPolicyRepository policies;
    private final PersonaRepository personas;
    private final AuditEntryRepository auditEntries;
    private final RevisionService revisions;
    private final AuditService audit;

    public AdminController(GovernedFieldRepository governedFields, FieldPolicyRepository policies,
                           PersonaRepository personas, AuditEntryRepository auditEntries,
                           RevisionService revisions, AuditService audit) {
        this.governedFields = governedFields;
        this.policies = policies;
        this.personas = personas;
        this.auditEntries = auditEntries;
        this.revisions = revisions;
        this.audit = audit;
    }

    // ---- overview -------------------------------------------------------

    public record FieldOverview(String coordinate, String subgraph, String reason,
                                GovernedField.Status status, String source,
                                Instant firstSeenAt, Instant lastRegisteredAt,
                                List<String> allowedRoles, boolean policyEnabled,
                                Instant policyUpdatedAt, String policyUpdatedBy) {
    }

    @GetMapping("/overview")
    public List<FieldOverview> overview() {
        Map<String, FieldPolicy> byCoordinate = policies.findAll().stream()
                .collect(Collectors.toMap(FieldPolicy::getCoordinate, Function.identity()));
        return governedFields.findAll().stream()
                .map(gf -> {
                    FieldPolicy p = byCoordinate.get(gf.getCoordinate());
                    return new FieldOverview(gf.getCoordinate(), gf.getSubgraph(), gf.getReason(),
                            gf.getStatus(), gf.getSource(),
                            gf.getFirstSeenAt(), gf.getLastRegisteredAt(),
                            p == null ? List.of() : p.getAllowedRoles(),
                            p != null && p.isEnabled(),
                            p == null ? null : p.getUpdatedAt(),
                            p == null ? null : p.getUpdatedBy());
                })
                .toList();
    }

    // ---- policies -------------------------------------------------------

    public record PolicyUpdate(@NotNull List<String> allowedRoles, Boolean enabled) {
    }

    @PutMapping("/policies/{coordinate}")
    public FieldPolicy putPolicy(@PathVariable String coordinate,
                                 @Valid @RequestBody PolicyUpdate update,
                                 @RequestHeader(value = "X-Admin-Actor", required = false) String actor) {
        if (governedFields.findById(coordinate).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "not a governed field: " + coordinate
                            + " (fields become governable via subgraph @governed declarations)");
        }
        FieldPolicy policy = new FieldPolicy(coordinate, List.copyOf(update.allowedRoles()),
                update.enabled() == null || update.enabled(),
                actorOrAnonymous(actor), Instant.now());
        policies.save(policy);
        long revision = revisions.bump();
        audit.record(actorOrAnonymous(actor), "policy.updated", coordinate,
                "allowedRoles=" + policy.getAllowedRoles() + " enabled=" + policy.isEnabled(), revision);
        return policy;
    }

    @DeleteMapping("/policies/{coordinate}")
    public Map<String, Object> deletePolicy(@PathVariable String coordinate,
                                            @RequestHeader(value = "X-Admin-Actor", required = false) String actor) {
        if (policies.findById(coordinate).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no policy for: " + coordinate);
        }
        policies.deleteById(coordinate);
        long revision = revisions.bump();
        audit.record(actorOrAnonymous(actor), "policy.deleted", coordinate,
                "field reverts to deny-everyone", revision);
        return Map.of("deleted", coordinate, "revision", revision);
    }

    // ---- personas -------------------------------------------------------

    public record PersonaUpdate(String displayName, @NotNull List<String> roles, List<String> subjects) {
    }

    @GetMapping("/personas")
    public List<Persona> listPersonas() {
        return personas.findAll();
    }

    @PutMapping("/personas/{id}")
    public Persona putPersona(@PathVariable String id,
                              @Valid @RequestBody PersonaUpdate update,
                              @RequestHeader(value = "X-Admin-Actor", required = false) String actor) {
        Persona persona = new Persona(id, update.displayName(), List.copyOf(update.roles()),
                update.subjects() == null ? List.of() : List.copyOf(update.subjects()));
        personas.save(persona);
        long revision = revisions.bump();
        audit.record(actorOrAnonymous(actor), "persona.updated", id,
                "roles=" + persona.getRoles() + " subjects=" + persona.getSubjects(), revision);
        return persona;
    }

    // ---- audit ----------------------------------------------------------

    @GetMapping("/audit")
    public List<AuditEntry> auditLog(@RequestParam(defaultValue = "50") int limit) {
        return auditEntries.findAllByOrderByAtDesc(PageRequest.of(0, Math.min(limit, 500)));
    }

    private static String actorOrAnonymous(String actor) {
        return actor == null || actor.isBlank() ? "anonymous" : actor;
    }
}
