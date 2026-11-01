package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 5.10.2 — Evidence Identity & Metadata Reconciliation Test Suite
 *
 * Verifies:
 * 1. Physical PDF integrity, SHA-256 digests, and exact text-layer UIN matching for all 9 authentic documents.
 * 2. Three UIN discrepancy resolutions (New India Mediclaim, Star Comprehensive, Aditya Birla Activ One).
 * 3. Bidirectional canonical policy-code and legacy alias resolution without duplicate products.
 * 4. Multi-layered publication gating excluding all 7 expansion synthetics and 5 seed synthetics in empty and populated catalogue states.
 * 5. Startup synchronization idempotency and alias migration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Phase5_10_2EvidenceIdentityReconciliationTest {

    @Mock
    private StructuredPolicyRepository policyRepository;

    private PolicyIngestionService policyIngestionService;
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        policyIngestionService = new PolicyIngestionService(policyRepository);
        recommendationService = new RecommendationService(
                policyRepository,
                null, // RecommendationProfileRepository
                null, // RecommendationResultRepository
                null, // EligibilityEngine
                null, // RankingEngine
                policyIngestionService,
                null  // CatalogMetadataRepository
        );
    }

    // =========================================================================
    // TASK 1 & 2: PHYSICAL PDF INTEGRITY & UIN RECONCILIATION
    // =========================================================================

    @Test
    @DisplayName("Task 2.1: Verify in-document UIN and SHA-256 for all 9 physical PDF files on disk")
    void testAllNinePhysicalPdfFilesMatchReconciledLedger() throws Exception {
        List<StructuredPolicy> authenticCatalog = policyIngestionService.getAuthenticAcquiredCatalog().stream()
                .filter(StructuredPolicy::isAuthenticAcquiredDocument)
                .collect(Collectors.toList());

        assertEquals(9, authenticCatalog.size(), "Authentic catalog must contain exactly 9 physical evidence policies");

        for (StructuredPolicy policy : authenticCatalog) {
            String filename = policy.getPolicyWordingDocument();
            assertNotNull(filename, "Policy document filename cannot be null for " + policy.getPolicyCode());

            ClassPathResource resource = new ClassPathResource("evidence/policies/" + filename);
            assertTrue(resource.exists(), "Physical file must exist on disk: evidence/policies/" + filename);

            // 1. Verify SHA-256 digest
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes;
            try (InputStream is = resource.getInputStream()) {
                fileBytes = is.readAllBytes();
            }
            byte[] hash = digest.digest(fileBytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String calculatedSha256 = hexString.toString();
            assertEquals(policy.getDocumentChecksum().toLowerCase(), calculatedSha256.toLowerCase(),
                    "Physical SHA-256 mismatch for " + filename);

            // 2. Extract PDF text and verify in-document UIN
            try (PDDocument document = Loader.loadPDF(fileBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(document.getNumberOfPages(), 10)); // Check first 10 pages
                String text = stripper.getText(document);

                assertTrue(text.contains(policy.getIrdaUin()),
                        String.format("Physical PDF %s must contain in-document UIN %s in its text layer",
                                filename, policy.getIrdaUin()));
            }
        }
    }

    @Test
    @DisplayName("Task 2.2: Verify reconciliation of the 3 affected UIN products and historical tracking")
    void testThreeUinMismatchesReconciledWithHistoricalLineage() {
        List<StructuredPolicy> authentic = policyIngestionService.getAuthenticAcquiredCatalog();

        // 1. New India Mediclaim
        StructuredPolicy niaMediclaim = authentic.stream()
                .filter(p -> p.matchesPolicyCodeOrAlias("THE_NEW_INDIA_ASSURANCE_COMPAN_V102425"))
                .findFirst().orElseThrow();
        assertEquals("NIAHLIP21277V042021", niaMediclaim.getIrdaUin(),
                "New India Mediclaim must use in-document physical UIN");
        assertEquals("V042021", niaMediclaim.getUinRevision());
        assertEquals("VERIFIED_CANONICAL", niaMediclaim.getUinRevisionStatus());
        assertTrue(niaMediclaim.getHistoricalUins().contains("NIAHLIP25040V102425"),
                "Historical UINs must preserve prospective circular UIN");
        assertEquals("SUCCESSOR_CIRCULAR_PENDING_PHYSICAL_ACQUISITION",
                niaMediclaim.getUinRelationships().get("NIAHLIP25040V102425"));

        // 2. Star Comprehensive
        StructuredPolicy starComp = authentic.stream()
                .filter(p -> p.matchesPolicyCodeOrAlias("STAR_COMPREHENSIVE"))
                .findFirst().orElseThrow();
        assertEquals("SHAHLIP26044V092526", starComp.getIrdaUin(),
                "Star Comprehensive must use physical master circular in-document UIN");
        assertEquals("V092526", starComp.getUinRevision());
        assertEquals("VERIFIED_CANONICAL", starComp.getUinRevisionStatus());
        assertTrue(starComp.getHistoricalUins().contains("SHAHLIP22028V072122"),
                "Historical UINs must preserve predecessor filing alias");
        assertEquals("PREDECESSOR_FILING_FILENAME_ALIAS",
                starComp.getUinRelationships().get("SHAHLIP22028V072122"));

        // 3. Aditya Birla Activ One
        StructuredPolicy abActivOne = authentic.stream()
                .filter(p -> p.matchesPolicyCodeOrAlias("ADITYA_BIRLA_ACTIV_ONE"))
                .findFirst().orElseThrow();
        assertEquals("ADIHLIP27048V022627", abActivOne.getIrdaUin(),
                "Aditya Birla Activ One must use in-document physical UIN");
        assertEquals("V022627", abActivOne.getUinRevision());
        assertEquals("VERIFIED_CANONICAL", abActivOne.getUinRevisionStatus());
        assertTrue(abActivOne.getHistoricalUins().contains("ADIHLIP24040V012324"));
        assertTrue(abActivOne.getHistoricalUins().contains("ADIHLIP24044V012324"));
        assertTrue(abActivOne.getHistoricalUins().contains("ADIHLIP24108V012324"));
        assertEquals("PREDECESSOR_VARIANT_ALIAS", abActivOne.getUinRelationships().get("ADIHLIP24040V012324"));
        assertEquals("REVIEW_REQUIRED_UNVERIFIED_REPORT_ALIAS", abActivOne.getUinRelationships().get("ADIHLIP24108V012324"));

        // 4. United India Senior Citizen
        StructuredPolicy uiicSenior = authentic.stream()
                .filter(p -> p.matchesPolicyCodeOrAlias("UNITED_INDIA_SENIOR_CITIZEN"))
                .findFirst().orElseThrow();
        assertEquals("UIIHLIP21114V032021", uiicSenior.getIrdaUin(),
                "UIIC Senior Citizen must use physical in-document UIN");
        assertTrue(uiicSenior.getHistoricalUins().contains("UIIHLIP22129V062122"));
        assertEquals("REVIEW_REQUIRED_UNVERIFIED_REPORT_ALIAS", uiicSenior.getUinRelationships().get("UIIHLIP22129V062122"));
    }

    // =========================================================================
    // TASK 3: CANONICAL POLICY CODES & LEGACY ALIAS RESOLUTION
    // =========================================================================

    @Test
    @DisplayName("Task 3: Verify bidirectional resolution for the 3 legacy aliases without duplicate records")
    void testLegacyAliasesResolveToCanonicalRecords() {
        List<StructuredPolicy> authentic = policyIngestionService.getAuthenticAcquiredCatalog();

        // 1. UNITED_INDIA_FAMILY_MEDICARE <=> UNITED_INDIA_INSURANCE_COMPANY_V042122
        StructuredPolicy uiicFam = authentic.stream()
                .filter(p -> "UNITED_INDIA_FAMILY_MEDICARE".equals(p.getPolicyCode()))
                .findFirst().orElseThrow();
        assertEquals("UNITED_INDIA_FAMILY_MEDICARE", uiicFam.getPolicyCode());
        assertTrue(uiicFam.getPolicyAliases().contains("UNITED_INDIA_INSURANCE_COMPANY_V042122"));
        assertTrue(uiicFam.matchesPolicyCodeOrAlias("UNITED_INDIA_FAMILY_MEDICARE"));
        assertTrue(uiicFam.matchesPolicyCodeOrAlias("UNITED_INDIA_INSURANCE_COMPANY_V042122"));

        // 2. THE_NEW_INDIA_PREMIER_MEDICLAIM <=> NEW_INDIA_PREMIER_MEDICLAIM_V032021
        StructuredPolicy niaPrem = authentic.stream()
                .filter(p -> "THE_NEW_INDIA_PREMIER_MEDICLAIM".equals(p.getPolicyCode()))
                .findFirst().orElseThrow();
        assertEquals("THE_NEW_INDIA_PREMIER_MEDICLAIM", niaPrem.getPolicyCode());
        assertTrue(niaPrem.getPolicyAliases().contains("NEW_INDIA_PREMIER_MEDICLAIM_V032021"));
        assertTrue(niaPrem.matchesPolicyCodeOrAlias("THE_NEW_INDIA_PREMIER_MEDICLAIM"));
        assertTrue(niaPrem.matchesPolicyCodeOrAlias("NEW_INDIA_PREMIER_MEDICLAIM_V032021"));

        // 3. UNITED_INDIA_SENIOR_CITIZEN <=> UNITED_INDIA_INDIVIDUAL_SENIOR_CITIZEN
        StructuredPolicy uiicSenior = authentic.stream()
                .filter(p -> "UNITED_INDIA_SENIOR_CITIZEN".equals(p.getPolicyCode()))
                .findFirst().orElseThrow();
        assertEquals("UNITED_INDIA_SENIOR_CITIZEN", uiicSenior.getPolicyCode());
        assertTrue(uiicSenior.getPolicyAliases().contains("UNITED_INDIA_INDIVIDUAL_SENIOR_CITIZEN"));
        assertTrue(uiicSenior.matchesPolicyCodeOrAlias("UNITED_INDIA_SENIOR_CITIZEN"));
        assertTrue(uiicSenior.matchesPolicyCodeOrAlias("UNITED_INDIA_INDIVIDUAL_SENIOR_CITIZEN"));

        // 4. Assert zero duplicates in all catalog policies
        List<StructuredPolicy> all = policyIngestionService.getAllCatalogPolicies();
        Set<String> uniqueCodes = new HashSet<>();
        for (StructuredPolicy p : all) {
            assertTrue(uniqueCodes.add(p.getPolicyCode()), "Duplicate policy code detected in catalogue: " + p.getPolicyCode());
        }
    }

    // =========================================================================
    // TASK 4: SYNTHETIC POLICY PUBLICATION EXCLUSION
    // =========================================================================

    @Test
    @DisplayName("Task 4.1: Verify all 7 expansion synthetic fixtures are strictly excluded by publication gate")
    void testAllSevenExpansionSyntheticFixturesBlockedByPublicationGate() {
        List<StructuredPolicy> allAcquired = policyIngestionService.getAuthenticAcquiredCatalog();

        List<String> expectedSyntheticCodes = List.of(
                "NIC_NATIONAL_MEDICLAIM_PLUS",
                "OIC_HAPPY_FAMILY_FLOATER",
                "BAGIC_HEALTH_GUARD",
                "NIVA_BUPA_REASSURE_2",
                "ICICI_LOMBARD_ELEVATE",
                "TATA_AIG_MEDICARE",
                "MANIPAL_CIGNA_PROHEALTH_PRIME"
        );

        for (String code : expectedSyntheticCodes) {
            StructuredPolicy synthetic = allAcquired.stream()
                    .filter(p -> p.matchesPolicyCodeOrAlias(code))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Synthetic fixture not found: " + code));

            // Verify synthetic markers
            assertTrue(synthetic.isSyntheticFixture(), "Must have syntheticFixture=true: " + code);
            assertEquals("TEST_FIXTURE_OR_SYNTHETIC", synthetic.getDocumentAuthenticityStatus());
            assertEquals("SYNTHETIC_DEMO", synthetic.getReviewerApprovalStatus());
            assertFalse(synthetic.isActive(), "Must have active=false: " + code);
            assertFalse(synthetic.isAuthenticAcquiredDocument(), "isAuthenticAcquiredDocument must return false: " + code);

            // Crucial: Must be blocked by recommendation publication gate
            assertFalse(recommendationService.isPublicationReady(synthetic),
                    "Synthetic fixture must be rejected by isPublicationReady: " + code);
        }
    }

    @Test
    @DisplayName("Task 4.2: Verify fail-closed behavior when synthetic flags are missing or altered")
    void testPublicationGateFailsClosedIfSyntheticFlagsAltered() {
        // Construct adversarial policy with syntheticFixture=false, active=true, but SYNTHETIC_DEMO approval
        StructuredPolicy adversarial = StructuredPolicy.builder()
                .policyCode("ADVERSARIAL_POLICY_001")
                .policyName("Adversarial Trojan Policy")
                .provider("Mock Insurance Co.")
                .irdaUin("MOKHLIP12345V012024")
                .uinRevision("V012024")
                .active(true)
                .syntheticFixture(false) // Adversarial attempt to bypass synthetic flag
                .documentAuthenticityStatus("VERIFIED_OFFICIAL_SOURCE")
                .reviewerApprovalStatus("SYNTHETIC_DEMO") // Reviewer status still flags synthetic
                .verificationStatus("VERIFIED_CONTRACTUAL_SPECIFICATION")
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder().sumInsured(500000).build()))
                .contractTerms(StructuredPolicy.ContractTerms.builder().roomRentLimit("1% SI").build())
                .build();

        assertFalse(recommendationService.isPublicationReady(adversarial),
                "isPublicationReady must fail closed when reviewerApprovalStatus is SYNTHETIC_DEMO");

        // Construct policy with REVIEW_REQUIRED revision status
        StructuredPolicy reviewRequired = StructuredPolicy.builder()
                .policyCode("REVIEW_REQUIRED_POLICY_002")
                .policyName("Unverified Revision Policy")
                .provider("Mock Insurance Co.")
                .irdaUin("MOKHLIP12345V022025")
                .uinRevision("V022025")
                .uinRevisionStatus("REVIEW_REQUIRED")
                .active(true)
                .syntheticFixture(false)
                .documentAuthenticityStatus("VERIFIED_OFFICIAL_SOURCE")
                .reviewerApprovalStatus("REVIEWER_APPROVED")
                .verificationStatus("VERIFIED_CONTRACTUAL_SPECIFICATION")
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder().sumInsured(500000).build()))
                .contractTerms(StructuredPolicy.ContractTerms.builder().roomRentLimit("No Capping").build())
                .build();

        assertFalse(recommendationService.isPublicationReady(reviewRequired),
                "isPublicationReady must fail closed when uinRevisionStatus is REVIEW_REQUIRED");
    }

    @Test
    @DisplayName("Task 4.3: Verify recommendation publication exclusion in empty vs populated catalog states")
    void testRecommendationExclusionInEmptyAndPopulatedCatalogStates() {
        // 1. Populated DB state: Database contains all policies
        List<StructuredPolicy> allCatalog = policyIngestionService.getAllCatalogPolicies();
        when(policyRepository.findByActiveTrue()).thenReturn(
                allCatalog.stream().filter(StructuredPolicy::isActive).collect(Collectors.toList())
        );
        when(policyRepository.count()).thenReturn((long) allCatalog.size());

        List<StructuredPolicy> publishedPopulated = recommendationService.loadCatalog();
        assertEquals(9, publishedPopulated.size(), "Only the 9 authentic policies must be eligible in populated catalog");
        for (StructuredPolicy p : publishedPopulated) {
            assertFalse(p.isSyntheticFixture(), "No synthetic fixture may be published: " + p.getPolicyCode());
            assertTrue(p.isAuthenticAcquiredDocument(), "Only authentic policies may be published: " + p.getPolicyCode());
        }

        // 2. Fresh unseeded DB state (count = 0)
        when(policyRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(policyRepository.count()).thenReturn(0L);

        // Reset bootstrap in RecommendationService
        recommendationService.resetBootstrapStateForTesting();

        List<StructuredPolicy> publishedFallback = recommendationService.loadCatalog();
        assertEquals(9, publishedFallback.size(), "In initial unseeded fallback, all authentic acquired policies are publication-ready");
        for (StructuredPolicy p : publishedFallback) {
            assertFalse(p.isSyntheticFixture(), "No synthetic fixture may be published in fallback: " + p.getPolicyCode());
            assertTrue(p.isAuthenticAcquiredDocument(), "Only authentic policies may be published in fallback: " + p.getPolicyCode());
        }
    }

    // =========================================================================
    // TASK 5: MONGODB SYNCHRONIZATION & IDEMPOTENCY
    // =========================================================================

    @Test
    @DisplayName("Task 5: Verify startup synchronization migrates legacy aliases and is idempotent")
    void testStartupSynchronizationIsIdempotentAndMigratesAliases() {
        // Simulate existing MongoDB containing a record under the legacy alias
        StructuredPolicy existingLegacyRecord = StructuredPolicy.builder()
                .id("mongo-id-uiic-001")
                .policyCode("UNITED_INDIA_INSURANCE_COMPANY_V042122")
                .policyName("United India Family Medicare Policy (Old)")
                .irdaUin("UIIHLIP22070V042122")
                .uinRevision("V042122")
                .active(true)
                .build();

        Map<String, StructuredPolicy> simulatedMongoDb = new HashMap<>();
        simulatedMongoDb.put(existingLegacyRecord.getPolicyCode(), existingLegacyRecord);

        when(policyRepository.count()).thenAnswer(inv -> (long) simulatedMongoDb.size());
        when(policyRepository.findByPolicyCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return Optional.ofNullable(simulatedMongoDb.get(code));
        });
        when(policyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("generated-id-" + p.getPolicyCode());
            }
            simulatedMongoDb.put(p.getPolicyCode(), p);
            return p;
        });

        // First synchronization pass
        policyIngestionService.run();

        // Verify that the policy was migrated and updated with the existing MongoDB ID
        StructuredPolicy migrated = simulatedMongoDb.get("UNITED_INDIA_FAMILY_MEDICARE");
        assertNotNull(migrated, "Policy must be saved under canonical code UNITED_INDIA_FAMILY_MEDICARE");
        assertEquals("mongo-id-uiic-001", migrated.getId(), "Must preserve original MongoDB ID from legacy record");
        assertTrue(migrated.getPolicyAliases().contains("UNITED_INDIA_INSURANCE_COMPANY_V042122"));

        int countAfterFirstSync = simulatedMongoDb.size();

        // Second synchronization pass (testing idempotency)
        policyIngestionService.run();

        assertEquals(countAfterFirstSync, simulatedMongoDb.size(),
                "Repeated synchronization must be idempotent and not create duplicate records");
        assertEquals("mongo-id-uiic-001", simulatedMongoDb.get("UNITED_INDIA_FAMILY_MEDICARE").getId(),
                "Document ID must remain unchanged across repeated sync runs");
    }
}
