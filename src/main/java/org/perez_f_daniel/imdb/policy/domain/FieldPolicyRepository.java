package org.perez_f_daniel.imdb.policy.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldPolicyRepository extends MongoRepository<FieldPolicy, String> {
}
