package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalEvidenceRepository extends MongoRepository<ExternalEvidenceRecord, String> {

    List<ExternalEvidenceRecord> findByInsurerCode(String insurerCode);

    List<ExternalEvidenceRecord> findByInsurerCodeAndReportingPeriod(String insurerCode, String reportingPeriod);

    List<ExternalEvidenceRecord> findByInsurerCodeAndMetricCategory(String insurerCode, String metricCategory);

    Optional<ExternalEvidenceRecord> findByInsurerCodeAndMetricKeyAndReportingPeriod(String insurerCode, String metricKey, String reportingPeriod);

    List<ExternalEvidenceRecord> findByVerificationStatus(String verificationStatus);

    List<ExternalEvidenceRecord> findBySourceDocumentHash(String sourceDocumentHash);

    boolean existsBySourceDocumentHashAndMetricKey(String sourceDocumentHash, String metricKey);

    boolean existsByInsurerCodeAndMetricKeyAndReportingPeriod(String insurerCode, String metricKey, String reportingPeriod);
}
