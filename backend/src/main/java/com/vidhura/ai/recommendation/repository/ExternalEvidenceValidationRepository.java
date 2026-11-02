package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalEvidenceValidationRepository extends MongoRepository<ExternalEvidenceValidationResult, String> {

    Optional<ExternalEvidenceValidationResult> findTopByEvidenceRecordIdOrderByValidatedAtDesc(String evidenceRecordId);

    Optional<ExternalEvidenceValidationResult> findTopByEvidenceRecordIdAndRecordRevisionOrderByValidatedAtDesc(String evidenceRecordId, int recordRevision);

    List<ExternalEvidenceValidationResult> findByEvidenceRecordIdOrderByValidatedAtDesc(String evidenceRecordId);

    List<ExternalEvidenceValidationResult> findByInsurerCodeOrderByValidatedAtDesc(String insurerCode);

    List<ExternalEvidenceValidationResult> findByOverallOutcomeOrderByReviewPriorityScoreDesc(String overallOutcome);

    List<ExternalEvidenceValidationResult> findAllByOrderByReviewPriorityScoreDescValidatedAtDesc();
}
