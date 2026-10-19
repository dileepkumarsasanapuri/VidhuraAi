package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.ClaimScenario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimScenarioRepository extends MongoRepository<ClaimScenario, String> {
    List<ClaimScenario> findByPolicyId(String policyId);
    List<ClaimScenario> findByUserId(String userId);
    Optional<ClaimScenario> findByIdAndUserId(String id, String userId);
}
