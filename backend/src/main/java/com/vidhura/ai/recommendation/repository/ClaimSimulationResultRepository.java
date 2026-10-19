package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.ClaimSimulationResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimSimulationResultRepository extends MongoRepository<ClaimSimulationResult, String> {
    Optional<ClaimSimulationResult> findByScenarioId(String scenarioId);
    Optional<ClaimSimulationResult> findByScenarioIdAndUserId(String scenarioId, String userId);
    List<ClaimSimulationResult> findByPolicyId(String policyId);
    List<ClaimSimulationResult> findByUserId(String userId);
}
