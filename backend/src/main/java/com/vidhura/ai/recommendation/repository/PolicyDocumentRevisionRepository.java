package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyDocumentRevisionRepository extends MongoRepository<PolicyDocumentRevision, String> {
    List<PolicyDocumentRevision> findByIrdaUin(String irdaUin);
    Optional<PolicyDocumentRevision> findByIrdaUinAndUinRevision(String irdaUin, String uinRevision);
    List<PolicyDocumentRevision> findByPolicyCode(String policyCode);
    Optional<PolicyDocumentRevision> findByVerifiedSha256(String verifiedSha256);
    boolean existsByVerifiedSha256(String verifiedSha256);
    boolean existsByIrdaUinAndUinRevision(String irdaUin, String uinRevision);
    List<PolicyDocumentRevision> findByStatus(String status);
    Optional<PolicyDocumentRevision> findByRegistryRecordId(String registryRecordId);
    List<PolicyDocumentRevision> findAllByVerifiedSha256(String verifiedSha256);
    List<PolicyDocumentRevision> findByAcquisitionState(String acquisitionState);
    long countByAcquisitionState(String acquisitionState);
}
