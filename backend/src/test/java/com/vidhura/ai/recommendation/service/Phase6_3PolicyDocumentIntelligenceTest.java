package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 6.3: Comprehensive Unit and Integration Test Suite for Policy Document Intelligence.
 *
 * Verifies:
 * - Native PDF text extraction page by page
 * - Table and clause boundary segmentation
 * - Structured taxonomy extraction (Room rent, ICU, Waiting periods, Copay, NCB, Exclusions)
 * - Category-aware processing (Medical vs Travel/Personal Accident)
 * - Scanned page detection & OCR fail-safe handling (no text fabrication)
 * - Cryptographic hash validation & mismatch rejection
 * - Idempotency & duplicate extraction prevention
 * - Human reviewer workflows (Approve, Reject, Request Correction) with audit trail
 * - Missing field non-fabrication
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Phase6_3PolicyDocumentIntelligenceTest {

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

        // Build a multi-page test PDF with realistic health insurance contractual terms
        byte[] pdfBytes = generateMultiPagePolicyPdf();
        samplePdfSha256 = computeSha256(pdfBytes);

        // Store into content-addressed storage
        Path contentDir = storageService.getContentAddressedDirectory();
        Files.createDirectories(contentDir);
        samplePdfFile = contentDir.resolve(samplePdfSha256.toLowerCase() + ".pdf").toFile();
        Files.write(samplePdfFile.toPath(), pdfBytes);
    }

    @Test
    @DisplayName("1. Native text extraction extracts text page-by-page and detects clause boundaries")
    void testNativeTextExtractionAndClauseBoundaries() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult result =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        assertNotNull(result);
        assertEquals(samplePdfSha256, result.getDocumentSha256());
        assertEquals(5, result.getTotalPages());
        assertEquals(5, result.getPages().size());

        // Check page 1
        DocumentPageExtractionService.PageContent p1 = result.getPages().get(0);
        assertEquals(1, p1.getPageNumber());
        assertTrue(p1.getRawText().contains("SECTION 1: BASIC COVERAGE"));
        assertFalse(p1.isScanned());
        assertFalse(p1.isEmpty());
        assertFalse(p1.getSegments().isEmpty());

        // Check page 2: Hospitalization & Room rent
        DocumentPageExtractionService.PageContent p2 = result.getPages().get(1);
        assertEquals(2, p2.getPageNumber());
        assertTrue(p2.getRawText().contains("SECTION 2: INPATIENT HOSPITALISATION"));
        assertTrue(p2.getRawText().contains("Room rent"));
    }

    @Test
    @DisplayName("2. Detects scanned or minimal text page without fabricating content")
    void testScannedPageDetection() throws IOException {
        // Create PDF with one normal page and one scanned (image-only or <40 chars)
        byte[] scannedDocBytes = generatePdfWithScannedPage();
        String hash = computeSha256(scannedDocBytes);
        File scannedFile = tempDir.resolve("scanned.pdf").toFile();
        Files.write(scannedFile.toPath(), scannedDocBytes);

        DocumentPageExtractionService.DocumentPagesResult result =
                pageExtractionService.extractPages(scannedFile, hash);

        assertEquals(2, result.getTotalPages());
        assertTrue(result.getScannedPagesCount() >= 1 || result.getEmptyPagesCount() >= 1);
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Page 2")));
    }

    @Test
    @DisplayName("3. Cryptographic hash mismatch throws SecurityException")
    void testDocumentHashMismatch() {
        String wrongHash = "0000000000000000000000000000000000000000000000000000000000000000";
        assertThrows(SecurityException.class, () ->
                pageExtractionService.extractPages(samplePdfFile, wrongHash)
        );
    }

    @Test
    @DisplayName("4. Clause taxonomy extracts strongly-typed values, units, and verbatim text")
    void testClauseTaxonomyExtraction() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult,
                "REG_TEST_001",
                "REV_TEST_001",
                "UIN-TEST-2024",
                "INDIVIDUAL",
                "RUN_TEST_001"
        );

        assertNotNull(extractions);
        assertFalse(extractions.isEmpty());

        // Verify Room Rent
        Optional<PolicyDocumentExtraction> roomRent = extractions.stream()
                .filter(e -> "ROOM_RENT_LIMIT".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(roomRent.isPresent(), "ROOM_RENT_LIMIT should be extracted");
        assertEquals("HOSPITALIZATION", roomRent.get().getTaxonomyCategory());
        assertEquals(2, roomRent.get().getOriginalPageNumber());
        assertEquals("PERCENTAGE", roomRent.get().getUnit());
        assertEquals(new BigDecimal("1"), roomRent.get().getNumericValue());
        assertNotNull(roomRent.get().getOriginalClauseText());

        // Verify ICU Charges
        Optional<PolicyDocumentExtraction> icu = extractions.stream()
                .filter(e -> "ICU_CHARGES_LIMIT".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(icu.isPresent(), "ICU_CHARGES_LIMIT should be extracted");
        assertEquals(new BigDecimal("2"), icu.get().getNumericValue());

        // Verify PED Waiting Period
        Optional<PolicyDocumentExtraction> ped = extractions.stream()
                .filter(e -> "WAITING_PERIOD_PED_MONTHS".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(ped.isPresent(), "WAITING_PERIOD_PED_MONTHS should be extracted");
        assertEquals("WAITING_PERIOD", ped.get().getTaxonomyCategory());
        assertEquals("MONTHS", ped.get().getUnit());
        assertEquals(new BigDecimal("36"), ped.get().getNumericValue());

        // Verify Initial Waiting Period
        Optional<PolicyDocumentExtraction> initialWait = extractions.stream()
                .filter(e -> "WAITING_PERIOD_INITIAL_DAYS".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(initialWait.isPresent(), "WAITING_PERIOD_INITIAL_DAYS should be extracted");
        assertEquals("DAYS", initialWait.get().getUnit());
        assertEquals(new BigDecimal("30"), initialWait.get().getNumericValue());

        // Verify Co-payment
        Optional<PolicyDocumentExtraction> copay = extractions.stream()
                .filter(e -> "COPAY_PERCENTAGE".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(copay.isPresent(), "COPAY_PERCENTAGE should be extracted");
        assertEquals("COST_SHARING", copay.get().getTaxonomyCategory());
        assertEquals("PERCENTAGE", copay.get().getUnit());
        assertEquals(new BigDecimal("20"), copay.get().getNumericValue());

        // Verify No-claim Bonus
        Optional<PolicyDocumentExtraction> ncb = extractions.stream()
                .filter(e -> "NO_CLAIM_BONUS".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(ncb.isPresent(), "NO_CLAIM_BONUS should be extracted");
        assertEquals(new BigDecimal("10"), ncb.get().getNumericValue());
    }

    @Test
    @DisplayName("5. Category-aware processing: non-medical policy ignores medical clauses")
    void testCategoryAwareProcessing() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        // Process as TRAVEL category
        List<PolicyDocumentExtraction> travelExtractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult,
                "REG_TRAVEL_001",
                "REV_TRAVEL_001",
                "UIN-TRAVEL-2024",
                "TRAVEL",
                "RUN_TRAVEL_001"
        );

        // Room rent and ICU must NOT be extracted as active coverage for travel insurance, but explicitly marked NOT_APPLICABLE
        Optional<PolicyDocumentExtraction> roomRent = travelExtractions.stream()
                .filter(e -> "ROOM_RENT_LIMIT".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(roomRent.isPresent(), "ROOM_RENT_LIMIT record should exist for tracking");
        assertEquals("NOT_APPLICABLE", roomRent.get().getExtractionStatus(), "Should be marked NOT_APPLICABLE");

        Optional<PolicyDocumentExtraction> icu = travelExtractions.stream()
                .filter(e -> "ICU_CHARGES_LIMIT".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(icu.isPresent(), "ICU_CHARGES_LIMIT record should exist for tracking");
        assertEquals("NOT_APPLICABLE", icu.get().getExtractionStatus(), "Should be marked NOT_APPLICABLE");
    }

    @Test
    @DisplayName("6. Missing fields are not fabricated or defaulted to zero or covered")
    void testMissingFieldNonFabrication() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult,
                "REG_TEST_001",
                "REV_TEST_001",
                "UIN-TEST-2024",
                "INDIVIDUAL",
                "RUN_TEST_001"
        );

        // Sample PDF does not mention RESTORATION or PRE_HOSPITALIZATION
        assertFalse(extractions.stream().anyMatch(e -> "RESTORATION_BENEFIT".equals(e.getExtractedFieldName())));
        assertFalse(extractions.stream().anyMatch(e -> "PRE_HOSPITALIZATION_DAYS".equals(e.getExtractedFieldName())));
    }

    @Test
    @DisplayName("7. End-to-end extraction pipeline is idempotent and prevents duplicate extraction")
    void testEndToEndExtractionAndIdempotency() {
        PolicyDocumentRevision revision = PolicyDocumentRevision.builder()
                .id("REV_TEST_001")
                .registryRecordId("REG_TEST_001")
                .irdaUin("UIN-TEST-2024")
                .verifiedSha256(samplePdfSha256)
                .storageKey(samplePdfFile.getAbsolutePath())
                .status("VERIFIED")
                .acquisitionState("ACQUIRED")
                .build();

        IrdaiRegistryRecord record = IrdaiRegistryRecord.builder()
                .id("REG_TEST_001")
                .normalizedUin("UIN-TEST-2024")
                .classificationStatus("INDIVIDUAL")
                .productName("Test Health Shield")
                .build();

        when(revisionRepository.findByVerifiedSha256(samplePdfSha256)).thenReturn(Optional.of(revision));
        when(registryRepository.findById("REG_TEST_001")).thenReturn(Optional.of(record));
        when(extractionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // First run: processes document
        when(extractionRepository.existsByDocumentSha256AndExtractionEngineVersion(samplePdfSha256, ClauseTaxonomyExtractor.ENGINE_VERSION))
                .thenReturn(false);
        when(extractionRepository.existsByDocumentRevisionIdAndExtractionEngineVersion("REV_TEST_001", ClauseTaxonomyExtractor.ENGINE_VERSION))
                .thenReturn(false);

        PolicyDocumentIntelligenceService.DocumentExtractionResult run1 =
                intelligenceService.processDocument(samplePdfSha256, "RUN_1", false);

        assertEquals("COMPLETED", run1.getLifecycleStatus());
        assertFalse(run1.getExtractedAttributes().isEmpty());
        verify(extractionRepository, times(1)).saveAll(anyList());

        // Second run: idempotent check suppresses duplicate extraction
        when(extractionRepository.existsByDocumentSha256AndExtractionEngineVersion(samplePdfSha256, ClauseTaxonomyExtractor.ENGINE_VERSION))
                .thenReturn(true);
        when(extractionRepository.existsByDocumentRevisionIdAndExtractionEngineVersion("REV_TEST_001", ClauseTaxonomyExtractor.ENGINE_VERSION))
                .thenReturn(true);
        when(extractionRepository.findAllByDocumentSha256(samplePdfSha256)).thenReturn(run1.getExtractedAttributes());
        when(extractionRepository.findAllByDocumentRevisionId("REV_TEST_001")).thenReturn(run1.getExtractedAttributes());

        PolicyDocumentIntelligenceService.DocumentExtractionResult run2 =
                intelligenceService.processDocument(samplePdfSha256, "RUN_2", false);

        assertEquals("COMPLETED", run2.getLifecycleStatus());
        assertFalse(run2.isReprocessed());
        // Verify saveAll was NOT called a second time
        verify(extractionRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("8. Human reviewer workflow: APPROVE and REJECT update statuses with audit trail")
    void testReviewerWorkflowWithAuditTrail() {
        PolicyDocumentExtraction extraction = PolicyDocumentExtraction.builder()
                .id("EXT_TEST_001")
                .documentSha256(samplePdfSha256)
                .extractedFieldName("ROOM_RENT_LIMIT")
                .typedFieldValue("Up to 1% of Sum Insured per day")
                .extractionStatus("EXTRACTED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .auditTrail(new ArrayList<>())
                .build();

        when(extractionRepository.findById("EXT_TEST_001")).thenReturn(Optional.of(extraction));
        when(extractionRepository.save(any(PolicyDocumentExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Reviewer approves
        PolicyDocumentExtraction approved = intelligenceService.reviewExtraction(
                "EXT_TEST_001", "APPROVE", "Verified against page 2 clause", "auditor@vidhura.ai"
        );

        assertEquals("VERIFIED_REVIEWER", approved.getVerificationStatus());
        assertEquals("VERIFIED", approved.getExtractionStatus());
        assertEquals("auditor@vidhura.ai", approved.getVerifiedBy());
        assertNotNull(approved.getVerifiedAt());
        assertEquals(1, approved.getAuditTrail().size());
        assertEquals("APPROVE", approved.getAuditTrail().get(0).getAction());

        // Reviewer rejects
        PolicyDocumentExtraction rejected = intelligenceService.reviewExtraction(
                "EXT_TEST_001", "REJECT", "Ambiguous definition in add-on rider", "auditor@vidhura.ai"
        );

        assertEquals("REJECTED_REVIEWER", rejected.getVerificationStatus());
        assertEquals("REJECTED", rejected.getExtractionStatus());
        assertEquals(2, rejected.getAuditTrail().size());
        assertEquals("REJECT", rejected.getAuditTrail().get(1).getAction());
    }

    @Test
    @DisplayName("9. Expanded taxonomy extracts additional benefits, exclusions, riders, and domiciliary")
    void testExpandedTaxonomyClauses() throws IOException {
        DocumentPageExtractionService.DocumentPagesResult pagesResult =
                pageExtractionService.extractPages(samplePdfFile, samplePdfSha256);

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pagesResult,
                "REG_TEST_001",
                "REV_TEST_001",
                "UIN-TEST-2024",
                "INDIVIDUAL",
                "RUN_TEST_EXPANDED"
        );

        // Verify Domiciliary
        Optional<PolicyDocumentExtraction> domiciliary = extractions.stream()
                .filter(e -> "DOMICILIARY_HOSPITALIZATION".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(domiciliary.isPresent(), "DOMICILIARY_HOSPITALIZATION should be extracted");
        assertEquals("HOSPITALIZATION", domiciliary.get().getTaxonomyCategory());
        assertEquals(new BigDecimal("10"), domiciliary.get().getNumericValue());

        // Verify Ambulance
        Optional<PolicyDocumentExtraction> ambulance = extractions.stream()
                .filter(e -> "AMBULANCE_COVER".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(ambulance.isPresent(), "AMBULANCE_COVER should be extracted");
        assertEquals("ADDITIONAL_BENEFITS", ambulance.get().getTaxonomyCategory());

        // Verify AYUSH
        Optional<PolicyDocumentExtraction> ayush = extractions.stream()
                .filter(e -> "AYUSH_COVER".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(ayush.isPresent(), "AYUSH_COVER should be extracted");
        assertEquals("ADDITIONAL_BENEFITS", ayush.get().getTaxonomyCategory());

        // Verify Organ Donor
        Optional<PolicyDocumentExtraction> organDonor = extractions.stream()
                .filter(e -> "ORGAN_DONOR_EXPENSES".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(organDonor.isPresent(), "ORGAN_DONOR_EXPENSES should be extracted");

        // Verify Maternity Waiting Period
        Optional<PolicyDocumentExtraction> matWait = extractions.stream()
                .filter(e -> "WAITING_PERIOD_MATERNITY_MONTHS".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(matWait.isPresent(), "WAITING_PERIOD_MATERNITY_MONTHS should be extracted");
        assertEquals(new BigDecimal("24"), matWait.get().getNumericValue());

        // Verify Permanent Exclusions
        Optional<PolicyDocumentExtraction> exclusions = extractions.stream()
                .filter(e -> "PERMANENT_EXCLUSIONS".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(exclusions.isPresent(), "PERMANENT_EXCLUSIONS should be extracted");
        assertEquals("EXCLUSIONS", exclusions.get().getTaxonomyCategory());

        // Verify Optional Riders
        Optional<PolicyDocumentExtraction> riders = extractions.stream()
                .filter(e -> "OPTIONAL_COVERS_RIDERS".equals(e.getExtractedFieldName()))
                .findFirst();
        assertTrue(riders.isPresent(), "OPTIONAL_COVERS_RIDERS should be extracted");
        assertEquals("OPTIONAL_BENEFITS", riders.get().getTaxonomyCategory());
    }

    @Test
    @DisplayName("10. Shared physical PDF blobs with independent logical revisions produce distinct scoped records")
    void testSharedPhysicalBlobsWithIndependentLogicalRevisions() {
        // Two distinct logical revisions sharing the same physical PDF blob
        PolicyDocumentRevision revA = PolicyDocumentRevision.builder()
                .id("REV_COHORT_001")
                .registryRecordId("REG_COHORT_001")
                .irdaUin("UIN-COHORT-001")
                .verifiedSha256(samplePdfSha256)
                .storageKey(samplePdfFile.getAbsolutePath())
                .status("VERIFIED")
                .acquisitionState("ACQUIRED")
                .build();

        PolicyDocumentRevision revB = PolicyDocumentRevision.builder()
                .id("REV_COHORT_002")
                .registryRecordId("REG_COHORT_002")
                .irdaUin("UIN-COHORT-002")
                .verifiedSha256(samplePdfSha256)
                .storageKey(samplePdfFile.getAbsolutePath())
                .status("VERIFIED")
                .acquisitionState("ACQUIRED")
                .build();

        IrdaiRegistryRecord regA = IrdaiRegistryRecord.builder()
                .id("REG_COHORT_001").normalizedUin("UIN-COHORT-001").classificationStatus("INDIVIDUAL").build();
        IrdaiRegistryRecord regB = IrdaiRegistryRecord.builder()
                .id("REG_COHORT_002").normalizedUin("UIN-COHORT-002").classificationStatus("INDIVIDUAL").build();

        when(registryRepository.findById("REG_COHORT_001")).thenReturn(Optional.of(regA));
        when(registryRepository.findById("REG_COHORT_002")).thenReturn(Optional.of(regB));
        when(extractionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Process revA
        PolicyDocumentIntelligenceService.DocumentExtractionResult resA =
                intelligenceService.processRevision(revA, "RUN_SCALEUP", false);
        assertEquals("COMPLETED", resA.getLifecycleStatus());
        assertFalse(resA.getExtractedAttributes().isEmpty());
        assertTrue(resA.getExtractedAttributes().stream().allMatch(e -> "REV_COHORT_001".equals(e.getDocumentRevisionId())));
        assertTrue(resA.getExtractedAttributes().stream().allMatch(e -> e.getId().startsWith("EXT_REV_COHORT_001_")));

        // Process revB (same physical file, distinct revision ID)
        PolicyDocumentIntelligenceService.DocumentExtractionResult resB =
                intelligenceService.processRevision(revB, "RUN_SCALEUP", false);
        assertEquals("COMPLETED", resB.getLifecycleStatus());
        assertFalse(resB.getExtractedAttributes().isEmpty());
        assertTrue(resB.getExtractedAttributes().stream().allMatch(e -> "REV_COHORT_002".equals(e.getDocumentRevisionId())));
        assertTrue(resB.getExtractedAttributes().stream().allMatch(e -> e.getId().startsWith("EXT_REV_COHORT_002_")));

        // Verify saveAll was called for both revisions separately
        verify(extractionRepository, times(2)).saveAll(anyList());
    }

    @Test
    @DisplayName("11. Reviewer workflow: REQUEST_CORRECTION moves status to REVIEW_REQUIRED")
    void testReviewerWorkflowRequestCorrection() {
        PolicyDocumentExtraction extraction = PolicyDocumentExtraction.builder()
                .id("EXT_REV_001_ROOM_RENT_P2")
                .documentRevisionId("REV_001")
                .documentSha256(samplePdfSha256)
                .extractedFieldName("ROOM_RENT_LIMIT")
                .typedFieldValue("1% of SI")
                .extractionStatus("EXTRACTED")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .auditTrail(new ArrayList<>())
                .build();

        when(extractionRepository.findById("EXT_REV_001_ROOM_RENT_P2")).thenReturn(Optional.of(extraction));
        when(extractionRepository.save(any(PolicyDocumentExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyDocumentExtraction corrected = intelligenceService.reviewExtraction(
                "EXT_REV_001_ROOM_RENT_P2", "REQUEST_CORRECTION", "Check if ICU sublimit overrides standard room rent", "senior_auditor@vidhura.ai"
        );

        assertEquals("UNVERIFIED_PENDING_REVIEW", corrected.getVerificationStatus());
        assertEquals("REVIEW_REQUIRED", corrected.getExtractionStatus());
        assertEquals("senior_auditor@vidhura.ai", corrected.getVerifiedBy());
        assertEquals(1, corrected.getAuditTrail().size());
        assertEquals("REQUEST_CORRECTION", corrected.getAuditTrail().get(0).getAction());
        assertEquals("Check if ICU sublimit overrides standard room rent", corrected.getAuditTrail().get(0).getNotes());
    }

    @Test
    @DisplayName("12. Graceful error handling when PDF file is missing or corrupted")
    void testMissingFileGracefulHandling() {
        PolicyDocumentRevision missingRev = PolicyDocumentRevision.builder()
                .id("REV_MISSING_001")
                .registryRecordId("REG_MISSING_001")
                .irdaUin("UIN-MISSING-2024")
                .verifiedSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .storageKey("non_existent_file.pdf")
                .status("VERIFIED")
                .acquisitionState("ACQUIRED")
                .build();

        IrdaiRegistryRecord record = IrdaiRegistryRecord.builder()
                .id("REG_MISSING_001").normalizedUin("UIN-MISSING-2024").classificationStatus("INDIVIDUAL").build();
        when(registryRepository.findById("REG_MISSING_001")).thenReturn(Optional.of(record));
        when(revisionRepository.findById("REV_MISSING_001")).thenReturn(Optional.of(missingRev));

        PolicyDocumentIntelligenceService.DocumentExtractionResult result =
                intelligenceService.processRevision(missingRev, "RUN_ERROR_TEST", false);

        assertEquals("EXTRACTION_FAILED", result.getLifecycleStatus());
        assertEquals(0, result.getExtractedAttributes().size());
        verify(revisionRepository, times(1)).save(argThat(r -> "EXTRACTION_FAILED".equals(r.getStatus())));
    }

    // Helper: generate multi-page realistic policy document
    private byte[] generateMultiPagePolicyPdf() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            // Page 1: Basic Coverage
            PDPage p1 = new PDPage();
            doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                cs.beginText();
                cs.setFont(boldFont, 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("SECTION 1: BASIC COVERAGE");
                cs.setFont(font, 10);
                cs.newLineAtOffset(0, -30);
                cs.showText("The Company will indemnify the Insured Person for medically necessary hospitalisation.");
                cs.endText();
            }

            // Page 2: Inpatient & Room rent & Domiciliary
            PDPage p2 = new PDPage();
            doc.addPage(p2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                cs.beginText();
                cs.setFont(boldFont, 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("SECTION 2: INPATIENT HOSPITALISATION");
                cs.setFont(font, 10);
                cs.newLineAtOffset(0, -30);
                cs.showText("Room rent, boarding and nursing expenses up to 1% of Sum Insured per day.");
                cs.newLineAtOffset(0, -20);
                cs.showText("ICU charges are admissible up to 2% of Sum Insured per day.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Domiciliary hospitalisation expenses covered up to 10% of Sum Insured.");
                cs.endText();
            }

            // Page 3: Waiting periods
            PDPage p3 = new PDPage();
            doc.addPage(p3);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p3)) {
                cs.beginText();
                cs.setFont(boldFont, 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("SECTION 3: WAITING PERIODS");
                cs.setFont(font, 10);
                cs.newLineAtOffset(0, -30);
                cs.showText("Initial waiting period of 30 days shall apply to all illnesses except accidents.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Pre-existing disease waiting period of 36 months of continuous coverage.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Specific listed diseases waiting period of 24 months for cataracts and hernia.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Maternity waiting period of 24 months shall apply for maternity claims.");
                cs.endText();
            }

            // Page 4: Cost-sharing & Benefits
            PDPage p4 = new PDPage();
            doc.addPage(p4);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p4)) {
                cs.beginText();
                cs.setFont(boldFont, 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("SECTION 4: COST SHARING AND BONUSES");
                cs.setFont(font, 10);
                cs.newLineAtOffset(0, -30);
                cs.showText("Co-payment of 20% on each admissible claim for insured members.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Cumulative bonus of 10% on basic sum insured for every claim free year.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Emergency road ambulance expenses covered up to Rs. 2000 per hospitalisation.");
                cs.newLineAtOffset(0, -20);
                cs.showText("AYUSH treatment in a recognized hospital covered up to 100% of sum insured.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Organ donor expenses are covered up to the Sum Insured.");
                cs.endText();
            }

            // Page 5: Exclusions & Riders
            PDPage p5 = new PDPage();
            doc.addPage(p5);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p5)) {
                cs.beginText();
                cs.setFont(boldFont, 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("SECTION 5: PERMANENT EXCLUSIONS AND RIDERS");
                cs.setFont(font, 10);
                cs.newLineAtOffset(0, -30);
                cs.showText("Permanent exclusions include intentional self-injury, cosmetic surgery and treatments.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Optional rider: Critical illness cover available on additional premium.");
                cs.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] generatePdfWithScannedPage() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Page 1: normal
            PDPage p1 = new PDPage();
            doc.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 750);
                cs.showText("This is a standard machine readable page with sufficient textual contents.");
                cs.endText();
            }

            // Page 2: scanned / minimal text (representing image)
            PDPage p2 = new PDPage();
            doc.addPage(p2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                cs.beginText();
                cs.setFont(font, 6);
                cs.newLineAtOffset(50, 750);
                cs.showText("Img 1"); // 5 characters < 40 char threshold
                cs.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
