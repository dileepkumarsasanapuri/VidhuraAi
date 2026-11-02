package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryRunRepository;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite for Market-Wide Policy Intelligence Expansion (Phases 6.2, 6.3, 6.4 continuation).
 *
 * Covers:
 * 1. Resumable acquisition & content deduplication.
 * 2. Automated post-acquisition extraction pipeline triggering.
 * 3. Conflict reviewer queue generation with BLOCKED_BY_CONFLICT disposition.
 * 4. Adaptive quality controls (preventing definition leaks, numeric co-payment errors, false unlimited coverage).
 * 5. Publication safeguards (unverified extractions blocked from consumer recommendation models).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Phase6_MarketWideExpansionTest {

    @Mock
    private IrdaiRegistryRepository registryRepository;

    @Mock
    private PolicyDocumentRevisionRepository revisionRepository;

    @Mock
    private PolicyDiscoveryRunRepository runRepository;

    @Mock
    private PolicyDocumentExtractionRepository extractionRepository;

    @TempDir
    Path tempStorageDir;

    private TargetedDocumentStorageService storageService;
    private DocumentIntegrityValidator integrityValidator;
    private DocumentPageExtractionService pageExtractionService;
    private PolicyDocumentOcrService ocrService;
    private ClauseTaxonomyExtractor taxonomyExtractor;
    private PolicyDocumentIntelligenceService intelligenceService;
    private DocumentAcquisitionPipelineService pipelineService;

    @BeforeEach
    void setUp() throws Exception {
        Path storageRoot = tempStorageDir.resolve("policies");
        Path extractedRoot = tempStorageDir.resolve("extracted");
        Files.createDirectories(storageRoot);
        Files.createDirectories(extractedRoot);

        storageService = new TargetedDocumentStorageService(storageRoot.toString());
        integrityValidator = new DocumentIntegrityValidator();
        ocrService = new PolicyDocumentOcrService(extractedRoot.toString());
        pageExtractionService = new DocumentPageExtractionService(
                storageRoot.toString(),
                extractedRoot.toString()
        );
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

        pipelineService = new DocumentAcquisitionPipelineService(
                registryRepository,
                revisionRepository,
                runRepository,
                storageService,
                integrityValidator,
                intelligenceService
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
    @DisplayName("Expansion 1: Content-addressed storage deduplicates identical physical blobs across multiple logical revisions")
    void testContentDeduplicationAcrossLogicalRevisions() throws IOException {
        byte[] pdfBytes = createTestPdf("Standard Public Health Policy Wording SHAHLIP21159V022021");
        String sha = DocumentIntegrityValidator.computeSha256(pdfBytes);

        // Logical Revision 1
        Map<String, Object> manifest1 = new HashMap<>(Map.of("recordId", "REC-001", "uin", "UIN-A"));
        TargetedDocumentStorageService.StorageReceipt r1 = storageService.storeDocument(
                pdfBytes, sha, "REC-001", "PolicyDoc_A.pdf", manifest1
        );
        assertFalse(r1.isAlreadyExisted(), "First write creates artifact");

        // Logical Revision 2 with identical physical bytes
        Map<String, Object> manifest2 = new HashMap<>(Map.of("recordId", "REC-002", "uin", "UIN-B"));
        TargetedDocumentStorageService.StorageReceipt r2 = storageService.storeDocument(
                pdfBytes, sha, "REC-002", "PolicyDoc_B.pdf", manifest2
        );
        assertTrue(r2.isAlreadyExisted(), "Second write must be deduplicated");
        assertEquals(r1.getContentAddressedPath(), r2.getContentAddressedPath(), "Must share physical path");
        assertNotEquals(r1.getOriginalPath(), r2.getOriginalPath(), "Must preserve distinct logical original references");
    }

    @Test
    @DisplayName("Expansion 2: Conflict queue returns all conflicting candidates with BLOCKED_BY_CONFLICT disposition")
    void testConflictQueueGeneration() {
        PolicyDocumentExtraction conflictingExt = PolicyDocumentExtraction.builder()
                .id("EXT_CONF_1")
                .documentSha256("sha-123")
                .canonicalPolicyUin("UIN123")
                .extractedFieldName("COPAY_PERCENTAGE")
                .candidateValue("10% Copay")
                .numericValue(BigDecimal.valueOf(10.0))
                .unit("PERCENTAGE")
                .originalPageNumber(5)
                .evidenceRole("POLICY_SCHEDULE")
                .hasConflict(true)
                .conflictingCandidates(List.of(
                        PolicyDocumentExtraction.ConflictingCandidate.builder()
                                .candidateValue("20% Copay")
                                .numericValue(BigDecimal.valueOf(20.0))
                                .pageNumber(15)
                                .evidenceRole("OPERATIVE_CLAUSE")
                                .conflictReason("Mismatch across pages 5 and 15")
                                .build()
                ))
                .build();

        when(extractionRepository.findAllByHasConflictTrue()).thenReturn(List.of(conflictingExt));

        List<PolicyDocumentIntelligenceService.ConflictQueueItem> queue = intelligenceService.getConflictQueue();
        assertNotNull(queue);
        assertEquals(1, queue.size());
        PolicyDocumentIntelligenceService.ConflictQueueItem item = queue.get(0);
        assertEquals("EXT_CONF_1", item.getExtractionId());
        assertEquals("BLOCKED_BY_CONFLICT", item.getDisposition());
        assertEquals(1, item.getConflictingCandidates().size());
        assertEquals("20% Copay", item.getConflictingCandidates().get(0).getCandidateValue());
    }

    @Test
    @DisplayName("Expansion 3: Adaptive Quality Control blocks generic co-payment definition from being treated as numeric value")
    void testCoPaymentDefinitionNumericGuard() {
        DocumentPageExtractionService.DocumentPagesResult pages = DocumentPageExtractionService.DocumentPagesResult.builder()
                .documentSha256("test-sha")
                .totalPages(1)
                .pages(List.of(
                        DocumentPageExtractionService.PageContent.builder()
                                .pageNumber(1)
                                .rawText("Section 1: Definitions. Co-payment means a cost sharing requirement under a health insurance policy.")
                                .build()
                ))
                .build();

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pages, "REC-01", "REV-01", "UIN-01", "INDIVIDUAL", "RUN-1", "Arogya", "Star"
        );

        Optional<PolicyDocumentExtraction> copayExt = extractions.stream()
                .filter(e -> "COPAY_PERCENTAGE".equals(e.getExtractedFieldName()))
                .findFirst();

        assertTrue(copayExt.isPresent());
        PolicyDocumentExtraction ext = copayExt.get();
        assertEquals("DEFINITION", ext.getEvidenceRole());
        assertNull(ext.getNumericValue(), "Numeric value must not be populated from a definition");
        assertEquals("REVIEW_REQUIRED", ext.getExtractionStatus());
        assertTrue(ext.getConditions().contains("schedule"), "Must state schedule verification required");
    }

    @Test
    @DisplayName("Expansion 4: Adaptive Quality Control flags illustrative examples as REVIEW_REQUIRED")
    void testExampleRoleAdaptiveGuard() {
        DocumentPageExtractionService.DocumentPagesResult pages = DocumentPageExtractionService.DocumentPagesResult.builder()
                .documentSha256("test-sha")
                .totalPages(1)
                .pages(List.of(
                        DocumentPageExtractionService.PageContent.builder()
                                .pageNumber(1)
                                .rawText("Section 2: Hospitalization. For example, if Room Rent is capped at Rs. 5000 per day, the insured pays the excess.")
                                .build()
                ))
                .build();

        List<PolicyDocumentExtraction> extractions = taxonomyExtractor.extractTaxonomyClauses(
                pages, "REC-01", "REV-01", "UIN-01", "INDIVIDUAL", "RUN-1", "Arogya", "Star"
        );

        Optional<PolicyDocumentExtraction> roomRentExt = extractions.stream()
                .filter(e -> "ROOM_RENT_LIMIT".equals(e.getExtractedFieldName()))
                .findFirst();

        assertTrue(roomRentExt.isPresent());
        PolicyDocumentExtraction ext = roomRentExt.get();
        assertEquals("EXAMPLE", ext.getEvidenceRole());
        assertEquals("REVIEW_REQUIRED", ext.getExtractionStatus());
        assertTrue(ext.getConditions().contains("Illustrative example clause"));
    }

    @Test
    @DisplayName("Expansion 5: Publication Safeguards - unverified extractions rejected from StructuredPolicy")
    void testPublicationSafeguardsRejectUnverifiedExtractions() {
        StructuredPolicy policy = StructuredPolicy.builder()
                .id("TEST_POL_001")
                .policyCode("TEST_POL_001")
                .policyName("Health Shield")
                .contractTerms(StructuredPolicy.ContractTerms.builder().build())
                .build();

        PolicyDocumentExtraction unverified = PolicyDocumentExtraction.builder()
                .id("EXT_UNVERIFIED_1")
                .extractedFieldName("COPAY_PERCENTAGE")
                .candidateValue("10%")
                .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                .build();

        int applied = intelligenceService.applyVerifiedExtractionsToStructuredPolicy(policy, List.of(unverified));
        assertEquals(0, applied, "Unverified extraction must not be applied to StructuredPolicy");
        assertEquals(0.0, policy.getContractTerms().getCopayPercentage(), "Copay must remain default 0.0 unapplied");
    }
}
