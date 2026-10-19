package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.DiscoveredDocument;
import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
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

public class RecommendationLifecycleAndDeduplicationTest {

    private StructuredPolicyRepository policyRepository;
    private PolicySourceRepository sourceRepository;
    private DiscoveredDocumentRepository documentRepository;
    private RecommendationProfileRepository profileRepository;
    private RecommendationResultRepository resultRepository;

    private CanonicalProductRegistry canonicalProductRegistry;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicyPublicationService publicationService;
    private RecommendationService recommendationService;
    private PolicySourceMonitor sourceMonitor;
    private PolicyDocumentFetcher mockFetcher;

    private final Map<String, StructuredPolicy> databasePolicies = new ConcurrentHashMap<>();
    private final Map<String, PolicySource> databaseSources = new ConcurrentHashMap<>();
    private final Map<String, DiscoveredDocument> databaseDocuments = new ConcurrentHashMap<>();

    private static final String GENUINE_CONTRACT_WORDING_V1 = """
            NIVA BUPA HEALTH INSURANCE COMPANY LIMITED
            REASSURE 2.0 - CONTRACTUAL POLICY WORDINGS
            IRDAI Registration UIN: NBHHLIP27054V032627
            Revision: V032627
            
            SECTION 1: ELIGIBILITY & SCOPE
            Adult entry age: 18 to 65 years. Children eligible: 91 days to 25 years.
            Allowed family: Individual, Floater. Zones: Zone 1, Zone 2.
            
            SECTION 2: IN-PATIENT HOSPITALIZATION & ROOM RENT
            No room rent capping. Single Private AC Room or any higher room category is covered.
            Insurer covers actual room charges without proportionate deductions on doctor or surgeon charges.
            
            SECTION 3: SUM INSURED RESTORATION & BONUS
            Unlimited 100% restoration for same or different illness throughout the policy year.
            100% cumulative bonus per year up to maximum 500% of sum insured.
            
            SECTION 4: PRE-EXISTING DISEASE (PED) WAITING PERIOD
            Pre-existing conditions, illnesses, and diseases (PED) are covered after 36 months of continuous coverage.
            Initial waiting period: 30 days. Specific illness waiting period: 24 months.
            
            SECTION 5: CO-PAYMENT
            Zero mandatory co-payment across all age bands and network hospitals. Nil copay.
            
            SECTION 6: EXCLUSIONS
            Investigation & evaluation only admissions without active treatment;
            Cosmetic or plastic surgery unless necessitated by accidental trauma;
            Non-medical hospital consumables classified under IRDAI List 1.
            """;

    private static final String GENUINE_CONTRACT_WORDING_V2 = """
            NIVA BUPA HEALTH INSURANCE COMPANY LIMITED
            REASSURE 2.0 - REVISED POLICY WORDINGS
            IRDAI Registration UIN: NBHHLIP27054V032627
            Revision: V042627
            
            SECTION 1: ELIGIBILITY & SCOPE
            Adult entry age: 18 to 65 years. Children eligible: 91 days to 25 years.
            
            SECTION 2: IN-PATIENT HOSPITALIZATION & ROOM RENT
            No room rent capping. Single Private AC Room covered without proportionate deduction.
            
            SECTION 3: SUM INSURED RESTORATION
            Unlimited 100% restoration for same or different illness.
            
            SECTION 4: PRE-EXISTING DISEASE WAITING PERIOD
            Pre-existing diseases covered after 24 months under IRDAI accelerated coverage guidelines.
            Initial waiting period: 30 days. Specific illness: 24 months.
            
            SECTION 5: CO-PAYMENT
            Zero mandatory co-payment across network hospitals.
            
            SECTION 6: EXCLUSIONS
            Investigation only admissions; Cosmetic surgery; IRDAI List 1 consumables.
            """;

    @BeforeEach
    void setUp() {
        databasePolicies.clear();
        databaseSources.clear();
        databaseDocuments.clear();

        policyRepository = Mockito.mock(StructuredPolicyRepository.class);
        sourceRepository = Mockito.mock(PolicySourceRepository.class);
        documentRepository = Mockito.mock(DiscoveredDocumentRepository.class);
        profileRepository = Mockito.mock(RecommendationProfileRepository.class);
        resultRepository = Mockito.mock(RecommendationResultRepository.class);

        canonicalProductRegistry = new CanonicalProductRegistry();

        // Mock policyRepository
        when(policyRepository.save(any(StructuredPolicy.class))).thenAnswer(invocation -> {
            StructuredPolicy p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId("pol-" + UUID.randomUUID().toString().substring(0, 8));
            }
            databasePolicies.put(p.getId(), p);
            return p;
        });

        when(policyRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.ofNullable(databasePolicies.get(id));
        });

        when(policyRepository.findByPolicyCode(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return databasePolicies.values().stream()
                    .filter(p -> code.equalsIgnoreCase(p.getPolicyCode()))
                    .findFirst();
        });

        when(policyRepository.findByIrdaUin(anyString())).thenAnswer(invocation -> {
            String uin = invocation.getArgument(0);
            return databasePolicies.values().stream()
                    .filter(p -> uin.equalsIgnoreCase(p.getIrdaUin()))
                    .findFirst();
        });

        when(policyRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(databasePolicies.values()));
        when(policyRepository.findByActiveTrue()).thenAnswer(invocation ->
                databasePolicies.values().stream().filter(StructuredPolicy::isActive).toList());
        when(policyRepository.countByActiveTrue()).thenAnswer(invocation ->
                databasePolicies.values().stream().filter(StructuredPolicy::isActive).count());

        // Initialize recommendation engine
        EligibilityEngine eligibilityEngine = new EligibilityEngine();
        RankingEngine rankingEngine = new RankingEngine();
        PolicyIngestionService policyIngestionService = new PolicyIngestionService(policyRepository);

        recommendationService = new RecommendationService(
                policyRepository,
                profileRepository,
                resultRepository,
                eligibilityEngine,
                rankingEngine,
                policyIngestionService
        );

        publicationService = new PolicyPublicationService(policyRepository, recommendationService, canonicalProductRegistry);
        discoveryPipelineService = new PolicyDiscoveryPipelineService(policyRepository, canonicalProductRegistry, recommendationService);

        mockFetcher = Mockito.mock(PolicyDocumentFetcher.class);
        sourceMonitor = new PolicySourceMonitor(
                sourceRepository,
                documentRepository,
                mockFetcher,
                null,
                discoveryPipelineService,
                canonicalProductRegistry
        );
    }

    @Test
    @DisplayName("Test Full Lifecycle: Stage draft -> Verify approval -> Recommendation inclusion -> Revision update -> Rollback")
    void testFullRecommendationLifecycle() {
        // Step 1: Stage genuine contractual policy draft
        PolicyDiscoveryDto.PolicyDiscoveryRequest reqV1 = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Niva_Bupa_ReAssure_2.0_Wording.pdf")
                .documentUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/reassure-2.0.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .policyName("ReAssure 2.0")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27054V032627")
                .uinRevision("V032627")
                .rawPolicyText(GENUINE_CONTRACT_WORDING_V1)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto stagedV1 = discoveryPipelineService.discoverAndIngest(reqV1);
        assertNotNull(stagedV1.getPolicyId());
        assertEquals("EXTRACTION_PENDING_REVIEW", stagedV1.getVerificationStatus());
        assertEquals(100.0, stagedV1.getContractConfidenceScore());

        StructuredPolicy policyV1 = databasePolicies.get(stagedV1.getPolicyId());
        assertNotNull(policyV1);
        assertFalse(policyV1.isActive(), "Staged policy must be inactive before approval");

        // Confirm it does NOT appear in recommendations while inactive
        recommendationService.refreshCatalog();
        RecommendationRequestDto recReq = RecommendationRequestDto.builder()
                .primaryAge(32)
                .insuranceType("INDIVIDUAL")
                .members(new ArrayList<>())
                .cityOrPincode("Bengaluru")
                .annualBudget(25000.0)
                .budgetType("PREFERRED")
                .desiredSumInsured(1000000L)
                .primaryPriority("BALANCED")
                .preExistingConditions(new ArrayList<>())
                .build();

        RecommendationResponseDto initialRecs = recommendationService.generateRecommendations("user-1", recReq);
        boolean foundInInitial = initialRecs.getRankedPolicies().stream()
                .anyMatch(r -> r.getPolicyCode().contains("REASSURE"));
        assertFalse(foundInInitial, "Pending policy must NOT be recommended");

        // Step 2: Reviewer Approves Policy V1
        PolicyDiscoveryDto.PolicyVerificationRequest approveReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("MedicalUnderwriter@vidhura.ai")
                .reviewerNotes("Verified against official Niva Bupa contractual policy wordings. Zero copay, uncapped room rent, 36m PED confirmed.")
                .build();

        StructuredPolicy approvedV1 = publicationService.verifyAndPublish(policyV1.getId(), approveReq);
        assertTrue(approvedV1.isActive(), "Approved policy must be active");
        assertEquals("VERIFIED_OFFICIAL_CATALOG", approvedV1.getVerificationStatus());

        // Step 3: Confirm it is now active in recommendations with contract citations
        recommendationService.refreshCatalog();
        RecommendationResponseDto activeRecs = recommendationService.generateRecommendations("user-1", recReq);
        boolean foundInActive = activeRecs.getRankedPolicies().stream()
                .anyMatch(r -> r.getPolicyCode().contains("REASSURE") || r.getPolicyName().contains("ReAssure"));
        assertTrue(foundInActive, "Approved policy must appear in recommendations");

        // Step 4: Revision Ingestion (V2: 24-month PED)
        PolicyDiscoveryDto.PolicyDiscoveryRequest reqV2 = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Niva_Bupa_ReAssure_2.0_Wording_Revised.pdf")
                .documentUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/reassure-2.0-v4.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .policyName("ReAssure 2.0")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .irdaUin("NBHHLIP27054V032627")
                .uinRevision("V042627")
                .rawPolicyText(GENUINE_CONTRACT_WORDING_V2)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto stagedV2 = discoveryPipelineService.discoverAndIngest(reqV2);
        StructuredPolicy policyV2 = databasePolicies.get(stagedV2.getPolicyId());
        assertNotNull(policyV2);
        assertFalse(policyV2.isActive(), "New draft revision must remain inactive pending review");
        assertTrue(approvedV1.isActive(), "Existing published revision must remain active until V2 is explicitly approved");

        // Step 5: Approve Revision V2
        PolicyDiscoveryDto.PolicyVerificationRequest approveV2Req = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("ChiefUnderwriter@vidhura.ai")
                .reviewerNotes("Approved revised wording with 24-month PED reduction.")
                .build();

        StructuredPolicy approvedV2 = publicationService.verifyAndPublish(policyV2.getId(), approveV2Req);
        assertTrue(approvedV2.isActive(), "Approved revision V2 must be active");
        assertFalse(approvedV1.isActive(), "Prior revision V1 must be superseded and inactive");
        assertEquals("SUPERSEDED", approvedV1.getVerificationStatus(), "Prior revision status must be SUPERSEDED");

        // Step 6: Test Rollback
        StructuredPolicy rolledBack = publicationService.rollbackRevision(
                approvedV2.getId(),
                "LeadAuditor@vidhura.ai",
                "Rollback to V1 due to clarification needed on regulatory filing endorsement date."
        );

        assertNotNull(rolledBack);
        assertFalse(approvedV2.isActive(), "Deactivated revision must be inactive");
        assertEquals("ROLLBACK_DEACTIVATED", approvedV2.getVerificationStatus());
        assertTrue(approvedV1.isActive(), "Superseded revision V1 must be restored to active");
        assertEquals("VERIFIED_OFFICIAL_CATALOG", approvedV1.getVerificationStatus());

        // Step 7: Confirm recommendations now return restored V1
        recommendationService.refreshCatalog();
        RecommendationResponseDto rollbackRecs = recommendationService.generateRecommendations("user-1", recReq);
        assertTrue(rollbackRecs.getRankedPolicies().stream()
                .anyMatch(r -> r.getPolicyCode().contains("REASSURE") || r.getPolicyName().contains("ReAssure")),
                "Restored policy must continue to be recommended");
    }

    @Test
    @DisplayName("Test Repeat Crawl Deduplication: Confirms 0 duplicate drafts and identical checksum skipping")
    void testRepeatCrawlDeduplication() {
        byte[] pdfBytes = GENUINE_CONTRACT_WORDING_V1.getBytes();
        String sha256 = PolicyDocumentFetcher.computeSha256(pdfBytes);

        PolicySource source = PolicySource.builder()
                .id("src-nb-reassure")
                .sourceName("Niva Bupa ReAssure 2.0 Official PDF")
                .sourceUrl("https://www.nivabupa.com/content/dam/nivabupa/PDF/reassure-2.0.pdf")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .canonicalProductName("ReAssure 2.0")
                .irdaUin("NBHHLIP27054V032627")
                .expectedRevision("V032627")
                .documentType("POLICY_WORDING")
                .status("ACTIVE")
                .lastSha256Checksum(sha256) // Already fetched previously!
                .lastEtag("\"etag-12345\"")
                .lastModifiedHeader("Wed, 08 Oct 2026 10:00:00 GMT")
                .active(true)
                .build();

        databaseSources.put(source.getId(), source);
        when(sourceRepository.findById("src-nb-reassure")).thenReturn(Optional.of(source));

        // Mock fetcher to return HTTP 304 Not Modified when ETag matches
        when(mockFetcher.fetchDocument(source.getSourceUrl(), "\"etag-12345\"", "Wed, 08 Oct 2026 10:00:00 GMT"))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(304)
                        .etag("\"etag-12345\"")
                        .lastModifiedHeader("Wed, 08 Oct 2026 10:00:00 GMT")
                        .fetchedAt(Instant.now())
                        .build());

        // Mock fetcher to return HTTP 200 with identical SHA-256 if server doesn't support 304
        when(mockFetcher.fetchDocument(source.getSourceUrl(), null, null))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(pdfBytes)
                        .sha256Checksum(sha256)
                        .fileSizeBytes(pdfBytes.length)
                        .etag("\"etag-12345\"")
                        .fetchedAt(Instant.now())
                        .build());

        // 1. Conditional Poll with ETag match -> Returns SKIPPED_304_NOT_MODIFIED
        String outcome1 = sourceMonitor.pollSingleSource("src-nb-reassure", false);
        assertEquals("SKIPPED_304_NOT_MODIFIED", outcome1,
                "Repeat crawl must skip unchanged document with HTTP 304");

        // 2. Poll without conditional headers but identical SHA-256 -> Returns SKIPPED_IDENTICAL_CHECKSUM
        source.setLastEtag(null);
        source.setLastModifiedHeader(null);
        String outcome2 = sourceMonitor.pollSingleSource("src-nb-reassure", false);
        assertEquals("SKIPPED_IDENTICAL_CHECKSUM", outcome2,
                "Repeat crawl must skip identical document content via SHA-256 check");

        // Verify that NO new draft policies were added to the database
        assertEquals(0, databasePolicies.size(),
                "Deduplication must ensure zero duplicate policy records are created on repeat crawl");
    }
}
