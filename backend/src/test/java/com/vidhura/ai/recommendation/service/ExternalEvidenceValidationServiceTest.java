package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult.ValidationFinding;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceRepository;
import com.vidhura.ai.recommendation.repository.ExternalEvidenceValidationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalEvidenceValidationServiceTest {

    @Mock
    private ExternalEvidenceRepository evidenceRepository;

    @Mock
    private ExternalEvidenceValidationRepository validationRepository;

    private ExternalEvidenceValidationService validationService;

    // Authentic Statutory Hashes
    private static final String CIO_2023_24_HASH = "de3f04d382e7702aa2b5e57b7de997b9b6b0f0b9696c3b5b25da247d3f108d75";
    private static final String IRDAI_2022_23_HASH = "00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb";
    private static final String NIA_DISCLOSURES_HASH = "3b787a46e3039e07e1d7828804fdf0475b7163a530b0ffd0a841ec38ba3fb435";
    private static final String UIIC_NL39_HASH = "962ad2ae611c3c3c5369df1d991a899fa85148fa031b24d027d518132c4f8bb4";

    @BeforeEach
    void setUp() {
        validationService = new ExternalEvidenceValidationService(evidenceRepository, validationRepository);
        when(validationRepository.save(any(ExternalEvidenceValidationResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Validation Check: Valid statutory evidence passes automated validation with clean outcome")
    void testValidStatutoryEvidencePasses() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-cio-1")
                .insurerCode("NEW_INDIA")
                .insurerName("The New India Assurance Co. Ltd.")
                .evidenceType("OMBUDSMAN_ANNUAL_REPORT")
                .metricCategory("OMBUDSMAN_AWARDS")
                .metricKey("ombudsman_awards_count")
                .metricValue("386")
                .numericValue(new BigDecimal("386"))
                .metricUnit("COUNT")
                .reportingPeriod("FY 2023-24")
                .sourceDocumentTitle("Council for Insurance Ombudsmen Annual Report 2023-24")
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .citationPageNumber(65)
                .citationTableReference("STATEMENT H5")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .revision(2)
                .revisionType("SOURCE_VERIFIED")
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertNotNull(result);
        assertEquals("VALIDATION_PASSED", result.getOverallOutcome());
        // Revision 2 staged correction receives review prioritization boost (Score 21, MEDIUM tier)
        assertEquals("MEDIUM", result.getPriorityTier());
        assertTrue(result.getReviewPriorityScore() >= 10 && result.getReviewPriorityScore() < 50);

        // Governance Invariant: Never changes status to VERIFIED_STATUTORY
        assertEquals("UNVERIFIED_PENDING_REVIEW", record.getVerificationStatus());
        assertNull(record.getVerifiedBy());
        assertNull(record.getVerifiedAt());
        assertEquals("VALIDATION_PASSED", record.getLatestValidationOutcome());
    }

    @Test
    @DisplayName("Source Integrity: Missing source URL produces BLOCKER and VALIDATION_FAILED")
    void testMissingSourceUrlFails() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-err-1")
                .insurerCode("UNITED_INDIA")
                .metricKey("claims_settlement_ratio")
                .numericValue(new BigDecimal("98.4"))
                .sourceUrl("") // Missing URL
                .sourceDocumentHash(CIO_2023_24_HASH)
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_FAILED", result.getOverallOutcome());
        assertEquals("CRITICAL", result.getPriorityTier());
        assertTrue(result.getReviewPriorityScore() >= 100);

        boolean hasMissingUrlBlocker = result.getFindings().stream()
                .anyMatch(f -> "SRC_URL_MISSING".equals(f.getFindingCode()) && "BLOCKER".equals(f.getSeverity()));
        assertTrue(hasMissingUrlBlocker, "Must contain SRC_URL_MISSING blocker finding");
    }

    @Test
    @DisplayName("Source Integrity: Disallowed source domain produces BLOCKER and VALIDATION_FAILED")
    void testDisallowedDomainFails() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-err-2")
                .insurerCode("UNITED_INDIA")
                .metricKey("claims_settlement_ratio")
                .numericValue(new BigDecimal("98.4"))
                .sourceUrl("https://unverified-thirdparty-blog.org/report.pdf")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_FAILED", result.getOverallOutcome());
        boolean hasDomainBlocker = result.getFindings().stream()
                .anyMatch(f -> "SRC_URL_DISALLOWED_DOMAIN".equals(f.getFindingCode()) && "BLOCKER".equals(f.getSeverity()));
        assertTrue(hasDomainBlocker, "Must reject non-allowlisted source domain");
    }

    @Test
    @DisplayName("Source Integrity: Errant hash b6727c6d... from Phase 3.8 report typo is flagged as BLOCKER")
    void testErrantHashIdentityConflict() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-err-3")
                .insurerCode("NEW_INDIA")
                .metricKey("ombudsman_complaints")
                .sourceUrl("https://cioins.co.in/annualreports/AnnualReport2023-2024.pdf")
                .sourceDocumentHash("b6727c6d66e7bb844512c262e31505c065f462a677ca3e7f41d33ea64724a4f8")
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_FAILED", result.getOverallOutcome());
        boolean hasConflict = result.getFindings().stream()
                .anyMatch(f -> "DOC_IDENTITY_CONFLICT".equals(f.getFindingCode()) && "BLOCKER".equals(f.getSeverity()));
        assertTrue(hasConflict, "Errant hash must be caught as identity conflict");
    }

    @Test
    @DisplayName("Source Integrity: Synthetic consolidated UIIC filename is flagged as BLOCKER")
    void testSyntheticConsolidatedUiicFilenameConflict() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-err-4")
                .insurerCode("UNITED_INDIA")
                .sourceDocumentTitle("UIIC_Public_Disclosures_March_2023.pdf")
                .sourceUrl("https://uiic.co.in/web/grievance-details")
                .sourceDocumentHash(CIO_2023_24_HASH)
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_FAILED", result.getOverallOutcome());
        boolean hasConflict = result.getFindings().stream()
                .anyMatch(f -> "DOC_IDENTITY_CONFLICT".equals(f.getFindingCode()) && "BLOCKER".equals(f.getSeverity()));
        assertTrue(hasConflict, "Synthetic UIIC monolithic file must trigger identity conflict blocker");
    }

    @Test
    @DisplayName("Citation Validation: Form NL-41 cited for grievance disposal is flagged as ERROR")
    void testFormNl41VsNl45Mismatch() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-err-5")
                .insurerCode("NEW_INDIA")
                .metricKey("grievance_disposal_count")
                .metricValue("5,567")
                .numericValue(new BigDecimal("5567"))
                .sourceUrl("https://www.newindia.co.in/disclosures.pdf")
                .sourceDocumentHash(NIA_DISCLOSURES_HASH)
                .citationPageNumber(42)
                .citationTableReference("FORM NL-41 OFFICES INFORMATION") // Mismatched form
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_FAILED", result.getOverallOutcome());
        boolean hasFormMismatch = result.getFindings().stream()
                .anyMatch(f -> "CITATION_FORM_MISMATCH".equals(f.getFindingCode()) && "ERROR".equals(f.getSeverity()));
        assertTrue(hasFormMismatch, "Form NL-41 for grievances must produce CITATION_FORM_MISMATCH error");
    }

    @Test
    @DisplayName("Numerical Validation: Arithmetic formula reproduction failure triggers ERROR")
    void testArithmeticReproductionMismatch() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-err-6")
                .insurerCode("NEW_INDIA")
                .metricKey("complaints_per_10k_policies")
                .metricUnit("COMPLAINTS_PER_10K_POLICIES")
                .numerator(new BigDecimal("5567"))
                .denominator(new BigDecimal("7796252"))
                // Correct reproduction is 7.14 per 10k, but recorded value is erroneously 1.30
                .numericValue(new BigDecimal("1.30"))
                .sourceUrl("https://www.newindia.co.in/disclosures.pdf")
                .sourceDocumentHash(NIA_DISCLOSURES_HASH)
                .citationPageNumber(46)
                .citationTableReference("Form NL-45 Grievance Disposal")
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_FAILED", result.getOverallOutcome());
        boolean hasCalcError = result.getFindings().stream()
                .anyMatch(f -> "CALC_REPRODUCTION_MISMATCH".equals(f.getFindingCode()) && "ERROR".equals(f.getSeverity()));
        assertTrue(hasCalcError, "1.30 instead of 7.14 must trigger arithmetic reproduction mismatch");
    }

    @Test
    @DisplayName("Semantics Validation: Regulatory SLA claim marked as MEASURED_COMPLIANT triggers ERROR")
    void testRegulatorySlaConfusedWithCompliance() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-err-7")
                .insurerCode("UNITED_INDIA")
                .metricKey("regulatory_cashless_approval_sla_1h")
                .numericValue(new BigDecimal("100"))
                .actualComplianceStatus("MEASURED_COMPLIANT") // Confusing regulatory mandate with compliance
                .sourceUrl("https://irdai.gov.in/circulars")
                .sourceDocumentHash(IRDAI_2022_23_HASH)
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_FAILED", result.getOverallOutcome());
        boolean hasSlaError = result.getFindings().stream()
                .anyMatch(f -> "REGULATORY_SLA_CONFUSED_WITH_COMPLIANCE".equals(f.getFindingCode()) && "ERROR".equals(f.getSeverity()));
        assertTrue(hasSlaError, "Must prevent conflating regulatory mandates with measured insurer compliance");
    }

    @Test
    @DisplayName("Semantics Validation: Health grievance rate with all-lines policy denominator triggers WARNING")
    void testAllLinesDenominatorProxyWarning() {
        ExternalEvidenceRecord record = ExternalEvidenceRecord.builder()
                .id("ev-warn-1")
                .insurerCode("UNITED_INDIA")
                .businessSegment("HEALTH")
                .metricKey("complaints_per_10k_policies")
                .metricUnit("COMPLAINTS_PER_10K_POLICIES")
                .numerator(new BigDecimal("7324"))
                .denominator(new BigDecimal("8842105")) // All-lines Statement 4 denominator
                .numericValue(new BigDecimal("8.28"))
                .reportingPopulation("HEALTH_POLICIES") // Inaccurately labeled as health-only
                .verificationNotes("Direct calculation") // Missing proxy tag
                .sourceUrl("https://uiic.co.in/web/grievance-details")
                .sourceDocumentHash(UIIC_NL39_HASH)
                .build();

        ExternalEvidenceValidationResult result = validationService.validateRecord(record);

        assertEquals("VALIDATION_PASSED_WITH_WARNINGS", result.getOverallOutcome());
        boolean hasProxyWarning = result.getFindings().stream()
                .anyMatch(f -> "DENOMINATOR_POPULATION_MISMATCH".equals(f.getFindingCode()) && "WARNING".equals(f.getSeverity()));
        assertTrue(hasProxyWarning, "All-lines policy denominator for health rate must trigger population warning");
    }

    @Test
    @DisplayName("Prioritization Queue: Blockers rank CRITICAL, errors rank HIGH, warnings rank MEDIUM")
    void testReviewPrioritizationQueueOrder() {
        ExternalEvidenceRecord blockerRec = ExternalEvidenceRecord.builder()
                .id("rec-blocker")
                .insurerCode("NEW_INDIA")
                .metricKey("ombudsman_complaints")
                .sourceUrl("")
                .sourceDocumentHash("invalid-hash")
                .build();

        ExternalEvidenceRecord warningRec = ExternalEvidenceRecord.builder()
                .id("rec-warning")
                .insurerCode("UNITED_INDIA")
                .businessSegment("HEALTH")
                .metricKey("complaints_per_10k_policies")
                .metricUnit("COMPLAINTS_PER_10K_POLICIES")
                .numerator(new BigDecimal("7324"))
                .denominator(new BigDecimal("8842105"))
                .numericValue(new BigDecimal("8.28"))
                .sourceUrl("https://uiic.co.in/web/grievance-details")
                .sourceDocumentHash(UIIC_NL39_HASH)
                .build();

        validationService.validateRecord(blockerRec);
        validationService.validateRecord(warningRec);

        when(evidenceRepository.findAll()).thenReturn(List.of(warningRec, blockerRec));

        List<ExternalEvidenceDto.ReviewQueueItemDto> queue =
                validationService.getPrioritizedReviewQueue(null, null, null, null);

        assertEquals(2, queue.size());
        assertEquals("rec-blocker", queue.get(0).getEvidenceRecordId(),
                "Blocker record must rank higher in review queue than warning record");
        assertEquals("CRITICAL", queue.get(0).getPriorityTier());
        assertTrue(queue.get(0).getReviewPriorityScore() > queue.get(1).getReviewPriorityScore());
    }
}
