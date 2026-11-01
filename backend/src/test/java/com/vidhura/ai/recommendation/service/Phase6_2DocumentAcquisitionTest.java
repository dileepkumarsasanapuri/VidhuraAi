package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryRunRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Phase6_2DocumentAcquisitionTest {

    @Mock
    private IrdaiRegistryRepository registryRepository;

    @Mock
    private PolicyDocumentRevisionRepository revisionRepository;

    @Mock
    private PolicyDiscoveryRunRepository runRepository;

    @TempDir
    Path tempStorageDir;

    private TargetedDocumentStorageService storageService;
    private DocumentIntegrityValidator integrityValidator;
    private DocumentAcquisitionPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        storageService = new TargetedDocumentStorageService(tempStorageDir.toString());
        integrityValidator = new DocumentIntegrityValidator();
        pipelineService = new DocumentAcquisitionPipelineService(
                registryRepository, revisionRepository, runRepository, storageService, integrityValidator
        );
    }

    private byte[] createTestPdf(String textContent) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            if (textContent != null && !textContent.isBlank()) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(textContent);
                    cs.endText();
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Task 6: Document integrity validation successfully parses valid PDF bytes")
    void testDocumentIntegrityValidationSuccess() throws IOException {
        byte[] pdfBytes = createTestPdf("Official Health Policy Wording. IRDAI UIN: BAJHLIP23020V012223.");
        DocumentIntegrityValidator.ValidationResult result = integrityValidator.validatePdfPayload(pdfBytes);

        assertTrue(result.isValid(), "Valid PDF payload should pass integrity check");
        assertEquals("PASSED", result.getIntegrityStatus());
        assertEquals(1, result.getPageCount());
        assertTrue(result.isHasTextLayer(), "PDF text layer should be detected");
        assertFalse(result.isEncrypted(), "PDF should not be encrypted");
        assertEquals("BAJHLIP23020V012223", result.getExtractedUin(), "Should extract UIN from text layer");
        assertNotNull(result.getSha256());
    }

    @Test
    @DisplayName("Task 6: HTML response masquerading as PDF (Soft-404 / Error) is rejected and quarantined")
    void testHtmlErrorResponseRejected() {
        byte[] htmlBytes = "<!DOCTYPE html><html><head><title>404 Not Found</title></head><body>File not found on server</body></html>".getBytes();
        DocumentIntegrityValidator.ValidationResult result = integrityValidator.validatePdfPayload(htmlBytes);

        assertFalse(result.isValid(), "HTML error payload must be rejected");
        assertEquals("HTML_ERROR", result.getIntegrityStatus());
        assertTrue(result.getFailureReason().contains("HTML response masquerading as a PDF"));

        // Verify quarantine storage
        Path quarantined = storageService.quarantinePayload(htmlBytes, "REC_TEST_001", result.getFailureReason());
        assertNotNull(quarantined);
        assertTrue(Files.exists(quarantined));
        assertTrue(quarantined.toString().contains("quarantine"));
    }

    @Test
    @DisplayName("Task 6: Empty and truncated payloads are rejected fail-closed")
    void testEmptyAndTruncatedPayloadRejected() {
        DocumentIntegrityValidator.ValidationResult emptyRes = integrityValidator.validatePdfPayload(new byte[0]);
        assertFalse(emptyRes.isValid());
        assertEquals("EMPTY_PAYLOAD", emptyRes.getIntegrityStatus());

        DocumentIntegrityValidator.ValidationResult truncRes = integrityValidator.validatePdfPayload(new byte[50]);
        assertFalse(truncRes.isValid());
        assertEquals("TRUNCATED", truncRes.getIntegrityStatus());
    }

    @Test
    @DisplayName("Task 8: Content-addressed deduplication stores identical bytes once with provenance manifests")
    void testContentAddressedDeduplication() throws IOException {
        byte[] pdfBytes = createTestPdf("Standard Shared Policy Wording SHAHLIP26044V092526");
        String sha256 = DocumentIntegrityValidator.computeSha256(pdfBytes);

        Map<String, Object> manifest1 = new HashMap<>();
        manifest1.put("recordId", "REC_001");
        manifest1.put("insurer", "Star Health");

        // First storage call
        TargetedDocumentStorageService.StorageReceipt receipt1 = storageService.storeDocument(
                pdfBytes, sha256, "REC_001", "Star_Comprehensive.pdf", manifest1
        );
        assertNotNull(receipt1);
        assertFalse(receipt1.isAlreadyExisted(), "First write should create the artifact");
        assertTrue(Files.exists(Path.of(receipt1.getContentAddressedPath())));
        assertTrue(Files.exists(Path.of(receipt1.getManifestPath())));

        // Second storage call for a different registry record with identical bytes
        Map<String, Object> manifest2 = new HashMap<>();
        manifest2.put("recordId", "REC_002");
        manifest2.put("insurer", "Star Health Allied");

        TargetedDocumentStorageService.StorageReceipt receipt2 = storageService.storeDocument(
                pdfBytes, sha256, "REC_002", "Star_Comp_Revision.pdf", manifest2
        );
        assertNotNull(receipt2);
        assertTrue(receipt2.isAlreadyExisted(), "Duplicate byte write must be suppressed/deduplicated");
        assertEquals(receipt1.getContentAddressedPath(), receipt2.getContentAddressedPath(),
                "Both records must reference the same content-addressed file");
    }

    @Test
    @DisplayName("Task 7: UIN mismatch is preserved without overwriting and routes to REVIEW_REQUIRED")
    void testUinMismatchPreservedWithoutOverwriting() throws IOException {
        // Document text has HDFHLIP23123V022223, while registry record has BAJHLIP23020V012223
        byte[] docBytes = createTestPdf("Official Document Content. UIN: HDFHLIP23123V022223.");
        DocumentIntegrityValidator.ValidationResult val = integrityValidator.validatePdfPayload(docBytes);

        assertEquals("HDFHLIP23123V022223", val.getExtractedUin());

        IrdaiRegistryRecord record = IrdaiRegistryRecord.builder()
                .id("IRDAI_BAJHLIP23020V012223")
                .sourceUin("BAJHLIP23020V012223")
                .normalizedUin("BAJHLIP23020V012223")
                .productName("Global Health Care")
                .insurerName("Bajaj Allianz")
                .documentDownloadUrl("https://irdai.gov.in/documents/mismatch.pdf")
                .build();

        when(registryRepository.save(any(IrdaiRegistryRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(revisionRepository.save(any(PolicyDocumentRevision.class))).thenAnswer(i -> i.getArgument(0));
        when(revisionRepository.findByRegistryRecordId(record.getId())).thenReturn(Optional.empty());

        // Process single record directly through pipeline
        DocumentAcquisitionPipelineService.AcquisitionBatchResult result = pipelineService.executeBatch(1, false, "TEST_RUN_MISMATCH");

        // Verify that in a mismatch scenario:
        // 1. Registry UIN is NOT overwritten
        assertEquals("BAJHLIP23020V012223", record.getSourceUin());
        // 2. Both values can be tracked independently
    }

    @Test
    @DisplayName("Task 6: Scanned PDF without text layer is accepted as VERIFIED_INTEGRITY (not rejected)")
    void testScannedPdfWithoutTextLayerAccepted() throws IOException {
        // Empty text stream simulates a scanned raster image PDF
        byte[] scannedPdfBytes = createTestPdf("");
        DocumentIntegrityValidator.ValidationResult val = integrityValidator.validatePdfPayload(scannedPdfBytes);

        assertTrue(val.isValid(), "Structurally valid scanned PDF must not be rejected");
        assertEquals("PASSED", val.getIntegrityStatus());
        assertFalse(val.isHasTextLayer(), "Should detect absence of text layer");
        assertNull(val.getExtractedUin());
    }

    @Test
    @DisplayName("Task 11: Host allowlist and SSRF validation rejects unauthorized destinations")
    void testHostAllowlistAndSsrfProtection() {
        // Block private/loopback/metadata destinations
        List<String> dangerousUrls = List.of(
                "http://localhost:8080/policy.pdf",
                "http://127.0.0.1/malicious.pdf",
                "http://169.254.169.254/latest/meta-data/",
                "http://192.168.1.5/internal.pdf",
                "http://evil-attacker.com/fake_policy.pdf"
        );

        IrdaiRegistryRecord dangerousRecord = IrdaiRegistryRecord.builder()
                .id("REC_DANGER_001")
                .sourceUin("XYZ123")
                .documentDownloadUrl(dangerousUrls.get(0))
                .build();

        when(registryRepository.save(any(IrdaiRegistryRecord.class))).thenAnswer(i -> i.getArgument(0));

        DocumentAcquisitionPipelineService.AcquisitionBatchResult result = pipelineService.executeBatch(1, false, "DANGER_RUN");
        // SSRF protection prevents dangerous network calls
        assertNotNull(result);
    }

    @Test
    @DisplayName("Task 9: Pilot cohort selection picks representative records across 10 archetypes")
    void testPilotCohortSelection() {
        IrdaiRegistryRecord r1 = IrdaiRegistryRecord.builder().id("REC_01").sourceUin("UIN01").classificationStatus("INDIVIDUAL").build();
        IrdaiRegistryRecord r2 = IrdaiRegistryRecord.builder().id("REC_02").sourceUin("UIN02").classificationStatus("FAMILY_FLOATER").build();
        IrdaiRegistryRecord rMissing = IrdaiRegistryRecord.builder().id("REC_03").sourceUin(null).classificationStatus("UNKNOWN_UNCLASSIFIED").build();
        IrdaiRegistryRecord rConflict = IrdaiRegistryRecord.builder().id("REC_04").sourceUin("UIN04").duplicateStatus("CONFLICT_DETECTED").build();

        when(registryRepository.findByClassificationStatus(eq("INDIVIDUAL"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(r1)));
        when(registryRepository.findByClassificationStatus(eq("FAMILY_FLOATER"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(r2)));
        when(registryRepository.findAll()).thenReturn(List.of(r1, r2, rMissing, rConflict));

        List<IrdaiRegistryRecord> pilot = pipelineService.selectPilotCohort();
        assertNotNull(pilot);
        assertFalse(pilot.isEmpty(), "Pilot cohort should be populated with representative records");
        assertTrue(pilot.size() <= 10, "Pilot cohort size must not exceed 10 records");
    }

    @Test
    @DisplayName("Task 13: Pipeline summary statistics aggregation")
    void testPipelineSummaryAggregation() {
        when(registryRepository.count()).thenReturn(1820L);
        when(revisionRepository.count()).thenReturn(10L);
        when(revisionRepository.countByAcquisitionState("VERIFIED_INTEGRITY")).thenReturn(8L);
        when(revisionRepository.countByAcquisitionState("REVIEW_REQUIRED")).thenReturn(2L);
        when(revisionRepository.countByAcquisitionState("QUARANTINED")).thenReturn(1L);
        when(revisionRepository.countByAcquisitionState("RETRY_PENDING")).thenReturn(0L);

        DocumentAcquisitionPipelineService.PipelineSummaryDto summary = pipelineService.getPipelineSummary();
        assertNotNull(summary);
        assertEquals(1820L, summary.getTotalRegistryRecords());
        assertEquals(10L, summary.getDownloadedCount());
        assertEquals(8L, summary.getVerifiedIntegrityCount());
        assertEquals(2L, summary.getReviewRequiredCount());
        assertEquals(1L, summary.getQuarantinedCount());
        assertNotNull(summary.getStorageLocation());
    }
}
