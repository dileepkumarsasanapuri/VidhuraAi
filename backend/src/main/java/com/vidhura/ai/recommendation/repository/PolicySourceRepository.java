package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.PolicySource;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicySourceRepository extends MongoRepository<PolicySource, String> {
    List<PolicySource> findByActiveTrue();
    Optional<PolicySource> findBySourceUrl(String sourceUrl);
    Optional<PolicySource> findByIrdaUin(String irdaUin);
    boolean existsBySourceUrl(String sourceUrl);
    List<PolicySource> findByStatus(String status);
}
