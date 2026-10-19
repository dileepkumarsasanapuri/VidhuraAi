package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Golden Policy Acceptance Test for Niva Bupa ReAssure 2.0 (UIN: NBHHLIP27054V032627).
 * Verifies end-to-end contractual correctness:
 * 1. Extraction from genuine policy wording document.
 * 2. Strict server-side publication gate (draft isolation).
 * 3. Reviewer verification and promotion to VERIFIED_OFFICIAL_CATALOG.
 * 4. Grounding of recommendation output in traceable, verbatim contractual clauses.
 * 5. Explicit data classification separating contractual facts from modeled estimates.
 * 6. Revision replacement and rollback restoring exact contractual evidence.
 */
public class GoldenPolicyRecommendationAcceptanceTest {

    private StructuredPolicyRepository policyRepository;
    private RecommendationProfileRepository profileRepository;
    private RecommendationResultRepository resultRepository;
    private CanonicalProductRegistry canonicalProductRegistry;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicyPublicationService publicationService;
    private RecommendationService recommendationService;

    private final Map<String, StructuredPolicy> simulatedDb = new ConcurrentHashMap<>();

    private static final String OFFICIAL_REASSURE_2_0_PDF_TEXT = """
            NIVA BUPA HEALTH INSURANCE COMPANY LIMITED
            (Formerly known as Max Bupa Health Insurance Company Limited)
            REASSURE 2.0 - POLICY WORDINGS
            IRDAI Registration UIN: NBHHLIP27054V032627
            Product Revision: V032627
            Date of Regulatory Filing: 19-Jun-2026
            
            SECTION 1: DEFINITIONS & ELIGIBILITY
            1.1 Proposer & Insured: Adults eligible between 18 years and 65 years.
            1.2 Dependent Children: 91 days to 25 years.
            
            SECTION 2: BENEFITS & COVERAGE CLAUSES
            Section 2.1: In-patient Hospitalization & Room Rent Limit
            We will indemnify medical expenses for in-patient hospitalisation incurred for Room Rent,
            boarding, nursing, and intensive care unit (ICU) charges.
            There is no room rent capping. Single Private AC Room or any higher room category is eligible
            without any proportionate deduction on associated medical and surgical expenses.
            
            Section 2.2: Pre and Post Hospitalization Medical Expenses
            Pre-hospitalization expenses covered for 60 days immediately prior to hospital admission.
            Post-hospitalization expenses covered for 180 days immediately following hospital discharge.
            
            Section 2.3: ReAssure Forever / Restoration Benefit
            Unlimited 100% restoration of Sum Insured for same or different illness throughout the policy year.
            Triggered immediately upon the first paid claim during the policy tenure.
            
            Section 2.4: Cumulative Bonus Booster (Lock the Clock)
            100% cumulative bonus per claim-free policy year up to maximum 500% of base sum insured.
            
            SECTION 3: WAITING PERIODS
            Section 3.1: 30-day initial waiting period for all illness admissions (accidental trauma excluded).
            Section 3.2: 24 months specific illness waiting period for specified surgical procedures.
            Section 3.3: Pre-Existing Disease (PED) Waiting Period
            Pre-existing diseases, conditions, and treatments (PED) are covered after 36 months of continuous coverage.
            
            SECTION 4: CO-PAYMENT & DEDUCTIBLES
            Section 4.1: Nil Co-payment. Zero copay across all age bands and network hospitals. No mandatory copay applies.
            
            SECTION 5: GENERAL PERMANENT EXCLUSIONS
            5.1 Investigation & evaluation only admissions without active medical treatment.
            5.2 Cosmetic or plastic surgery unless necessitated by accidental trauma.
            5.3 Non-prescription dietary supplements, vitamins, and minerals.
            5.4 Intentional self-inflicted injury or attempted suicide.
            5.5 Non-medical hospital consumables classified under IRDAI List 1.
            """;

    @BeforeEach
    void setUp() {
        simulatedDb.clear();

        policyRepository = Mockito.mock(StructuredPolicyRepository.class);
        profileRepository = Mockito.mock(RecommendationProfileRepository.class);
        resultRepository = Mockito.mock(RecommendationResultRepository.class);

        canonicalProductRegistry = new CanonicalProductRegistry();
        discoveryPipelineService = new PolicyDiscoveryPipelineService(policyRepository, canonicalProductRegistry);

        EligibilityEngine eligibilityEngine = new EligibilityEngine();
        RankingEngine rankingEngine = new RankingEngine();
        PolicyIngestionService ingestionService = new PolicyIngestionService(policyRepository);

        recommendationService = new RecommendationService(
                policyRepository, profileRepository, resultRepository,
                eligibilityEngine, rankingEngine, ingestionService, null);

        publicationService = new PolicyPublicationService(policyRepository, recommendationService, canonicalProductRegistry);

        // Mock repository persistence backed by simulatedDb map
        when(policyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("pol-" + UUID.randomUUID().toString().substring(0, 8));
            }
            simulatedDb.put(p.getId(), p);
            return p;
        });

        when(policyRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(simulatedDb.get(inv.getArgument(0))));

        when(policyRepository.findByPolicyCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return simulatedDb.values().stream()
                    .filter(p -> code.equals(p.getPolicyCode()))
                    .findFirst();
        });

        when(policyRepository.findByIrdaUin(anyString())).thenAnswer(inv -> {
            String uin = inv.getArgument(0);
            return simulatedDb.values().stream()
                    .filter(p -> uin.equalsIgnoreCase(p.getIrdaUin()))
                    .findFirst();
        });

        when(policyRepository.findByActiveTrue()).thenAnswer(inv ->
                simulatedDb.values().stream()
                        .filter(StructuredPolicy::isActive)
                        .filter(p -> "VERIFIED_OFFICIAL_CATALOG".equals(p.getVerificationStatus()))
                        .toList());

        when(policyRepository.findAll()).thenAnswer(inv -> new ArrayList<>(simulatedDb.values()));
        when(policyRepository.count()).thenAnswer(inv -> (long) simulatedDb.size());

        when(profileRepository.save(any(RecommendationProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(resultRepository.save(any(RecommendationResult.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("End-to-End Acceptance: ReAssure 2.0 golden policy discovery, publication, and citation traceability")
    void testGoldenPolicyEndToEndAcceptance() {
        // =========================================================================
        // STEP 1: Policy Discovery & Ingestion from Genuine Policy Wording
        // =========================================================================
        PolicyDiscoveryDto.PolicyDiscoveryRequest discoveryReq = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Niva_Bupa_ReAssure_2.0_Policy_Wordings.pdf")
                .documentUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/reassure-2.0-policy-wordings.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .policyName("ReAssure 2.0")
                .provider("Niva Bupa Health Insurance Company Limited")
                .irdaUin("NBHHLIP27054V032627")
                .uinRevision("V032627")
                .rawPolicyText(OFFICIAL_REASSURE_2_0_PDF_TEXT)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto ingestionResult = discoveryPipelineService.discoverAndIngest(discoveryReq);

        // Assert discovery metadata and fail-closed classification
        assertEquals("POLICY_WORDING", ingestionResult.getDocumentClassification(),
                "Genuine contractual text must be positively classified as POLICY_WORDING");
        assertEquals("NBHHLIP27054V032627", ingestionResult.getIrdaUin());
        assertEquals("V032627", ingestionResult.getUinRevision());
        assertEquals(100.0, ingestionResult.getContractConfidenceScore(),
                "All contractual pillars (room rent, PED, copay, restore) must yield 100% contract confidence");
        assertTrue(ingestionResult.getContractUncertainFields().isEmpty(),
                "Contractual uncertainty list must be empty for verified wording");

        // Assert Server-Side Publication Gate
        assertEquals("EXTRACTION_PENDING_REVIEW", ingestionResult.getVerificationStatus(),
                "Server-side gate must enforce status EXTRACTION_PENDING_REVIEW");
        assertFalse(ingestionResult.isReadyForPublication(),
                "Automated ingestion must never mark readyForPublication = true");

        StructuredPolicy stagedDraft = simulatedDb.get(ingestionResult.getPolicyId());
        assertNotNull(stagedDraft);
        assertFalse(stagedDraft.isActive(), "Staged policy must have active = false");

        // Verify draft isolation: recommendation service must return 0 active policies while draft is unapproved
        recommendationService.refreshCatalog();
        RecommendationRequestDto userReq = RecommendationRequestDto.builder()
                .primaryAge(32)
                .annualBudget(25000.0)
                .budgetType("PREFERRED")
                .cityOrPincode("Bengaluru")
                .insuranceType("INDIVIDUAL")
                .primaryPriority("LOWER_OUT_OF_POCKET")
                .build();

        RecommendationResponseDto unapprovedResponse = recommendationService.generateRecommendations("user-test-1", userReq);
        assertTrue(unapprovedResponse.getRankedPolicies().isEmpty(),
                "Draft policy must be completely isolated and excluded from live user recommendations");

        // =========================================================================
        // STEP 2: Compliance Reviewer Verification & Publication Gate
        // =========================================================================
        PolicyDiscoveryDto.PolicyVerificationRequest approvalRequest = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("ComplianceAuditor@vidhura.ai")
                .reviewerNotes("Verified against Niva Bupa official IRDAI public filing (NBHHLIP27054V032627). All 4 coverage pillars and exclusions match.")
                .build();

        StructuredPolicy publishedPolicy = publicationService.verifyAndPublish(stagedDraft.getId(), approvalRequest);

        assertEquals("VERIFIED_OFFICIAL_CATALOG", publishedPolicy.getVerificationStatus());
        assertTrue(publishedPolicy.isActive(), "Published policy must now have active = true");
        assertEquals("ComplianceAuditor@vidhura.ai", publishedPolicy.getVerifiedBy());
        assertEquals(100.0, publishedPolicy.getContractConfidenceScore());

        // Verify that all extracted evidence citations are upgraded to VERIFIED_CONTRACT_CLAUSE
        assertNotNull(publishedPolicy.getDocumentEvidence());
        assertFalse(publishedPolicy.getDocumentEvidence().isEmpty());
        for (StructuredPolicy.PolicyEvidenceItem evidenceItem : publishedPolicy.getDocumentEvidence()) {
            assertEquals("VERIFIED_CONTRACT_CLAUSE", evidenceItem.getEvidenceConfidence(),
                    "All evidence items must be promoted to VERIFIED_CONTRACT_CLAUSE upon reviewer publication");
            assertNotNull(evidenceItem.getVerbatimText(), "Verbatim text must not be null");
            assertFalse(evidenceItem.getVerbatimText().isBlank(), "Verbatim text must not be blank");
        }

        // =========================================================================
        // STEP 3: Live Recommendation Grounding & Traceability Validation
        // =========================================================================
        recommendationService.refreshCatalog();
        RecommendationResponseDto liveResponse = recommendationService.generateRecommendations("user-test-1", userReq);

        assertFalse(liveResponse.getRankedPolicies().isEmpty(), "Published policy must now produce live recommendations");
        RecommendationResponseDto.RankedPolicyCard topOption = liveResponse.getRankedPolicies().get(0);

        // 1. Identity agreement between source document and recommendation record
        assertEquals("ReAssure 2.0", topOption.getPolicyName());
        assertEquals("NBHHLIP27054V032627", topOption.getIrdaUin());
        assertTrue(topOption.getOverallMatchScore() >= 90.0, "ReAssure 2.0 must achieve high match score (>= 90%) for out-of-pocket priority");

        // 2. Clause-level citations mapping to source PDF
        List<StructuredPolicy.PolicyEvidenceItem> citations = topOption.getSupportingEvidence();
        assertFalse(citations.isEmpty(), "Recommendation option must include contractual supporting evidence");

        // (a) Room Rent verification
        StructuredPolicy.PolicyEvidenceItem roomRentCitation = citations.stream()
                .filter(c -> "ROOM_RENT".equalsIgnoreCase(c.getTag()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing ROOM_RENT citation"));
        assertTrue(roomRentCitation.getSectionReference().contains("Section 2.1"),
                "Section reference must contain Section 2.1: " + roomRentCitation.getSectionReference());
        assertTrue(roomRentCitation.getVerbatimText().toLowerCase().contains("room rent")
                || roomRentCitation.getVerbatimText().toLowerCase().contains("hospitalisation"),
                "Room rent verbatim quote must match contractual wording: " + roomRentCitation.getVerbatimText());

        // (b) Pre-Existing Disease (PED) Waiting Period verification
        StructuredPolicy.PolicyEvidenceItem pedCitation = citations.stream()
                .filter(c -> "PED".equalsIgnoreCase(c.getTag()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing PED citation"));
        assertTrue(pedCitation.getSectionReference().contains("3") || pedCitation.getSectionReference().contains("Section 3"),
                "Section reference must contain Section 3: " + pedCitation.getSectionReference());
        assertTrue(pedCitation.getVerbatimText().toLowerCase().contains("36 months"),
                "PED verbatim quote must verify 36 months waiting period: " + pedCitation.getVerbatimText());

        // (c) Co-payment verification
        StructuredPolicy.PolicyEvidenceItem copayCitation = citations.stream()
                .filter(c -> "COPAY".equalsIgnoreCase(c.getTag()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing COPAY citation"));
        assertTrue(copayCitation.getSectionReference().contains("4") || copayCitation.getSectionReference().contains("Section 4"),
                "Section reference must contain Section 4: " + copayCitation.getSectionReference());
        assertTrue(copayCitation.getVerbatimText().toLowerCase().contains("nil co-payment")
                || copayCitation.getVerbatimText().toLowerCase().contains("zero copay"),
                "Copay verbatim quote must verify Nil Co-payment: " + copayCitation.getVerbatimText());

        // (d) Restoration Benefit verification
        StructuredPolicy.PolicyEvidenceItem restoreCitation = citations.stream()
                .filter(c -> "RESTORATION".equalsIgnoreCase(c.getTag()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing RESTORATION citation"));
        assertTrue(restoreCitation.getSectionReference().contains("2.3") || restoreCitation.getSectionReference().contains("Section 2"),
                "Section reference must contain Section 2 / 2.3: " + restoreCitation.getSectionReference());
        assertTrue(restoreCitation.getVerbatimText().toLowerCase().contains("restoration"),
                "Restoration verbatim quote must verify sum insured restoration: " + restoreCitation.getVerbatimText());

        // 3. Transparent Data Classification Separation
        assertNotNull(topOption.getDataClassification(), "Must include explicit DataClassification payload");
        assertEquals("VERIFIED_OFFICIAL_CATALOG", topOption.getDataClassification().getContractualFactsStatus(),
                "Contractual facts status must be VERIFIED_OFFICIAL_CATALOG");
        assertEquals("MODELED_ACTUARIAL_ESTIMATE", topOption.getDataClassification().getPricingClassification(),
                "Pricing must be explicitly tagged as MODELED_ACTUARIAL_ESTIMATE");
        assertFalse(topOption.getDataClassification().isBindingQuote(),
                "Pricing estimate must explicitly state it is not a binding quote");
        assertEquals("HYPOTHETICAL_SCENARIO_ESTIMATE", topOption.getDataClassification().getBillSimulationClassification(),
                "Bill simulation must be tagged as HYPOTHETICAL_SCENARIO_ESTIMATE");
        assertEquals("DATED_REGULATORY_DISCLOSURE", topOption.getDataClassification().getRegulatoryMetricsClassification(),
                "Regulatory metrics must be tagged as DATED_REGULATORY_DISCLOSURE");

        // =========================================================================
        // STEP 4: Revision Replacement & Rollback Resilience
        // =========================================================================
        // Ingest new revision V042728 of ReAssure 2.0
        String revisedWordingText = OFFICIAL_REASSURE_2_0_PDF_TEXT.replace("V032627", "V042728");
        PolicyDiscoveryDto.PolicyDiscoveryRequest revisedReq = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Niva_Bupa_ReAssure_2.0_Policy_Wordings_v4.pdf")
                .documentUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/reassure-2.0-v4.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .policyName("ReAssure 2.0")
                .provider("Niva Bupa Health Insurance Company Limited")
                .irdaUin("NBHHLIP27054V042728")
                .uinRevision("V042728")
                .rawPolicyText(revisedWordingText)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto revisedIngestion = discoveryPipelineService.discoverAndIngest(revisedReq);
        assertEquals("EXTRACTION_PENDING_REVIEW", revisedIngestion.getVerificationStatus());

        // Ensure live recommendations STILL serve V032627 during draft staging
        recommendationService.refreshCatalog();
        RecommendationResponseDto v3LiveResponse = recommendationService.generateRecommendations("user-test-1", userReq);
        assertEquals("NBHHLIP27054V032627", v3LiveResponse.getRankedPolicies().get(0).getIrdaUin(),
                "Live recommendations must continue serving approved revision V032627 while V04 is in draft staging");

        // Reviewer publishes V042728
        PolicyDiscoveryDto.PolicyVerificationRequest approveV4Req = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("ChiefUnderwriter@vidhura.ai")
                .reviewerNotes("Approved annual IRDAI filing revision V042728.")
                .build();

        StructuredPolicy publishedV4 = publicationService.verifyAndPublish(revisedIngestion.getPolicyId(), approveV4Req);
        recommendationService.refreshCatalog();

        RecommendationResponseDto v4LiveResponse = recommendationService.generateRecommendations("user-test-1", userReq);
        assertEquals("NBHHLIP27054V042728", v4LiveResponse.getRankedPolicies().get(0).getIrdaUin(),
                "Live recommendations must now serve approved revision V042728");

        // Reviewer rolls back to V032627
        publicationService.rollbackRevision(publishedV4.getId(), "ComplianceAuditor@vidhura.ai", "Rollback to verified V03 filing");
        recommendationService.refreshCatalog();

        RecommendationResponseDto rolledBackResponse = recommendationService.generateRecommendations("user-test-1", userReq);
        assertEquals("NBHHLIP27054V032627", rolledBackResponse.getRankedPolicies().get(0).getIrdaUin(),
                "Rollback must immediately restore V032627 in live recommendations");
        assertTrue(rolledBackResponse.getRankedPolicies().get(0).getSupportingEvidence().get(0).getSectionReference().contains("Section 2.1"),
                "Rollback must restore exact contractual evidence citations");
    }
}
