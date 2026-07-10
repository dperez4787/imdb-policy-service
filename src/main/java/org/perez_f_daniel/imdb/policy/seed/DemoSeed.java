package org.perez_f_daniel.imdb.policy.seed;

import org.perez_f_daniel.imdb.policy.core.AuditService;
import org.perez_f_daniel.imdb.policy.core.RevisionService;
import org.perez_f_daniel.imdb.policy.domain.FieldPolicy;
import org.perez_f_daniel.imdb.policy.domain.FieldPolicyRepository;
import org.perez_f_daniel.imdb.policy.domain.GovernedField;
import org.perez_f_daniel.imdb.policy.domain.GovernedFieldRepository;
import org.perez_f_daniel.imdb.policy.domain.Persona;
import org.perez_f_daniel.imdb.policy.domain.PersonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seeds the demo state on an empty database so the bundle is meaningful
 * before phase 3 lands real @governed registrations. Only ever writes to
 * empty collections — a database that has seen a registration or an admin
 * edit is never touched. Source "seed" entries are naturally orphaned once
 * the owning subgraph registers its true declaration set.
 */
@Component
@ConditionalOnProperty(name = "policy.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoSeed implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    private final GovernedFieldRepository governedFields;
    private final FieldPolicyRepository policies;
    private final PersonaRepository personas;
    private final RevisionService revisions;
    private final AuditService audit;

    public DemoSeed(GovernedFieldRepository governedFields, FieldPolicyRepository policies,
                    PersonaRepository personas, RevisionService revisions, AuditService audit) {
        this.governedFields = governedFields;
        this.policies = policies;
        this.personas = personas;
        this.revisions = revisions;
        this.audit = audit;
    }

    @Override
    public void run(String... args) {
        boolean seeded = false;
        Instant now = Instant.now();

        if (personas.count() == 0) {
            personas.saveAll(List.of(
                    new Persona("public", "Public consumer", List.of("public"), List.of()),
                    new Persona("analyst", "Ratings analyst", List.of("analyst", "public"), List.of())));
            seeded = true;
        }

        if (governedFields.count() == 0) {
            governedFields.saveAll(List.of(
                    seedField("Rating.numVotes", "ratings", "vote counts are analyst-only", now),
                    seedField("Name.birthYear", "names", "person PII: birth year", now),
                    seedField("Name.deathYear", "names", "person PII: death year", now)));
            seeded = true;
        }

        if (policies.count() == 0) {
            policies.saveAll(List.of(
                    new FieldPolicy("Rating.numVotes", List.of("analyst"), true, "seed", now),
                    new FieldPolicy("Name.birthYear", List.of("analyst"), true, "seed", now)));
            // Name.deathYear gets no policy on purpose: governed + ungranted
            // demonstrates deny-by-default.
            seeded = true;
        }

        if (seeded) {
            long revision = revisions.bump();
            audit.record("seed", "seed.applied", "demo fixtures",
                    "personas/governed_fields/policies seeded where empty", revision);
            log.info("demo seed applied, bundle revision {}", revision);
        }
    }

    private static GovernedField seedField(String coordinate, String subgraph, String reason, Instant now) {
        return new GovernedField(coordinate, subgraph, reason,
                GovernedField.Status.ACTIVE, "seed", now, now);
    }
}
