package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Phase 6.3: Repository for PolicyDocumentExtraction records.
 */
@Repository
public interface PolicyDocumentExtractionRepository extends MongoRepository<PolicyDocumentExtraction, String> {

    List<PolicyDocumentExtraction> findAllByDocumentSha256(String documentSha256);

    List<PolicyDocumentExtraction> findAllByRegistryRecordId(String registryRecordId);

    List<PolicyDocumentExtraction> findAllByDocumentRevisionId(String documentRevisionId);

    List<PolicyDocumentExtraction> findAllByCanonicalPolicyUin(String canonicalPolicyUin);

    List<PolicyDocumentExtraction> findAllByExtractionStatus(String extractionStatus);

    List<PolicyDocumentExtraction> findAllByVerificationStatus(String verificationStatus);

    Optional<PolicyDocumentExtraction> findByDocumentSha256AndExtractedFieldName(String documentSha256, String extractedFieldName);

    long countByDocumentSha256(String documentSha256);

    long countByExtractionStatus(String extractionStatus);

    long countByVerificationStatus(String verificationStatus);

    boolean existsByDocumentSha256AndExtractionEngineVersion(String documentSha256, String extractionEngineVersion);

    boolean existsByDocumentRevisionIdAndExtractionEngineVersion(String documentRevisionId, String extractionEngineVersion);

    List<PolicyDocumentExtraction> findAllByHasConflictTrue();

    long countByHasConflictTrue();
}
