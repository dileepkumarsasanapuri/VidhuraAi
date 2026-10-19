package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyDiscoveryAndIngestionTest {

    @Mock
    private StructuredPolicyRepository structuredPolicyRepository;

    @Mock
    private RecommendationProfileRepository profileRepository;

    @Mock
    private RecommendationResultRepository resultRepository;

    private EligibilityEngine eligibilityEngine;
    private RankingEngine rankingEngine;
    private PolicyIngestionService seedIngestionService;
    private RecommendationService recommendationService;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicyPublicationService publicationService;

    // In-memory simulated MongoDB policy collection
    private final Map<String, StructuredPolicy> mockMongoDb = new HashMap<>();

    @BeforeEach
    void setUp() {
        mockMongoDb.clear();

        eligibilityEngine = new EligibilityEngine();
        rankingEngine = new RankingEngine();
        seedIngestionService = new PolicyIngestionService(structuredPolicyRepository);

        // Seed initial 7 policies into mock DB
        for (StructuredPolicy p : seedIngestionService.getSeedCatalog()) {
            p.setId(UUID.randomUUID().toString());
            mockMongoDb.put(p.getId(), p);
        }

        // Configure repository mock to simulate MongoDB behavior
        when(structuredPolicyRepository.findAll()).thenAnswer(inv -> new ArrayList<>(mockMongoDb.values()));
        when(structuredPolicyRepository.findByActiveTrue()).thenAnswer(inv ->
                mockMongoDb.values().stream().filter(StructuredPolicy::isActive).toList());
        when(structuredPolicyRepository.findById(any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(mockMongoDb.get(id));
        });
        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID().toString());
            mockMongoDb.put(p.getId(), p);
            return p;
        });
        when(structuredPolicyRepository.findByPolicyCode(any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return mockMongoDb.values().stream().filter(p -> code.equals(p.getPolicyCode())).findFirst();
        });
        when(structuredPolicyRepository.findByIrdaUin(any())).thenAnswer(inv -> {
            String uin = inv.getArgument(0);
            return mockMongoDb.values().stream().filter(p -> uin != null && uin.equalsIgnoreCase(p.getIrdaUin())).findFirst();
        });

        recommendationService = new RecommendationService(
                structuredPolicyRepository,
                profileRepository,
                resultRepository,
                eligibilityEngine,
                rankingEngine,
                seedIngestionService
        );

        discoveryPipelineService = new PolicyDiscoveryPipelineService(structuredPolicyRepository);
        publicationService = new PolicyPublicationService(structuredPolicyRepository, recommendationService);

        recommendationService.refreshCatalog();
    }

    @Test
    void testCatalogEvaluatesFromMongoDbInsteadOfFixedList() {
        // Initially 7 verified policies
        List<StructuredPolicy> initialCatalog = recommendationService.getCatalog();
        assertEquals(7, initialCatalog.size(), "Initial catalog should contain 7 verified policies from MongoDB");

        // Verify that adding an 8th verified policy in MongoDB dynamically expands the catalog without engine changes
        StructuredPolicy newPolicy = StructuredPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyCode("TATA_AIG_MEDICARE_PREMIER")
                .policyName("Tata AIG MediCare Premier")
                .provider("Tata AIG General Insurance Co. Ltd.")
                .irdaUin("TATHLIP21211V022021")
                .uinRevision("V022021")
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .planName("MediCare Premier Floater")
                .category("Comprehensive Health Insurance")
                .policyWordingDocument("Tata_AIG_MediCare_Wordings_v2024.pdf")
                .active(true)
                .verifiedBy("IRDAI Senior Reviewer")
                .publicationDate(Instant.now())
                .eligibility(StructuredPolicy.EligibilityRules.builder()
                        .minAdultAge(18).maxAdultAge(65)
                        .minChildAgeDays(91).maxChildAgeYears(25)
                        .allowedFamilyTypes(List.of("INDIVIDUAL", "SELF_AND_SPOUSE", "FAMILY_WITH_CHILDREN", "PARENTS"))
                        .minSumInsured(500000L).maxSumInsured(50000000L)
                        .allowedZones(List.of("Zone 1", "Zone 2", "Zone 3"))
                        .build())
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("No Limit (Single Private AC Room or higher)")
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(24)
                        .waitingPeriodInitialDays(30)
                        .waitingPeriodSpecificMonths(24)
                        .restorationBenefit("100% reload of Sum Insured unlimited times")
                        .noClaimBonus("50% per claim-free year up to 100%")
                        .build())
                .sumInsuredTiers(List.of(
                        StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000L).formattedSumInsured("₹10 Lakh")
                                .premiumAge18To35(12000).premiumAge36To45(16000)
                                .premiumAge46To55(24000).premiumAge56To65(38000)
                                .premiumAge66Plus(58000).spouseLoadingFactor(0.70).childLoadingFactor(0.30)
                                .build()
                ))
                .insurerMetrics(StructuredPolicy.InsurerMetrics.builder()
                        .claimSettlementRatio(96.2).incurredClaimsRatio(79.0).cashlessHospitalsCount(11000)
                        .csrReportingPeriod("IRDAI Annual Report FY 2022-23 (Table 4.5)")
                        .csrMetricName("IRDAI Claim Settlement Ratio")
                        .build())
                .documentEvidence(List.of(
                        StructuredPolicy.PolicyEvidenceItem.builder()
                                .clauseName("Room Rent")
                                .sectionReference("Section 3.1")
                                .verbatimText("No capping on room rent charges.")
                                .verifiedFact("Single private AC room without limit")
                                .build()
                ))
                .build();

        mockMongoDb.put(newPolicy.getId(), newPolicy);
        recommendationService.refreshCatalog();

        List<StructuredPolicy> expandedCatalog = recommendationService.getCatalog();
        assertEquals(8, expandedCatalog.size(), "Catalog should now dynamically contain 8 policies from MongoDB");

        // Run full recommendation pipeline with 8 policies
        RecommendationRequestDto request = RecommendationRequestDto.builder()
                .primaryAge(30)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Mumbai")
                .annualBudget(20000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .primaryPriority("BALANCED")
                .members(new ArrayList<>())
                .preExistingConditions(new ArrayList<>())
                .build();

        RecommendationResponseDto response = recommendationService.generateRecommendations("user-test", request);
        assertNotNull(response);
        assertEquals(8, response.getTotalPoliciesEvaluated(), "Recommendation engine should evaluate all 8 policies from MongoDB without code change");
        assertTrue(response.getRankedPolicies().stream().anyMatch(p -> "TATA_AIG_MEDICARE_PREMIER".equals(p.getPolicyCode())),
                "Tata AIG should be successfully ranked in recommendations");
    }

    @Test
    void testAutomatedPolicyDiscoveryAndUncertaintyDetection() {
        String rawPolicyText = """
                BAJAJ ALLIANZ GENERAL INSURANCE CO. LTD.
                HEALTH GUARD INDIVIDUAL & FAMILY FLOATER POLICY
                IRDAI Registration UIN: BAGHLIP21115V042021
                Section 3. Scope of Cover
                3.1 Room Rent: Capped at 1% of Sum Insured per day for standard room.
                3.2 Co-Payment: A mandatory 10% co-payment applies on all admissible claims.
                Section 4. Waiting Periods
                4.1 Pre-existing diseases (PED) covered after 36 months of continuous coverage.
                4.2 Initial waiting period of 30 days applies.
                4.3 Specific ailments covered after 24 months.
                Section 5. Sum Insured & Bonus
                100% reinstatement of sum insured once in a policy year.
                """;

        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Bajaj_Allianz_Health_Guard_Wordings.pdf")
                .documentUrl("https://www.bajajallianz.com/health-guard")
                .sourceType("POLICY_WORDING_PDF")
                .rawPolicyText(rawPolicyText)
                .autoPublishIfHighConfidence(false)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        assertNotNull(result);
        assertNotNull(result.getPolicyId());
        assertEquals("BAGHLIP21115V042021", result.getIrdaUin());
        assertEquals("V042021", result.getUinRevision());
        assertEquals("Bajaj Allianz General Insurance Co. Ltd.", result.getProvider());
        assertEquals("EXTRACTION_PENDING_REVIEW", result.getVerificationStatus(),
                "Discovered policy should default to EXTRACTION_PENDING_REVIEW, not published");
        assertFalse(result.isReadyForPublication(), "Server-side publication gate must block auto-publication");
        assertTrue(result.isRequiresHumanVerification(), "All automated extractions must require human verification");
        assertEquals(100.0, result.getContractConfidenceScore(), "Contractual clauses should have 100% confidence");
        assertEquals("MODELED_ESTIMATE_ONLY", result.getPricingStatus(), "Pricing should be identified as modeled");
        assertFalse(result.getCriticalAuditFindings().isEmpty());

        // Verify the extracted contract terms
        StructuredPolicy savedDraft = mockMongoDb.get(result.getPolicyId());
        assertNotNull(savedDraft);
        assertFalse(savedDraft.isActive(), "Extracted policy draft must remain inactive until reviewed");
        assertTrue(savedDraft.getContractTerms().isRoomRentCapped(), "Room rent should be detected as capped at 1%");
        assertEquals(10.0, savedDraft.getContractTerms().getCopayPercentage(), "10% copay should be detected");
        assertEquals(36, savedDraft.getContractTerms().getWaitingPeriodPedMonths(), "36 months PED should be detected");
    }

    @Test
    void testExtractedDocumentsCannotBeAutoPublishedRegardlessOfConfidence() {
        String rawPolicyText = """
                BAJAJ ALLIANZ GENERAL INSURANCE CO. LTD.
                HEALTH GUARD INDIVIDUAL & FAMILY FLOATER POLICY
                IRDAI Registration UIN: BAGHLIP21115V042021
                Section 3. Scope of Cover
                3.1 Room Rent: Capped at 1% of Sum Insured per day for standard room.
                3.2 Co-Payment: A mandatory 10% co-payment applies on all admissible claims.
                Section 4. Waiting Periods
                4.1 Pre-existing diseases (PED) covered after 36 months of continuous coverage.
                Section 5. Sum Insured & Bonus
                100% reinstatement of sum insured once in a policy year.
                """;

        // Even with autoPublishIfHighConfidence = true, server-side publication gate MUST enforce EXTRACTION_PENDING_REVIEW
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Bajaj_Allianz_Health_Guard_Wordings.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .rawPolicyText(rawPolicyText)
                .autoPublishIfHighConfidence(true) // Caller requests auto-publish
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        assertEquals("EXTRACTION_PENDING_REVIEW", result.getVerificationStatus(),
                "Server-side gate must enforce EXTRACTION_PENDING_REVIEW even if auto-publish was requested");
        assertFalse(result.isReadyForPublication(), "Document cannot be published merely because confidence score is high");

        StructuredPolicy savedDraft = mockMongoDb.get(result.getPolicyId());
        assertNotNull(savedDraft);
        assertFalse(savedDraft.isActive(), "Policy draft must NOT be active in database");

        // Verify it is excluded from active user recommendations
        List<StructuredPolicy> activeCatalog = recommendationService.getCatalog();
        assertFalse(activeCatalog.stream().anyMatch(p -> p.getId().equals(result.getPolicyId())),
                "Unreviewed policy must never appear in active user recommendations");
    }

    @Test
    void testDecoupledContractVsPricingConfidence() {
        // High contract certainty, but no Form NL-25 prospectus rates in text
        String docWithoutProspectus = """
                NIVA BUPA HEALTH INSURANCE CO. LTD.
                REASSURE 2.0 POLICY WORDINGS
                IRDAI Registration UIN: MAXHLIP23036V022223
                Section 3. Scope of Cover
                3.1 Room Rent: No room rent capping. Single Private AC Room or higher.
                3.2 Co-Payment: Zero copay across all network hospitals.
                Section 4. Waiting Periods
                4.1 Pre-existing diseases (PED) covered after 24 months of continuous coverage.
                Section 5. Sum Insured & Restoration
                Unlimited restoration of sum insured available.
                """;

        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Niva_Bupa_ReAssure.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .rawPolicyText(docWithoutProspectus)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        // Contractual clauses are fully verified (100 pts)
        assertEquals(100.0, result.getContractConfidenceScore(), "Contractual clauses must have 100% confidence");
        assertTrue(result.getContractUncertainFields().isEmpty(), "No contractual ambiguities should be flagged");

        // Pricing is evaluated independently
        assertEquals("MODELED_ESTIMATE_ONLY", result.getPricingStatus(),
                "Without prospectus rate tables, pricing must be flagged as MODELED_ESTIMATE_ONLY");
        assertTrue(result.getPricingUncertainFields().contains("UNVERIFIED_PROSPECTUS_RATES"),
                "Missing Form NL-25 prospectus rates must be flagged in pricing uncertainties");
        assertTrue(result.getPricingConfidenceScore() < 80.0, "Pricing confidence must reflect uncalibrated prospectus");
    }

    @Test
    void testPolicyWithAmbiguousClausesGeneratesUncertainFields() {
        String incompleteDoc = """
                XYZ HEALTH SCHEME
                Some generic promotional text without IRDAI UIN.
                Co-pay terms are not clearly stated.
                Room rent: contact customer support.
                """;

        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Incomplete_Draft.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .rawPolicyText(incompleteDoc)
                .autoPublishIfHighConfidence(false)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        assertNotNull(result);
        assertEquals("EXTRACTION_PENDING_REVIEW", result.getVerificationStatus());
        assertFalse(result.isReadyForPublication());
        assertTrue(result.getContractConfidenceScore() < 70.0, "Contract confidence score should be low for incomplete document");
        assertFalse(result.getContractUncertainFields().isEmpty(), "Contract uncertain fields must be flagged for human review");
        assertTrue(result.getContractUncertainFields().contains("UNCERTAIN_IRDAI_UIN"), "Missing UIN should be flagged");
    }

    @Test
    void testVerificationBeforePublicationGate() {
        // Create an unverified policy draft with status EXTRACTION_PENDING_REVIEW
        StructuredPolicy draft = StructuredPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyCode("UNVERIFIED_DRAFT_PLAN")
                .policyName("Unverified Medical Scheme")
                .provider("Test Insurer Ltd.")
                .irdaUin("TSTHLIP99999V012024")
                .uinRevision("V012024")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractTerms(StructuredPolicy.ContractTerms.builder().build())
                .sumInsuredTiers(List.of())
                .build();

        mockMongoDb.put(draft.getId(), draft);
        recommendationService.refreshCatalog();

        // Ensure the unverified draft policy is BLOCKED from entering the recommendation catalog
        List<StructuredPolicy> activeCatalog = recommendationService.getCatalog();
        assertFalse(activeCatalog.stream().anyMatch(p -> "UNVERIFIED_DRAFT_PLAN".equals(p.getPolicyCode())),
                "Unverified draft policy must be blocked from entering active recommendation catalog");

        // 1. Publication without reviewer identity MUST be rejected server-side
        PolicyDiscoveryDto.PolicyVerificationRequest emptyReviewerReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("")
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> publicationService.verifyAndPublish(draft.getId(), emptyReviewerReq),
                "Publication must be rejected when reviewer identity is missing");

        // 2. Publication with unverified critical clauses MUST be rejected server-side
        PolicyDiscoveryDto.PolicyVerificationRequest validReviewerReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Compliance Auditor Priya Sharma")
                .build();
        assertThrows(IllegalStateException.class,
                () -> publicationService.verifyAndPublish(draft.getId(), validReviewerReq),
                "Publication must be rejected when room rent or PED terms remain unverified");

        // 3. Reviewer verifies and supplies verified contract terms and tiers
        draft.setContractTerms(StructuredPolicy.ContractTerms.builder()
                .roomRentLimit("No Limit")
                .roomRentCapped(false)
                .waitingPeriodPedMonths(36)
                .build());
        draft.setEligibility(StructuredPolicy.EligibilityRules.builder()
                .minAdultAge(18).maxAdultAge(65).build());
        draft.setSumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder()
                .sumInsured(1000000L).premiumAge18To35(10000).build()));

        StructuredPolicy published = publicationService.verifyAndPublish(draft.getId(), validReviewerReq);

        assertEquals("VERIFIED_OFFICIAL_CATALOG", published.getVerificationStatus());
        assertTrue(published.isActive());
        assertEquals("Compliance Auditor Priya Sharma", published.getVerifiedBy());
        assertNotNull(published.getPublicationDate());

        // Verify that it is now immediately included in recommendations
        List<StructuredPolicy> updatedCatalog = recommendationService.getCatalog();
        assertTrue(updatedCatalog.stream().anyMatch(p -> "UNVERIFIED_DRAFT_PLAN".equals(p.getPolicyCode())),
                "Approved policy should now be available in recommendation catalog");
    }

    @Test
    void testFlaggingOutdatedPolicyExcludesItFromRecommendations() {
        // Retrieve an active policy from catalog
        List<StructuredPolicy> catalog = recommendationService.getCatalog();
        StructuredPolicy target = catalog.get(0);
        String targetCode = target.getPolicyCode();

        // Flag as FLAGGED_OUTDATED (e.g. revision expired)
        StructuredPolicy flagged = publicationService.flagPolicy(
                target.getId(),
                "IRDAI released revised version V022024; V01 filing withdrawn by insurer.",
                "FLAGGED_OUTDATED"
        );

        assertEquals("FLAGGED_OUTDATED", flagged.getVerificationStatus());
        assertFalse(flagged.isActive());

        // Verify it is excluded from active recommendations
        List<StructuredPolicy> refreshedCatalog = recommendationService.getCatalog();
        assertFalse(refreshedCatalog.stream().anyMatch(p -> targetCode.equals(p.getPolicyCode())),
                "Flagged outdated policy must be instantly excluded from active catalog");
    }

    @Test
    void testCatalogStats() {
        PolicyDiscoveryDto.CatalogStatsDto stats = publicationService.getCatalogStats();
        assertNotNull(stats);
        assertEquals(7, stats.getTotalPolicies());
        assertEquals(7, stats.getVerifiedPublishedPolicies());
        assertEquals(0, stats.getPendingReviewPolicies());
        assertEquals(0, stats.getFlaggedOrOutdatedPolicies());
    }

    @Test
    void testRepeatedIngestionOfPublishedPolicyReusesDraft() {
        // Ensure an active published policy exists in repository
        List<StructuredPolicy> catalog = recommendationService.getCatalog();
        StructuredPolicy publishedPolicy = catalog.stream()
                .filter(p -> "HDFC_OPTIMA_SECURE".equals(p.getPolicyCode()))
                .findFirst()
                .orElse(catalog.get(0));
        String publishedCode = publishedPolicy.getPolicyCode();
        String publishedId = publishedPolicy.getId();
        assertTrue(publishedPolicy.isActive());

        String rawDoc = "Product Name: " + publishedPolicy.getPolicyName() + "\n" +
                "IRDAI UIN: " + publishedPolicy.getIrdaUin() + "\n" +
                "Revision: " + (publishedPolicy.getUinRevision() != null ? publishedPolicy.getUinRevision() : "V012021") + "\n" +
                "Room rent: No capping. PED waiting period: 36 months.\n" +
                "Sum insured: 10 Lakh premium is Rs 10000.";

        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .policyName(publishedPolicy.getPolicyName())
                .rawPolicyText(rawDoc)
                .sourceType("INSURER_PORTAL")
                .documentUrl("https://insurer.com/policy.pdf")
                .build();

        // First ingestion of published policy: stages as draft
        PolicyDiscoveryDto.PolicyIngestionResultDto result1 = discoveryPipelineService.discoverAndIngest(req);
        assertNotNull(result1.getPolicyId());
        String expectedDraftCode = publishedCode + "_DRAFT_" + (publishedPolicy.getUinRevision() != null ? publishedPolicy.getUinRevision() : "V012021");
        assertEquals(expectedDraftCode, result1.getPolicyCode());
        String draftId1 = result1.getPolicyId();

        // Published policy must still be active and unchanged
        StructuredPolicy stillPublished = structuredPolicyRepository.findById(publishedId).orElseThrow();
        assertTrue(stillPublished.isActive());
        assertEquals(publishedCode, stillPublished.getPolicyCode());

        // Second ingestion with the same revision: must look up and reuse the draft entity ID
        PolicyDiscoveryDto.PolicyIngestionResultDto result2 = discoveryPipelineService.discoverAndIngest(req);
        assertEquals(draftId1, result2.getPolicyId(), "Repeated ingestion must reuse existing draft's entity ID");
        assertEquals(expectedDraftCode, result2.getPolicyCode());

        // Check that only ONE draft entity exists in MongoDB for this draft code
        long draftCount = mockMongoDb.values().stream()
                .filter(p -> expectedDraftCode.equals(p.getPolicyCode()))
                .count();
        assertEquals(1, draftCount, "Should not create duplicate draft records in MongoDB on repeated ingestion");

        // Verify that draft audit history recorded state reset
        StructuredPolicy reusedDraft = structuredPolicyRepository.findById(draftId1).orElseThrow();
        assertNotNull(reusedDraft.getAuditHistory());
        assertTrue(reusedDraft.getAuditHistory().stream()
                .anyMatch(a -> "DRAFT_REINGESTED_RESET".equals(a.getAction())),
                "Audit log must record that draft state was reset on re-ingestion");

        // Published policy must STILL be active and unchanged
        StructuredPolicy publishedAfter = structuredPolicyRepository.findById(publishedId).orElseThrow();
        assertTrue(publishedAfter.isActive());
        assertEquals(publishedCode, publishedAfter.getPolicyCode());
    }

    @Test
    void testReingestionDoesNotOverwriteOrUnpublishActiveDraft() {
        // Find existing published policy in catalog
        List<StructuredPolicy> catalog = recommendationService.getCatalog();
        StructuredPolicy publishedStar = catalog.stream()
                .filter(p -> "STAR_COMPREHENSIVE".equals(p.getPolicyCode()))
                .findFirst()
                .orElseThrow();
        String publishedCode = publishedStar.getPolicyCode();
        String revision = publishedStar.getUinRevision() != null ? publishedStar.getUinRevision() : "V012022";
        String activeDraftCode = publishedCode + "_DRAFT_" + revision;

        // Create an existing draft with that draftCode that was ALREADY approved and activated
        StructuredPolicy activeDraft = StructuredPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyCode(activeDraftCode)
                .policyName(publishedStar.getPolicyName())
                .provider(publishedStar.getProvider())
                .irdaUin(publishedStar.getIrdaUin() + "_DRAFT")
                .uinRevision(revision)
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .active(true) // already reviewed and active!
                .build();
        mockMongoDb.put(activeDraft.getId(), activeDraft);

        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .policyName(publishedStar.getPolicyName())
                .provider(publishedStar.getProvider())
                .irdaUin(publishedStar.getIrdaUin())
                .uinRevision(revision)
                .rawPolicyText("Product Name: " + publishedStar.getPolicyName() + "\nIRDAI UIN: " + publishedStar.getIrdaUin() + "\nRevision: " + revision + "\nRoom rent: No capping.")
                .sourceType("INSURER_PORTAL")
                .documentUrl("https://starhealth.in/policy.pdf")
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        // It must NOT overwrite or unpublish activeDraft
        StructuredPolicy preservedActiveDraft = structuredPolicyRepository.findById(activeDraft.getId()).orElseThrow();
        assertTrue(preservedActiveDraft.isActive(), "Active reviewed draft must not be silently deactivated or unpublished");
        assertEquals("VERIFIED_OFFICIAL_CATALOG", preservedActiveDraft.getVerificationStatus());

        // Instead, the new revision must be staged under a distinct stable branch
        assertNotEquals(activeDraft.getId(), result.getPolicyId(), "Must not reuse ID of active/reviewed draft");
        assertEquals(activeDraftCode + "_REV", result.getPolicyCode(),
                "New ingestion must branch under a stable revision code");

        // Second ingestion: must reuse the pending branch entity ID and not create another pending draft
        PolicyDiscoveryDto.PolicyIngestionResultDto result2 = discoveryPipelineService.discoverAndIngest(req);
        assertEquals(result.getPolicyId(), result2.getPolicyId(), "Repeated ingestion must reuse existing inactive branch ID");
        assertEquals(activeDraftCode + "_REV", result2.getPolicyCode());

        // Check that only ONE branch entity exists in MongoDB for this branch code
        long branchCount = mockMongoDb.values().stream()
                .filter(p -> (activeDraftCode + "_REV").equals(p.getPolicyCode()))
                .count();
        assertEquals(1, branchCount, "Should not create duplicate branch records in MongoDB on retry");
    }

    @Test
    void testPublishingNewRevisionAutomaticallySupersedesOldRevision() {
        // Step 1: Ensure initial revision V01 is active in MongoDB with verified contractual terms
        StructuredPolicy v01 = StructuredPolicy.builder()
                .id("star-v01")
                .policyCode("STAR_COMPREHENSIVE")
                .policyName("Star Comprehensive Insurance Policy")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .irdaUin("SHAHLIP21163V012021")
                .uinRevision("V012021")
                .active(true)
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .verifiedBy("Dr. Aditi Verma")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .roomRentLimit("Single Private AC Room without cap")
                        .build())
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(1000000L)
                        .formattedSumInsured("₹10 Lakh")
                        .premiumAge18To35(15000)
                        .build()))
                .build();
        mockMongoDb.put(v01.getId(), v01);
        recommendationService.refreshCatalog();

        assertTrue(recommendationService.getCatalog().stream().anyMatch(p -> "star-v01".equals(p.getId())));

        // Step 2: New revision V02 is drafted and approved
        StructuredPolicy v02Draft = StructuredPolicy.builder()
                .id("star-v02")
                .policyCode("STAR_COMPREHENSIVE_DRAFT_V022024")
                .policyName("Star Comprehensive Insurance Policy")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .irdaUin("SHAHLIP21163V012021")
                .uinRevision("V022024")
                .active(false)
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(24) // improved
                        .roomRentLimit("Single Private AC Room without cap")
                        .build())
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(1000000L)
                        .formattedSumInsured("₹10 Lakh")
                        .premiumAge18To35(16000)
                        .build()))
                .build();
        mockMongoDb.put(v02Draft.getId(), v02Draft);

        // Reviewer approves V02
        PolicyDiscoveryDto.PolicyVerificationRequest verifyReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Dr. Aditi Verma")
                .reviewerNotes("Approved revised PED terms under IRDAI 2024 regulations.")
                .build();

        StructuredPolicy publishedV02 = publicationService.verifyAndPublish(v02Draft.getId(), verifyReq);

        // Assertions:
        // 1. V02 is now active
        assertTrue(publishedV02.isActive());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", publishedV02.getVerificationStatus());

        // 2. V01 was automatically superseded
        StructuredPolicy supersededV01 = mockMongoDb.get("star-v01");
        assertNotNull(supersededV01);
        assertFalse(supersededV01.isActive(), "Prior revision must be deactivated upon new revision publication");
        assertEquals("SUPERSEDED", supersededV01.getVerificationStatus());
        assertTrue(supersededV01.getAuditNotes().contains("Superseded by newly approved revision"));

        // 3. Live recommendation catalog now contains V02, NOT V01
        List<StructuredPolicy> freshCatalog = recommendationService.getCatalog();
        assertTrue(freshCatalog.stream().anyMatch(p -> "star-v02".equals(p.getId())));
        assertFalse(freshCatalog.stream().anyMatch(p -> "star-v01".equals(p.getId())));
    }

    @Test
    void testRollbackRevisionRestoresSupersededRevision() {
        // Step 1: V01 was superseded by V02
        StructuredPolicy v01 = StructuredPolicy.builder()
                .id("star-v01-old")
                .policyCode("STAR_COMPREHENSIVE")
                .policyName("Star Comprehensive Insurance Policy")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .irdaUin("SHAHLIP21163V012021")
                .uinRevision("V012021")
                .active(false)
                .verifiedBy("Dr. Aditi Verma")
                .verificationStatus("SUPERSEDED")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .roomRentLimit("Single Private AC Room without cap")
                        .build())
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(1000000L)
                        .formattedSumInsured("₹10 Lakh")
                        .premiumAge18To35(15000)
                        .build()))
                .build();
        mockMongoDb.put(v01.getId(), v01);

        StructuredPolicy v02 = StructuredPolicy.builder()
                .id("star-v02-active")
                .policyCode("STAR_COMPREHENSIVE_DRAFT_V022024")
                .policyName("Star Comprehensive Insurance Policy")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .irdaUin("SHAHLIP21163V012021")
                .uinRevision("V022024")
                .active(true)
                .verifiedBy("Dr. Aditi Verma")
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(24)
                        .roomRentLimit("Single Private AC Room without cap")
                        .build())
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder()
                        .sumInsured(1000000L)
                        .formattedSumInsured("₹10 Lakh")
                        .premiumAge18To35(16000)
                        .build()))
                .build();
        mockMongoDb.put(v02.getId(), v02);
        recommendationService.refreshCatalog();

        // Step 2: Reviewer rolls back V02
        StructuredPolicy restored = publicationService.rollbackRevision(
                "star-v02-active",
                "Senior Reviewer Vikram",
                "Rollback due to actuarial premium calibration mismatch in V02"
        );

        // Assertions:
        // 1. V02 deactivated
        StructuredPolicy deactivatedV02 = mockMongoDb.get("star-v02-active");
        assertFalse(deactivatedV02.isActive());
        assertEquals("ROLLBACK_DEACTIVATED", deactivatedV02.getVerificationStatus());

        // 2. V01 restored back to active
        StructuredPolicy restoredV01 = mockMongoDb.get("star-v01-old");
        assertTrue(restoredV01.isActive());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", restoredV01.getVerificationStatus());

        // 3. Live recommendation catalog contains restored V01, and excludes rolled-back V02
        List<StructuredPolicy> freshCatalog = recommendationService.getCatalog();
        assertTrue(freshCatalog.stream().anyMatch(p -> "star-v01-old".equals(p.getId())));
        assertFalse(freshCatalog.stream().anyMatch(p -> "star-v02-active".equals(p.getId())));
    }
}
