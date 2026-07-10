package org.perez_f_daniel.imdb.policy.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditEntryRepository extends MongoRepository<AuditEntry, String> {
    List<AuditEntry> findAllByOrderByAtDesc(Pageable pageable);
}
