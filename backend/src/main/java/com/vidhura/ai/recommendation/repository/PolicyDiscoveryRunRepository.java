package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.PolicyDiscoveryRun;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyDiscoveryRunRepository extends MongoRepository<PolicyDiscoveryRun, String> {
    List<PolicyDiscoveryRun> findTop20ByOrderByStartedAtDesc();
    Optional<PolicyDiscoveryRun> findTopByOrderByStartedAtDesc();
    Optional<PolicyDiscoveryRun> findFirstByStatusOrderByStartedAtDesc(String status);
    boolean existsByStatus(String status);
}
