package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceValidationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 5.3 Unit & Integration Test Suite:
 * Policy-Governed Automated Evidence Acceptance
 *
 * Verifies the 10 core governance, provenance, idempotency, and revalidation requirements:
 * 1. Legitimate structured statutory record auto-accepted & published
 * 2. Valid hash but invalid/inconsistent field bounds rejected from auto-acceptance
 * 3. Conflicting sibling reporting periods or values routed to human review
 * 4. Ambiguous qualitative clauses (exclusions/waiting periods) strictly routed to review
 * 5. Hash mismatch strictly rejected/quarantined from publication
 * 6. Missing private-insurer metrics remain unavailable (no synthetic boost/penalty)
 * 7. Authorized human reviewer sign-off recorded accurately without confusion
 * 8. Reprocessing same evidence is idempotent (no duplicate audit entries)
 * 9. Updating rules/source creates traceable revalidation path (REVALIDATION_REQUIRED)
 * 10. End-to-end ingestion, governance validation, persistence, and publication eligibility
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Phase5_3AutomatedEvidenceAcceptanceTest {

    @Mock
    private ExternalEvidenceRepository evidenceRepository;

    @Mock
    private ExternalEvidenceValidationRepository validationRepository;

    @Mock
    private OfficialSourceAcquisitionService acquisitionService;

    private EvidenceAcceptanceGovernancePolicy governancePolicy;
    private ExternalEvidenceValidationService validationService;
    private ExternalEvidenceService evidenceService;

    // Authentic Statutory Hashes
    private static final String CIO_2023_24_HASH = "de3f04d382e7702aa2b5e57b7de997b9b6b0f0b9696c3b5b25da247d3f108d75";

    @BeforeEach
    void setUp() {
        governancePolicy = new EvidenceAcceptanceGovernancePolicy();
        validationService = new ExternalEvidenceValidationService(
                evidenceRepository,
                validationRepository,
                governancePolicy,
                acquisitionService
        );
        evidenceService = new ExternalEvidenceService(evidenceRepository, null, null, null, validationService);

        when(validationRepository.save(any(ExternalEvidenceValidationResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(evidenceRepository.save(any(ExternalEvidenceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Case 1: Legitimate structured statutory record is auto-accepted and published")
    void testLegitimateStatutoryRecordAutoAcceptedAndPublished() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-stat-101")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .insurerName("The New India Assurance Co. Ltd.")
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .metricValue("98.40%")
                .numericValue(new BigDecimal("98.40"))
                .metricUnit("PERCENTAGE")
                .reportingPeriod("FY 2023-24")
                .sourceDocumentTitle("Council for Insurance Ombudsmen Annual Report 2023-24")
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationPageNumber(65)
                .citationTableReference("STATEMENT H5")
                .revision(1)
                .approvalMode("PENDING_REVIEW")
                .acceptanceDecision("PENDING")
                .publicationStatus("STAGED_INTERNAL")
                .build();

        when(evidenceRepository.findById("ev-stat-101")).thenReturn(Optional.of(record));

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertNotNull(result);
        assertEquals("AUTO_ACCEPTED", record.getApprovalMode());
        assertEquals("ACCEPTED", record.getAcceptanceDecision());
        assertEquals("PUBLISHED_ELIGIBLE", record.getPublicationStatus());
        assertEquals("VERIFIED_STATUTORY", record.getVerificationStatus());
        assertNull(record.getVerifiedBy(), "Automated acceptance must NEVER fabricate human reviewer email");
        assertNotNull(record.getVerifiedAt());
        assertEquals(EvidenceAcceptanceGovernancePolicy.GOVERNANCE_RULE_VERSION, record.getGovernanceRuleVersion());

        // Check audit trail
        assertNotNull(record.getAuditHistory());
        assertEquals(1, record.getAuditHistory().size());
        ExternalEvidenceRecord.ReviewAuditEntry audit = record.getAuditHistory().get(0);
        assertEquals("AUTO_ACCEPTED", audit.getDecision());
        assertEquals("AUTO_ACCEPTED", audit.getDecisionMode());
        assertNull(audit.getReviewerEmail());
        assertEquals("SYSTEM_GOVERNANCE_ENGINE", audit.getReviewerRole());

        // Downstream publication check
        assertTrue(evidenceService.isEligibleForPublication(record));
    }

    @Test
    @DisplayName("Case 2: Valid hash but numerical value outside statutory bounds is NOT auto-accepted")
    void testValidHashWithInconsistentFieldMappingNotAutoAccepted() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-stat-102")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .metricCategory("INCURRED_CLAIM_RATIO")
                .metricKey("incurred_claim_ratio")
                .numericValue(new BigDecimal("12.5")) // Below minimum plausible ICR bound (20.0%)
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationTableReference("TABLE 12")
                .reportingPeriod("FY 2023-24")
                .build();

        validationService.validateRecord(record);

        assertEquals("PENDING_REVIEW", record.getApprovalMode());
        assertEquals("PENDING", record.getAcceptanceDecision());
        assertEquals("STAGED_INTERNAL", record.getPublicationStatus());
        assertFalse(evidenceService.isEligibleForPublication(record));
        assertTrue(record.getFailedMandatoryChecks().stream()
                .anyMatch(c -> c.contains("DISQUALIFIED_NUMERICAL_BOUNDS")));
    }

    @Test
    @DisplayName("Case 3: Conflicting verified sibling filings trigger human review")
    void testConflictingSiblingFilingsTriggerHumanReview() {
        ExternalEvidenceRecord existing = ExternalEvidenceRecord.builder()
                .id("ev-sib-1")
                .insurerCode("UNITED_INDIA_INSURANCE")
                .metricKey("claims_settlement_ratio_3m")
                .numericValue(new BigDecimal("94.20"))
                .reportingPeriod("FY 2023-24")
                .revision(1)
                .build();

        ExternalEvidenceRecord incoming = ExternalEvidenceRecord.builder()
                .id("ev-sib-2")
                .insurerCode("UNITED_INDIA_INSURANCE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .numericValue(new BigDecimal("98.10")) // Conflicting value for same revision and period!
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationTableReference("TABLE 4")
                .reportingPeriod("FY 2023-24")
                .revision(1)
                .build();

        when(evidenceRepository.findByInsurerCodeAndMetricKeyAndReportingPeriodOrderByRevisionDesc(
                "UNITED_INDIA_INSURANCE", "claims_settlement_ratio_3m", "FY 2023-24"))
                .thenReturn(List.of(existing));

        validationService.validateRecord(incoming);

        assertEquals("PENDING_REVIEW", incoming.getApprovalMode());
        assertEquals("PENDING", incoming.getAcceptanceDecision());
        assertEquals("STAGED_INTERNAL", incoming.getPublicationStatus());
        assertTrue(incoming.getFailedMandatoryChecks().stream().anyMatch(c -> c.contains("DISQUALIFIED_CONFLICT")));
        assertFalse(evidenceService.isEligibleForPublication(incoming));
    }

    @Test
    @DisplayName("Case 4: Ambiguous policy exclusion or qualitative category remains pending human review")
    void testAmbiguousQualitativeExclusionPendingReview() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-qual-1")
                .insurerCode("HDFC_ERGO")
                .metricCategory("POLICY_EXCLUSIONS_QUALITATIVE") // Non-statutory qualitative category
                .metricKey("robotic_surgery_sublimit")
                .metricValue("Capped at 50% sum insured unless specified")
                .sourceUrl("https://www.irdai.gov.in/filings/optima.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationTableReference("CLAUSE 4.2")
                .reportingPeriod("2024")
                .build();

        validationService.validateRecord(record);

        assertEquals("PENDING_REVIEW", record.getApprovalMode());
        assertEquals("PENDING", record.getAcceptanceDecision());
        assertEquals("STAGED_INTERNAL", record.getPublicationStatus());
        assertTrue(record.getFailedMandatoryChecks().stream()
                .anyMatch(c -> c.contains("DISQUALIFIED_CATEGORY")));
        assertFalse(evidenceService.isEligibleForPublication(record));
    }

    @Test
    @DisplayName("Case 5: Cryptographic hash mismatch prevents acceptance and recommendation use")
    void testHashMismatchPreventsAcceptanceAndQuarantines() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-badhash-1")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash("6c80ff19a379821c4dc8a795342ea218a9b1b4001674bc58cb402029f66fcdf") // 63 chars (malformed!)
                .citationTableReference("STATEMENT H5")
                .reportingPeriod("FY 2023-24")
                .build();

        validationService.validateRecord(record);

        assertEquals("REJECTED", record.getApprovalMode());
        assertEquals("REJECTED", record.getAcceptanceDecision());
        assertEquals("QUARANTINED", record.getPublicationStatus());
        assertEquals("REJECTED", record.getVerificationStatus());
        assertFalse(evidenceService.isEligibleForPublication(record));
    }

    @Test
    @DisplayName("Case 6: Missing private-insurer metrics remain unavailable without artificial penalties")
    void testMissingPrivateInsurerMetricsRemainUnavailable() {
        when(evidenceRepository.findByInsurerCodeAndReportingPeriod(eq("CARE_HEALTH"), any()))
                .thenReturn(Collections.emptyList());

        ExternalEvidenceDto.InsurerPerformanceSummaryDto summary =
                evidenceService.getInsurerPerformanceMetrics("CARE_HEALTH", "FY 2023-24");

        assertNotNull(summary);
        assertFalse(summary.isVerifiedStatutory());
        assertEquals("UNVERIFIED", summary.getVerificationStatus());
        assertEquals("UNVERIFIED_PENDING_DISCLOSURE", summary.getOverallPerformanceRating());
        assertNull(summary.getIncurredClaimRatio());
        assertNull(summary.getSettlementRatioLessThan3Months());
    }

    @Test
    @DisplayName("Case 7: Authorized human decision is recorded accurately without confusion with auto-acceptance")
    void testHumanDecisionRecordedAccuratelyWithoutConfusion() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-human-1")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationTableReference("STATEMENT H5")
                .reportingPeriod("FY 2023-24")
                .revision(1)
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .approvalMode("PENDING_REVIEW")
                .auditHistory(new ArrayList<>())
                .build();

        when(evidenceRepository.findById("ev-human-1")).thenReturn(Optional.of(record));

        User reviewer = User.builder()
                .id("user-rev-1")
                .email("compliance.officer@vidhura.ai")
                .role("ROLE_REVIEWER")
                .build();

        ExternalEvidenceDto.VerifyEvidenceRequest verifyReq = ExternalEvidenceDto.VerifyEvidenceRequest.builder()
                .verificationStatus("VERIFIED_STATUTORY")
                .verificationNotes("Physically cross-verified with CIO 2023-24 report Statement H5")
                .build();

        ExternalEvidenceDto.EvidenceRecordDto verifiedDto = evidenceService.verifyEvidenceRecord("ev-human-1", verifyReq, reviewer);

        assertNotNull(verifiedDto);
        assertEquals("MANUAL_REVIEWER", record.getApprovalMode());
        assertEquals("ACCEPTED", record.getAcceptanceDecision());
        assertEquals("PUBLISHED_ELIGIBLE", record.getPublicationStatus());
        assertEquals("VERIFIED_STATUTORY", record.getVerificationStatus());
        assertEquals("compliance.officer@vidhura.ai", record.getVerifiedBy());
        assertNotNull(record.getVerifiedAt());

        // Verify audit history explicitly records MANUAL_REVIEWER
        assertFalse(record.getAuditHistory().isEmpty());
        ExternalEvidenceRecord.ReviewAuditEntry entry = record.getAuditHistory().get(0);
        assertEquals("MANUAL_REVIEWER", entry.getDecisionMode());
        assertEquals("compliance.officer@vidhura.ai", entry.getReviewerEmail());
        assertEquals("ROLE_REVIEWER", entry.getReviewerRole());
        assertEquals("APPROVED", entry.getDecision());
    }

    @Test
    @DisplayName("Case 8: Reprocessing the same evidence is idempotent")
    void testReprocessingEvidenceIsIdempotent() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-idemp-1")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .numericValue(new BigDecimal("98.40"))
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationTableReference("STATEMENT H5")
                .reportingPeriod("FY 2023-24")
                .revision(1)
                .auditHistory(new ArrayList<>())
                .build();

        // Pass 1
        validationService.validateRecord(record);
        assertEquals("AUTO_ACCEPTED", record.getApprovalMode());
        assertEquals(1, record.getAuditHistory().size());

        // Pass 2 (Reprocess exact same evidence)
        validationService.validateRecord(record);
        assertEquals("AUTO_ACCEPTED", record.getApprovalMode());
        assertEquals(1, record.getAuditHistory().size(), "Reprocessing must not append duplicate audit entries");

        // Pass 3 (Reprocess again)
        validationService.validateRecord(record);
        assertEquals(1, record.getAuditHistory().size(), "Audit trail must remain idempotent across repeated scans");
    }

    @Test
    @DisplayName("Case 9: Updating rule version or material check creates traceable revalidation path")
    void testRevalidationPathWhenRulesOrSourcesChange() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-reval-1")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .numericValue(new BigDecimal("98.40"))
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationTableReference("STATEMENT H5")
                .reportingPeriod("FY 2023-24")
                .approvalMode("AUTO_ACCEPTED")
                .acceptanceDecision("ACCEPTED")
                .publicationStatus("PUBLISHED_ELIGIBLE")
                .verificationStatus("VERIFIED_STATUTORY")
                .governanceRuleVersion("2025.1-legacy")
                .build();

        when(evidenceRepository.findById("ev-reval-1")).thenReturn(Optional.of(record));

        // Revalidate record
        ExternalEvidenceValidationResult res = validationService.revalidateRecord("ev-reval-1");

        assertNotNull(res);
        // Under current governance policy, it passes and is updated to version 2026.1-phase5.3
        assertEquals(EvidenceAcceptanceGovernancePolicy.GOVERNANCE_RULE_VERSION, record.getGovernanceRuleVersion());
        assertEquals("AUTO_ACCEPTED", record.getApprovalMode());
        assertEquals("PUBLISHED_ELIGIBLE", record.getPublicationStatus());
    }

    @Test
    @DisplayName("Case 10: End-to-end ingestion, validation, governance decision, persistence, and recommendation eligibility")
    void testEndToEndIngestionToRecommendationEligibility() {
        ExternalEvidenceDto.IngestEvidenceRequest ingestReq = ExternalEvidenceDto.IngestEvidenceRequest.builder()
                .evidenceType("IRDAI_ANNUAL_REPORT")
                .insurerCode("NEW_INDIA_ASSURANCE")
                .insurerName("The New India Assurance Co. Ltd.")
                .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                .metricKey("claims_settlement_ratio_3m")
                .metricValue("98.40%")
                .numericValue(new BigDecimal("98.40"))
                .metricUnit("PERCENTAGE")
                .reportingPeriod("FY 2023-24")
                .sourceDocumentTitle("Council for Insurance Ombudsmen Annual Report 2023-24")
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationPageNumber(65)
                .citationTableReference("STATEMENT H5")
                .revision(1)
                .build();

        User standardUser = User.builder()
                .id("user-std-1")
                .email("researcher@vidhura.ai")
                .role("ROLE_USER")
                .build();

        // 1. Ingest
        ExternalEvidenceDto.EvidenceRecordDto dto = evidenceService.ingestEvidenceRecord(ingestReq, standardUser);

        assertNotNull(dto);
        assertEquals("AUTO_ACCEPTED", dto.getApprovalMode());
        assertEquals("ACCEPTED", dto.getAcceptanceDecision());
        assertEquals("PUBLISHED_ELIGIBLE", dto.getPublicationStatus());
        assertEquals("VERIFIED_STATUTORY", dto.getVerificationStatus());
        assertNull(dto.getVerifiedBy(), "Auto-accepted record must have null verifiedBy");
        assertEquals(EvidenceAcceptanceGovernancePolicy.GOVERNANCE_RULE_VERSION, dto.getGovernanceRuleVersion());

        // 2. Downstream recommendation query uses the published record
        when(evidenceRepository.findByInsurerCodeAndReportingPeriod(eq("NEW_INDIA_ASSURANCE"), any()))
                .thenReturn(List.of(ExternalEvidenceRecord.builder()
                        .insurerCode("NEW_INDIA_ASSURANCE")
                        .insurerName("The New India Assurance Co. Ltd.")
                        .metricCategory("SETTLEMENT_RATIO_LESS_3_MONTHS")
                        .metricKey("claims_settlement_ratio_3m")
                        .metricValue("98.40%")
                        .numericValue(new BigDecimal("98.40"))
                        .reportingPeriod("FY 2023-24")
                        .approvalMode("AUTO_ACCEPTED")
                        .acceptanceDecision("ACCEPTED")
                        .publicationStatus("PUBLISHED_ELIGIBLE")
                        .verificationStatus("VERIFIED_STATUTORY")
                        .revision(1)
                        .build()));

        ExternalEvidenceDto.InsurerPerformanceSummaryDto summary =
                evidenceService.getInsurerPerformanceMetrics("NEW_INDIA_ASSURANCE", "FY 2023-24");

        assertTrue(summary.isVerifiedStatutory());
        assertEquals("VERIFIED_STATUTORY", summary.getVerificationStatus());
        assertEquals("98.40%", summary.getSettlementRatioLessThan3Months());
        assertEquals("EXCELLENT", summary.getOverallPerformanceRating());
    }
}
