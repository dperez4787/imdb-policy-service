package org.perez_f_daniel.imdb.policy.registration;

import org.perez_f_daniel.imdb.policy.core.AuditService;
import org.perez_f_daniel.imdb.policy.core.RevisionService;
import org.perez_f_daniel.imdb.policy.domain.GovernedField;
import org.perez_f_daniel.imdb.policy.domain.GovernedFieldRepository;
import org.perez_f_daniel.imdb.policy.registration.RegistrationController.DeclaredField;
import org.perez_f_daniel.imdb.policy.registration.RegistrationController.RegistrationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RegistrationService {

    private final GovernedFieldRepository governedFields;
    private final RevisionService revisions;
    private final AuditService audit;

    public RegistrationService(GovernedFieldRepository governedFields,
                               RevisionService revisions, AuditService audit) {
        this.governedFields = governedFields;
        this.revisions = revisions;
        this.audit = audit;
    }

    /**
     * Reconciles the subgraph's declared set against what is stored:
     * new coordinates become ACTIVE, known ones are refreshed, and stored
     * coordinates missing from the declaration are marked ORPHANED (never
     * deleted — their policies stay visible in the admin view for cleanup).
     */
    public RegistrationResult apply(String subgraph, List<DeclaredField> declared, String actor) {
        Instant now = Instant.now();
        Map<String, GovernedField> existing = new LinkedHashMap<>();
        for (GovernedField gf : governedFields.findBySubgraph(subgraph)) {
            existing.put(gf.getCoordinate(), gf);
        }

        int added = 0;
        int updated = 0;
        List<GovernedField> toSave = new ArrayList<>();

        for (DeclaredField field : declared) {
            GovernedField current = existing.remove(field.coordinate());
            if (current == null) {
                toSave.add(new GovernedField(field.coordinate(), subgraph, field.reason(),
                        GovernedField.Status.ACTIVE, "registration", now, now));
                added++;
            } else {
                boolean changed = current.getStatus() != GovernedField.Status.ACTIVE
                        || !Objects.equals(current.getReason(), field.reason())
                        || !"registration".equals(current.getSource());
                if (changed) {
                    current.setStatus(GovernedField.Status.ACTIVE);
                    current.setReason(field.reason());
                    current.setSource("registration");
                    current.setLastRegisteredAt(now);
                    toSave.add(current);
                    updated++;
                }
            }
        }

        // Whatever the declaration no longer mentions is orphaned.
        int orphaned = 0;
        for (GovernedField leftover : existing.values()) {
            if (leftover.getStatus() != GovernedField.Status.ORPHANED) {
                leftover.setStatus(GovernedField.Status.ORPHANED);
                toSave.add(leftover);
                orphaned++;
            }
        }

        boolean changed = !toSave.isEmpty();
        long revision = revisions.current();
        if (changed) {
            governedFields.saveAll(toSave);
            revision = revisions.bump();
            audit.record(actor, "registration.applied", subgraph,
                    "added=" + added + " updated=" + updated + " orphaned=" + orphaned, revision);
        }
        return new RegistrationResult(subgraph, added, updated, orphaned, changed, revision);
    }
}
