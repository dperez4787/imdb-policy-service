package org.perez_f_daniel.imdb.policy.core;

import org.perez_f_daniel.imdb.policy.domain.AuditEntry;
import org.perez_f_daniel.imdb.policy.domain.AuditEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditService {

    private final AuditEntryRepository audit;

    public AuditService(AuditEntryRepository audit) {
        this.audit = audit;
    }

    public void record(String actor, String action, String target, String detail, long revision) {
        audit.save(new AuditEntry(Instant.now(), actor, action, target, detail, revision));
    }
}
