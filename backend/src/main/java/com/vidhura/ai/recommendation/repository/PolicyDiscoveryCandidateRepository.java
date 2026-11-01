package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.PolicyDiscoveryCandidate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyDiscoveryCandidateRepository extends MongoRepository<PolicyDiscoveryCandidate, String> {
    List<PolicyDiscoveryCandidate> findByLifecycleStage(String lifecycleStage);
    List<PolicyDiscoveryCandidate> findByInsurerCode(String insurerCode);
    Optional<PolicyDiscoveryCandidate> findByIrdaUin(String irdaUin);
    Optional<PolicyDiscoveryCandidate> findByIrdaUinAndUinRevision(String irdaUin, String uinRevision);
    Optional<PolicyDiscoveryCandidate> findByNormalizedProductCode(String normalizedProductCode);
    Optional<PolicyDiscoveryCandidate> findByVerifiedSha256(String verifiedSha256);
    List<PolicyDiscoveryCandidate> findByDiscoveryRunId(String discoveryRunId);
    boolean existsByIrdaUinAndUinRevision(String irdaUin, String uinRevision);
    boolean existsByVerifiedSha256(String verifiedSha256);
    boolean existsByNormalizedProductCodeAndUinRevision(String normalizedProductCode, String uinRevision);
    long countByLifecycleStage(String lifecycleStage);
}
