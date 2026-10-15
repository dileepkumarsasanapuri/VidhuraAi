package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.StructuredPolicy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StructuredPolicyRepository extends MongoRepository<StructuredPolicy, String> {
    Optional<StructuredPolicy> findByPolicyCode(String policyCode);
    List<StructuredPolicy> findByActiveTrue();
    boolean existsByPolicyCode(String policyCode);
}
