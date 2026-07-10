package org.perez_f_daniel.imdb.policy.bundle;

import org.perez_f_daniel.imdb.policy.core.RevisionService;
import org.perez_f_daniel.imdb.policy.domain.FieldPolicy;
import org.perez_f_daniel.imdb.policy.domain.FieldPolicyRepository;
import org.perez_f_daniel.imdb.policy.domain.GovernedField;
import org.perez_f_daniel.imdb.policy.domain.GovernedFieldRepository;
import org.perez_f_daniel.imdb.policy.domain.Persona;
import org.perez_f_daniel.imdb.policy.domain.PersonaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BundleService {

    public static final String DEFAULT_POSTURE = "allow-unless-governed";

    private final GovernedFieldRepository governedFields;
    private final FieldPolicyRepository policies;
    private final PersonaRepository personas;
    private final RevisionService revisions;

    public BundleService(GovernedFieldRepository governedFields, FieldPolicyRepository policies,
                         PersonaRepository personas, RevisionService revisions) {
        this.governedFields = governedFields;
        this.policies = policies;
        this.personas = personas;
        this.revisions = revisions;
    }

    /**
     * Compiles the current state into a bundle. Revision and content are read
     * non-atomically; a concurrent write can produce a bundle one poll stale,
     * which the router's next poll reconciles.
     */
    public PolicyBundle build() {
        Map<String, FieldPolicy> policyByCoordinate = policies.findAll().stream()
                .collect(Collectors.toMap(FieldPolicy::getCoordinate, Function.identity()));

        Map<String, PolicyBundle.FieldEntry> fields = new LinkedHashMap<>();
        for (GovernedField gf : governedFields.findByStatus(GovernedField.Status.ACTIVE)) {
            FieldPolicy policy = policyByCoordinate.get(gf.getCoordinate());
            List<String> allowedRoles = (policy != null && policy.isEnabled() && policy.getAllowedRoles() != null)
                    ? List.copyOf(policy.getAllowedRoles())
                    : List.of();
            fields.put(gf.getCoordinate(), new PolicyBundle.FieldEntry(allowedRoles, gf.getSubgraph()));
        }

        Map<String, List<String>> principals = new LinkedHashMap<>();
        for (Persona persona : personas.findAll()) {
            if (persona.getSubjects() == null) {
                continue;
            }
            for (String subject : persona.getSubjects()) {
                principals.merge(subject, List.copyOf(persona.getRoles()), (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    for (String role : b) {
                        if (!merged.contains(role)) {
                            merged.add(role);
                        }
                    }
                    return List.copyOf(merged);
                });
            }
        }

        return new PolicyBundle(revisions.current(), Instant.now(), DEFAULT_POSTURE, fields, principals);
    }
}
