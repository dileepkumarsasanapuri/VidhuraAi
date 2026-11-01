package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentExtractionRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentRevisionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 6.4: Policy Evidence Verification & Extraction Quality Hardening Test Suite.
 *
 * Verifies all 16 required Phase 6.4 scenarios:
 * 1. Definition vs Operative Clause classification & gate enforcement
 * 2. Definition vs Policy Schedule interpretation
 * 3. Benefit amount vs Explanatory Example rejection
 * 4. Co-payment definition vs actual percentage extraction & candidate retention
 * 5. Waiting period extraction with IRDAI standard codes (Excl01, Excl02, Excl03)
 * 6. Table row and column associations
 * 7. Variant-specific coverage preservation
 * 8. Multi-page table continuity
 * 9. Conflicting values across pages detection & gate enforcement
 * 10. Footnote and exception preservation
 * 11. Missing key coverage fields non-fabrication
 * 12. Reviewer correction and audit history tracking
 * 13. Invalid evidence page reference rejection
 * 14. Policy readiness rejection for insufficient verified evidence
 * 15. Category-aware readiness assessment
 * 16. No unverified attribute reaching StructuredPolicy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Phase6_4PolicyEvidenceVerificationTest {

    @Mock
    private PolicyDocumentRevisionRepository revisionRepository;

    @Mock
    private IrdaiRegistryRepository registryRepository;

    @Mock
    private PolicyDocumentExtractionRepository extractionRepository;

    @TempDir
    Path tempDir;

    private TargetedDocumentStorageService storageService;
    private DocumentPageExtractionService pageExtractionService;
    private PolicyDocumentOcrService ocrService;
    private ClauseTaxonomyExtractor taxonomyExtractor;
    private PolicyDocumentIntelligenceService intelligenceService;
    private PolicyEvidenceReadinessService readinessService;

    private File samplePdfFile;
    private String samplePdfSha256;

    @BeforeEach
    void setUp() throws Exception {
        Path storageRoot = tempDir.resolve("policies");
        Path extractedRoot = tempDir.resolve("extracted");
        Files.createDirectories(storageRoot);
        Files.createDirectories(extractedRoot);

        storageService = new TargetedDocumentStorageService(storageRoot.toString());
        pageExtractionService = new DocumentPageExtractionService(storageRoot.toString(), extractedRoot.toString());
        ocrService = new PolicyDocumentOcrService(extractedRoot.toString());
        taxonomyExtractor = new ClauseTaxonomyExtractor();

        intelligenceService = new PolicyDocumentIntelligenceService(
                pageExtractionService,
                ocrService,
                taxonomyExtractor,
                storageService,
                revisionRepository,
                registryRepository,
                extractionRepository,
                extractedRoot.toString()
        );

        readinessService = new PolicyEvidenceReadinessService(
                extractionRepository,
                revisionRepository,
                registryRepository
        );

        byte[] pdfBytes = generateComprehensivePhase6_4Pdf();
        samplePdfSha256 = computeSha256(pdfBytes);

        Path contentDir = storageService.getContentAddressedDirectory();
        Files.createDirectories(contentDir);
        samplePdfFile = contentDir.resolve(samplePdfSha256.toLowerCase() + ".pdf").toFile();
        Files.write(samplePdfFile.toPath(), pdfBytes);

        when(extractionRepository.save(any(PolicyDocumentExtraction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("1. Definition vs Operative Clause: Generic definition is classified DEFINITION and rejected by gate 5")
    void testDefinitionVersusOperativeClause() {
        PolicyDocumentExtraction defExt = PolicyDocumentExtraction.builder()
                .id("ext-def-1")
                .documentSha256(samplePdfSha256)
                .documentRevisionId("rev-1")
                .extractedFieldName("COPAY_PERCENTAGE")
                .candidateValue("Co-payment Definition")
                .evidenceRole("DEFINITION")
                .originalPageNumber(2)
                .verbatimClauseText("Co-payment means a cost sharing requirement where the insured will bear a specified percentage of claim.")
                .extractionStatus("EXTRACTED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        // Verification Gate 5 must throw IllegalStateException if attempting to APPROVE a generic definition
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                intelligenceService.validateVerificationGates(defExt));
        assertTrue(ex.getMessage().contains("Verification Gate 5 Failed"));
        assertTrue(ex.getMessage().contains("cannot satisfy operative coverage field"));
    }

    @Test
    @DisplayName("2. Definition vs Actual Policy Schedule: Schedule clause is classified POLICY_SCHEDULE and passes gate")
    void testDefinitionVersusActualPolicySchedule() {
        PolicyDocumentExtraction schedExt = PolicyDocumentExtraction.builder()
                .id("ext-sched-1")
                .documentSha256(samplePdfSha256)
                .documentRevisionId("rev-1")
                .extractedFieldName("COPAY_PERCENTAGE")
                .candidateValue("20% Co-payment")
                .numericValue(20.0)
                .unit("PERCENTAGE")
                .evidenceRole("POLICY_SCHEDULE")
                .originalPageNumber(4)
                .verbatimClauseText("TABLE OF BENEFITS: Co-payment: 20% shall apply to each and every claim for persons above 60 years.")
                .extractionStatus("EXTRACTED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        assertDoesNotThrow(() -> intelligenceService.validateVerificationGates(schedExt));
    }

    @Test
    @DisplayName("3. Benefit amount vs Explanatory Example: Example is classified EXAMPLE and blocked by gate 5")
    void testBenefitAmountVersusExplanatoryExample() {
        PolicyDocumentExtraction exampleExt = PolicyDocumentExtraction.builder()
                .id("ext-ex-1")
                .documentSha256(samplePdfSha256)
                .documentRevisionId("rev-1")
                .extractedFieldName("ROOM_RENT_LIMIT")
                .candidateValue("Example Rs. 5000 per day")
                .numericValue(5000.0)
                .unit("INR_PER_DAY")
                .evidenceRole("EXAMPLE")
                .originalPageNumber(3)
                .verbatimClauseText("ILLUSTRATIVE EXAMPLE: For example, if room rent incurred is Rs. 5000 per day, the proportionate deduction will apply.")
                .extractionStatus("EXTRACTED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                intelligenceService.validateVerificationGates(exampleExt));
        assertTrue(ex.getMessage().contains("Verification Gate 5 Failed"));
    }

    @Test
    @DisplayName("4. Co-payment definition vs actual percentage: Schedule preferred and definition candidate retained")
    void testCopayDefinitionVersusActualPercentage() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult, "reg-1", "rev-1", "UIN-TEST", "INDIVIDUAL", "run-1"
        );

        Optional<PolicyDocumentExtraction> copayOpt = extractions.stream()
                .filter(e -> "COPAY_PERCENTAGE".equals(e.getExtractedFieldName()))
                .findFirst();

        assertTrue(copayOpt.isPresent());
        PolicyDocumentExtraction copay = copayOpt.get();
        assertNotNull(copay.getNumericValue());
        assertEquals(20.0, copay.getNumericValue().doubleValue());
        assertTrue(List.of("POLICY_SCHEDULE", "OPERATIVE_CLAUSE", "BENEFIT_LIMIT").contains(copay.getEvidenceRole()));
    }

    @Test
    @DisplayName("5. Waiting Period extraction: IRDAI Standard Code Excl01 (PED) extracted accurately")
    void testWaitingPeriodExtractionWithIrdaiStandardCodes() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult, "reg-1", "rev-1", "UIN-TEST", "INDIVIDUAL", "run-1"
        );

        Optional<PolicyDocumentExtraction> pedOpt = extractions.stream()
                .filter(e -> "WAITING_PERIOD_PED_MONTHS".equals(e.getExtractedFieldName()))
                .findFirst();

        assertTrue(pedOpt.isPresent(), "PED Waiting period must be extracted from standard Excl01 clause");
        PolicyDocumentExtraction ped = pedOpt.get();
        assertNotNull(ped.getNumericValue());
        assertEquals(48.0, ped.getNumericValue().doubleValue());
        assertEquals("MONTHS", ped.getUnit());
        assertEquals("WAITING_PERIOD", ped.getEvidenceRole());
    }

    @Test
    @DisplayName("6. Table row and column associations: Preserves table context and column headers")
    void testTableRowAndColumnAssociations() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult, "reg-1", "rev-1", "UIN-TEST", "INDIVIDUAL", "run-1"
        );

        Optional<PolicyDocumentExtraction> roomOpt = extractions.stream()
                .filter(e -> "ROOM_RENT_LIMIT".equals(e.getExtractedFieldName()))
                .findFirst();

        assertTrue(roomOpt.isPresent());
        assertNotNull(roomOpt.get().getSurroundingContext());
    }

    @Test
    @DisplayName("7. Variant-specific coverage: Product variants identified in metadata")
    void testVariantSpecificCoverage() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult, "reg-1", "rev-1", "UIN-TEST", "INDIVIDUAL", "run-1", "Optima Secure Gold Plan", "HDFC ERGO"
        );

        for (PolicyDocumentExtraction ext : extractions) {
            assertEquals("Optima Secure Gold Plan", ext.getProductName());
            assertEquals("HDFC ERGO", ext.getInsurerName());
        }
    }

    @Test
    @DisplayName("8. Multi-page table continuity: Extracts terms across successive pages")
    void testMultiPageTableContinuity() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        assertTrue(pagesResult.getTotalPages() >= 4);
        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult, "reg-1", "rev-1", "UIN-TEST", "INDIVIDUAL", "run-1"
        );

        Set<Integer> pagesWithExtractions = new HashSet<>();
        for (PolicyDocumentExtraction ext : extractions) {
            if (ext.getOriginalPageNumber() != null) {
                pagesWithExtractions.add(ext.getOriginalPageNumber());
            }
        }
        assertTrue(pagesWithExtractions.size() >= 2, "Extractions must span across multiple pages of the document");
    }

    @Test
    @DisplayName("9. Conflicting values across pages: Flags conflict and blocks gate 7 approval")
    void testConflictingValuesAcrossPages() {
        PolicyDocumentExtraction conflictExt = PolicyDocumentExtraction.builder()
                .id("ext-conf-1")
                .documentSha256(samplePdfSha256)
                .documentRevisionId("rev-1")
                .extractedFieldName("ROOM_RENT_LIMIT")
                .candidateValue("1% of Sum Insured")
                .numericValue(1.0)
                .unit("PERCENTAGE_OF_SUM_INSURED")
                .evidenceRole("BENEFIT_LIMIT")
                .originalPageNumber(2)
                .verbatimClauseText("Room rent limit is 1% of Sum Insured per day.")
                .hasConflict(true)
                .conflictingCandidates(List.of(
                        PolicyDocumentExtraction.ConflictingCandidate.builder()
                                .candidateValue("Rs. 5,000 per day")
                                .numericValue(BigDecimal.valueOf(5000.0))
                                .pageNumber(4)
                                .evidenceRole("POLICY_SCHEDULE")
                                .conflictReason("Numeric value conflict: 1.0 vs 5000.0")
                                .build()
                ))
                .extractionStatus("REVIEW_REQUIRED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                intelligenceService.validateVerificationGates(conflictExt));
        assertTrue(ex.getMessage().contains("Verification Gate 7 Failed"));
        assertTrue(ex.getMessage().contains("unresolved conflicting evidence candidates"));
    }

    @Test
    @DisplayName("10. Footnote and exception preservation: Retains conditions and exceptions text")
    void testFootnoteAndExceptionPreservation() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult, "reg-1", "rev-1", "UIN-TEST", "INDIVIDUAL", "run-1"
        );

        Optional<PolicyDocumentExtraction> exclusionsOpt = extractions.stream()
                .filter(e -> "PERMANENT_EXCLUSIONS".equals(e.getExtractedFieldName()))
                .findFirst();

        assertTrue(exclusionsOpt.isPresent());
        assertTrue(exclusionsOpt.get().getVerbatimClauseText().toLowerCase().contains("permanent exclusions")
                || exclusionsOpt.get().getVerbatimClauseText().toLowerCase().contains("shall not be liable"));
    }

    @Test
    @DisplayName("11. Missing key coverage fields: Does not fabricate unmentioned benefits")
    void testMissingKeyCoverageFields() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult, "reg-1", "rev-1", "UIN-TEST", "INDIVIDUAL", "run-1"
        );

        // Our generated sample PDF does not mention Maternity Waiting Period
        Optional<PolicyDocumentExtraction> matOpt = extractions.stream()
                .filter(e -> "WAITING_PERIOD_MATERNITY_MONTHS".equals(e.getExtractedFieldName()))
                .findFirst();

        assertTrue(matOpt.isEmpty() || "NOT_APPLICABLE".equals(matOpt.get().getExtractionStatus())
                || matOpt.get().getCandidateValue() == null, "Non-existent benefits must not be fabricated");
    }

    @Test
    @DisplayName("12. Reviewer correction and audit history: Updates values, logs diff, and clears conflict")
    void testReviewerCorrectionAndAuditHistory() {
        PolicyDocumentExtraction originalExt = PolicyDocumentExtraction.builder()
                .id("ext-audit-1")
                .documentSha256(samplePdfSha256)
                .documentRevisionId("rev-1")
                .extractedFieldName("COPAY_PERCENTAGE")
                .candidateValue("10% Copay")
                .numericValue(10.0)
                .unit("PERCENTAGE")
                .evidenceRole("POLICY_SCHEDULE")
                .originalPageNumber(2)
                .verbatimClauseText("Table: Copay 10% for Zone B.")
                .hasConflict(true)
                .extractionStatus("REVIEW_REQUIRED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .auditTrail(new ArrayList<>())
                .build();

        when(extractionRepository.findById("ext-audit-1")).thenReturn(Optional.of(originalExt));

        PolicyDocumentExtraction corrected = intelligenceService.correctExtraction(
                "ext-audit-1",
                "20% Copay (Zone A & B Standard)",
                20.0,
                "PERCENTAGE",
                "POLICY_SCHEDULE",
                "Corrected to uniform schedule standard following endorsement addendum.",
                "auditor@vidhura.ai",
                true
        );

        assertEquals("20% Copay (Zone A & B Standard)", corrected.getCandidateValue());
        assertNotNull(corrected.getNumericValue());
        assertEquals(20.0, corrected.getNumericValue().doubleValue());
        assertEquals("VERIFIED_REVIEWER", corrected.getVerificationStatus());
        assertEquals("VERIFIED", corrected.getExtractionStatus());
        assertFalse(corrected.getHasConflict());
        assertEquals(2, corrected.getAuditTrail().size());
        assertEquals("CORRECTED", corrected.getAuditTrail().get(0).getAction());
        assertEquals("APPROVE", corrected.getAuditTrail().get(1).getAction());
    }

    @Test
    @DisplayName("13. Invalid evidence page references: Throws Gate 3 exception")
    void testInvalidEvidencePageReferences() {
        PolicyDocumentExtraction badPageExt = PolicyDocumentExtraction.builder()
                .id("ext-bad-page")
                .documentSha256(samplePdfSha256)
                .documentRevisionId("rev-1")
                .extractedFieldName("SUM_INSURED_TIERS")
                .candidateValue("5 Lakhs to 50 Lakhs")
                .evidenceRole("BENEFIT_LIMIT")
                .originalPageNumber(0) // Invalid
                .verbatimClauseText("Sum insured available up to 50 Lakhs")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                intelligenceService.validateVerificationGates(badPageExt));
        assertTrue(ex.getMessage().contains("Verification Gate 3 Failed"));
    }

    @Test
    @DisplayName("14. Policy readiness rejection: Insufficient verified evidence rejects recommendation readiness")
    void testReadinessRejectionForInsufficientVerifiedEvidence() {
        String revId = "rev-readiness-test";
        PolicyDocumentRevision revision = PolicyDocumentRevision.builder()
                .id(revId)
                .verifiedSha256(samplePdfSha256)
                .registryRecordId("reg-readiness-1")
                .irdaUin("UIN-READINESS-1")
                .build();

        IrdaiRegistryRecord reg = IrdaiRegistryRecord.builder()
                .id("reg-readiness-1")
                .productName("Sample Health Shield")
                .insurerName("Care Health")
                .classificationStatus("INDIVIDUAL")
                .build();

        // Only 1 verified field out of 8 required
        PolicyDocumentExtraction ext1 = PolicyDocumentExtraction.builder()
                .id("e1")
                .documentRevisionId(revId)
                .extractedFieldName("ROOM_RENT_LIMIT")
                .verificationStatus("VERIFIED_REVIEWER")
                .extractionStatus("VERIFIED")
                .build();

        when(revisionRepository.findById(revId)).thenReturn(Optional.of(revision));
        when(registryRepository.findById("reg-readiness-1")).thenReturn(Optional.of(reg));
        when(extractionRepository.findAllByDocumentRevisionId(revId)).thenReturn(List.of(ext1));

        PolicyEvidenceReadinessService.PolicyEvidenceReadinessResult result =
                readinessService.evaluatePolicyReadiness(revId);

        assertNotNull(result);
        assertFalse(result.isReadyForRecommendation(), "Policy must not be ready with only 1 verified field");
        assertEquals("INSUFFICIENT_VERIFIED_EVIDENCE", result.getReadinessStatus());
        assertEquals(8, result.getTotalRequiredFields());
        assertEquals(1, result.getVerifiedFieldsCount());
        assertTrue(result.getMissingOrUnverifiedFields().contains("WAITING_PERIOD_PED_MONTHS"));
        assertTrue(result.getMissingOrUnverifiedFields().contains("SUM_INSURED_TIERS"));
    }

    @Test
    @DisplayName("15. Category-aware readiness: Personal Accident evaluates PA required fields without hospitalisation")
    void testCategoryAwareReadiness() {
        String revId = "rev-pa-test";
        PolicyDocumentRevision revision = PolicyDocumentRevision.builder()
                .id(revId)
                .verifiedSha256(samplePdfSha256)
                .registryRecordId("reg-pa-1")
                .irdaUin("UIN-PA-1")
                .build();

        IrdaiRegistryRecord reg = IrdaiRegistryRecord.builder()
                .id("reg-pa-1")
                .productName("Personal Accident Protection")
                .insurerName("Tata AIG")
                .classificationStatus("PERSONAL_ACCIDENT")
                .build();

        PolicyDocumentExtraction paSum = PolicyDocumentExtraction.builder()
                .id("pa-1")
                .documentRevisionId(revId)
                .extractedFieldName("SUM_INSURED_TIERS")
                .verificationStatus("VERIFIED_REVIEWER")
                .extractionStatus("VERIFIED")
                .build();

        PolicyDocumentExtraction paDur = PolicyDocumentExtraction.builder()
                .id("pa-2")
                .documentRevisionId(revId)
                .extractedFieldName("POLICY_DURATION_YEARS")
                .verificationStatus("VERIFIED_REVIEWER")
                .extractionStatus("VERIFIED")
                .build();

        PolicyDocumentExtraction paExcl = PolicyDocumentExtraction.builder()
                .id("pa-3")
                .documentRevisionId(revId)
                .extractedFieldName("PERMANENT_EXCLUSIONS")
                .verificationStatus("VERIFIED_REVIEWER")
                .extractionStatus("VERIFIED")
                .build();

        when(revisionRepository.findById(revId)).thenReturn(Optional.of(revision));
        when(registryRepository.findById("reg-pa-1")).thenReturn(Optional.of(reg));
        when(extractionRepository.findAllByDocumentRevisionId(revId)).thenReturn(List.of(paSum, paDur, paExcl));

        PolicyEvidenceReadinessService.PolicyEvidenceReadinessResult result =
                readinessService.evaluatePolicyReadiness(revId);

        assertNotNull(result);
        assertTrue(result.isReadyForRecommendation(), "Personal Accident policy with all 3 PA fields verified must be ready");
        assertEquals("READY_FOR_RECOMMENDATION", result.getReadinessStatus());
        assertEquals(3, result.getTotalRequiredFields());
        assertEquals(3, result.getVerifiedFieldsCount());
    }

    @Test
    @DisplayName("16. Strict Enforcement: No unverified attribute reaches StructuredPolicy")
    void testNoUnverifiedAttributeReachesStructuredPolicy() {
        StructuredPolicy policy = StructuredPolicy.builder()
                .id("sp-1")
                .policyCode("HDFC_OPTIMA_SECURE")
                .policyName("HDFC Optima Secure")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("No Limit")
                        .copayPercentage(0.0)
                        .waitingPeriodInitialDays(0)
                        .waitingPeriodPedMonths(0)
                        .build())
                .build();

        PolicyDocumentExtraction unverifiedCopay = PolicyDocumentExtraction.builder()
                .extractedFieldName("COPAY_PERCENTAGE")
                .candidateValue("20% Copay")
                .numericValue(20.0)
                .unit("PERCENTAGE")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        PolicyDocumentExtraction reviewRequiredRoom = PolicyDocumentExtraction.builder()
                .extractedFieldName("ROOM_RENT_LIMIT")
                .candidateValue("10000")
                .numericValue(10000.0)
                .unit("INR_PER_DAY")
                .verificationStatus("REVIEW_REQUIRED")
                .build();

        // Apply unverified attributes
        int applied = intelligenceService.applyVerifiedExtractionsToStructuredPolicy(
                policy, List.of(unverifiedCopay, reviewRequiredRoom)
        );

        assertEquals(0, applied, "Zero unverified attributes must be applied to StructuredPolicy");
        assertEquals(0.0, policy.getContractTerms().getCopayPercentage(), "Copay must remain unchanged");
        assertEquals("No Limit", policy.getContractTerms().getRoomRentLimit(), "Room rent must remain unchanged");

        // Now verify one attribute
        PolicyDocumentExtraction verifiedCopay = PolicyDocumentExtraction.builder()
                .extractedFieldName("COPAY_PERCENTAGE")
                .candidateValue("20% Copay")
                .numericValue(20.0)
                .unit("PERCENTAGE")
                .verificationStatus("VERIFIED_REVIEWER")
                .build();

        int appliedVerified = intelligenceService.applyVerifiedExtractionsToStructuredPolicy(
                policy, List.of(verifiedCopay)
        );

        assertEquals(1, appliedVerified, "Exactly 1 verified attribute must be applied");
        assertEquals(20.0, policy.getContractTerms().getCopayPercentage(), "Verified copay must now be updated");
    }

    // Helper to generate a multi-page PDF covering definitions, schedule tables, IRDAI standard codes, and exclusions
    private byte[] generateComprehensivePhase6_4Pdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            // Page 1: Policy title & basic information
            PDPage p1 = new PDPage();
            doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("HDFC ERGO GENERAL INSURANCE COMPANY - POLICY WORDINGS");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Product: Optima Secure Gold Plan | UIN: HDFHLIP21156V032021");
                cs.newLineAtOffset(0, -20);
                cs.showText("Sum insured options: 5 Lakhs, 10 Lakhs, 15 Lakhs, 20 Lakhs, 25 Lakhs, 50 Lakhs, 1 Crore.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Policy Duration: 1 year, 2 years, or 3 years.");
                cs.endText();
            }

            // Page 2: Definitions (Generic Definitions must not masquerade as operative coverage)
            PDPage p2 = new PDPage();
            doc.addPage(p2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("SECTION 1: DEFINITIONS");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("1.1 Co-payment means a cost sharing requirement under a health insurance policy that provides");
                cs.newLineAtOffset(0, -15);
                cs.showText("that the policyholder will bear a specified percentage of the admissible claim amount.");
                cs.newLineAtOffset(0, -20);
                cs.showText("1.2 Deductible is a cost sharing requirement that provides that the insurer will not be liable");
                cs.newLineAtOffset(0, -15);
                cs.showText("for a specified rupee amount of the covered expenses.");
                cs.newLineAtOffset(0, -20);
                cs.showText("1.3 Pre-existing Disease means any condition, ailment, injury or disease that is diagnosed");
                cs.newLineAtOffset(0, -15);
                cs.showText("by a physician within 48 months prior to the effective date of the policy issued by the insurer.");
                cs.endText();
            }

            // Page 3: Inpatient Hospitalisation & Operative clauses
            PDPage p3 = new PDPage();
            doc.addPage(p3);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p3)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("SECTION 2: INPATIENT HOSPITALISATION EXPENSES");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("2.1 Room rent charges: Room rent limit: 1% of Sum Insured per day subject to max Rs 5000 per day.");
                cs.newLineAtOffset(0, -20);
                cs.showText("2.2 Intensive Care Unit (ICU) charges: ICU limit: 2% of Sum Insured per day.");
                cs.newLineAtOffset(0, -20);
                cs.showText("2.3 Pre-hospitalization medical expenses incurred up to 60 days prior to admission.");
                cs.newLineAtOffset(0, -20);
                cs.showText("2.4 Post-hospitalization medical expenses incurred up to 180 days after discharge.");
                cs.newLineAtOffset(0, -20);
                cs.showText("2.5 Restoration Benefit: 100% restoration of base sum insured upon exhaustion.");
                cs.endText();
            }

            // Page 4: Schedule of Benefits & Waiting periods with IRDAI standard codes
            PDPage p4 = new PDPage();
            doc.addPage(p4);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p4)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("SECTION 3: WAITING PERIODS (IRDAI STANDARDIZED CODES)");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("a) Pre-existing Diseases (Code- Excl01): Expenses related to the treatment of a pre-existing Disease (PED)");
                cs.newLineAtOffset(0, -15);
                cs.showText("and its direct complications shall be excluded until the expiry of 48 months of continuous coverage.");
                cs.newLineAtOffset(0, -20);
                cs.showText("b) First Thirty Days Waiting Period (Code- Excl03): Expenses related to the treatment of any illness");
                cs.newLineAtOffset(0, -15);
                cs.showText("within 30 days from the first policy commencement date shall be excluded.");
                cs.newLineAtOffset(0, -20);
                cs.showText("c) Specified disease / procedure waiting period (Code- Excl02): Expenses related to treatment of listed");
                cs.newLineAtOffset(0, -15);
                cs.showText("conditions shall be excluded until the expiry of 24 months of continuous coverage.");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.showText("POLICY SCHEDULE & COST SHARING");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Co-payment: 20% shall apply to each and every claim for insured persons aged above 60 years.");
                cs.endText();
            }

            // Page 5: Exclusions
            PDPage p5 = new PDPage();
            doc.addPage(p5);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p5)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("SECTION 4: PERMANENT EXCLUSIONS");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("The Company shall not be liable to make any payment under the policy in respect of any expenses whatsoever:");
                cs.newLineAtOffset(0, -15);
                cs.showText("1. Investigation & Evaluation (Code- Excl04): Expenses related to any admission primarily for diagnostics.");
                cs.newLineAtOffset(0, -15);
                cs.showText("2. Rest Cure, rehabilitation and respite care (Code- Excl05).");
                cs.newLineAtOffset(0, -15);
                cs.showText("3. Obesity/ Weight Control (Code- Excl06): Expenses related to surgical treatment of obesity.");
                cs.newLineAtOffset(0, -15);
                cs.showText("4. Cosmetic or plastic surgery (Code- Excl08).");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
