package org.perez_f_daniel.imdb.policy.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GovernedFieldRepository extends MongoRepository<GovernedField, String> {
    List<GovernedField> findBySubgraph(String subgraph);
    List<GovernedField> findByStatus(GovernedField.Status status);
}
