package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.controller.PolicyCatalogController;
import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.dto.RecommendationResponseDto;
import com.vidhura.ai.recommendation.engine.EligibilityEngine;
import com.vidhura.ai.recommendation.engine.RankingEngine;
import com.vidhura.ai.recommendation.model.RecommendationResult;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.RecommendationProfileRepository;
import com.vidhura.ai.recommendation.repository.RecommendationResultRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import com.vidhura.ai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Adversarial Verification Suite.
 *
 * Targets edge cases, adversarial inputs, real database behavior,
 * multi-instance consistency boundaries, and security constraints:
 *
 * P0 Checks:
 * 1. Publish with 100% confidence but no human approval -> Rejected
 * 2. Submit mismatched or counterfeit UIN -> Identity verification fails
 * 3. Submit conflicting policy terms & source citations -> Publication blocked
 * 4. ROLE_USER JWT against administrative routes -> Access denied
 * 5. Deactivate policy during concurrent multi-instance requests -> Consistency boundary enforced
 *
 * P1 Checks:
 * 6. Flagged policy remains excluded after application restart
 * 7. MongoDB failure during catalog refresh -> Active cache preserved without data loss
 * 8. Missing pricing data with complete contractual evidence -> Confidence scores remain independent
 * 9. Revoke reviewer authorization while JWT remains valid -> Immediate access denial
 * 10. Process same PDF twice or concurrently -> Idempotent upsert without duplicate records
 *
 * Architectural Details:
 * B. Product-specific PED waiting period verification
 * C. Audit log captures version provenance, reviewer identity, and changes
 * D. Historical recommendations preserve policy version and scoring version
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyAdversarialVerificationTest {

    @Mock
    private StructuredPolicyRepository policyRepository;

    @Mock
    private RecommendationProfileRepository profileRepository;

    @Mock
    private RecommendationResultRepository resultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.vidhura.ai.recommendation.repository.CatalogMetadataRepository catalogMetadataRepository;

    private EligibilityEngine eligibilityEngine;
    private RankingEngine rankingEngine;
    private PolicyIngestionService seedIngestionService;
    private RecommendationService recommendationService;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicyPublicationService publicationService;
    private PolicyCatalogController catalogController;

    private final Map<String, StructuredPolicy> simulatedMongo = new ConcurrentHashMap<>();
    private final Map<String, com.vidhura.ai.recommendation.model.CatalogMetadata> simulatedMetadata = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        simulatedMongo.clear();
        simulatedMetadata.clear();

        eligibilityEngine = new EligibilityEngine();
        rankingEngine = new RankingEngine();
        seedIngestionService = new PolicyIngestionService(policyRepository);

        // Seed initial 7 policies into simulated Mongo
        for (StructuredPolicy p : seedIngestionService.getSeedCatalog()) {
            if (p.getId() == null) p.setId(UUID.randomUUID().toString());
            simulatedMongo.put(p.getId(), p);
        }

        when(policyRepository.findAll()).thenAnswer(inv -> new ArrayList<>(simulatedMongo.values()));
        when(policyRepository.findByActiveTrue()).thenAnswer(inv ->
                simulatedMongo.values().stream().filter(StructuredPolicy::isActive).toList());
        when(policyRepository.count()).thenAnswer(inv -> (long) simulatedMongo.size());
        when(policyRepository.countByActiveTrue()).thenAnswer(inv ->
                simulatedMongo.values().stream().filter(StructuredPolicy::isActive).count());
        when(policyRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(simulatedMongo.get(id));
        });
        when(policyRepository.findByPolicyCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return simulatedMongo.values().stream().filter(p -> code.equals(p.getPolicyCode())).findFirst();
        });
        when(policyRepository.findByIrdaUin(anyString())).thenAnswer(inv -> {
            String uin = inv.getArgument(0);
            return simulatedMongo.values().stream().filter(p -> uin.equalsIgnoreCase(p.getIrdaUin())).findFirst();
        });
        when(policyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID().toString());
            simulatedMongo.put(p.getId(), p);
            return p;
        });

        when(catalogMetadataRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(simulatedMetadata.get(id));
        });
        when(catalogMetadataRepository.save(any(com.vidhura.ai.recommendation.model.CatalogMetadata.class))).thenAnswer(inv -> {
            com.vidhura.ai.recommendation.model.CatalogMetadata m = inv.getArgument(0);
            simulatedMetadata.put(m.getId(), m);
            return m;
        });

        recommendationService = new RecommendationService(
                policyRepository, profileRepository, resultRepository,
                eligibilityEngine, rankingEngine, seedIngestionService,
                catalogMetadataRepository
        );

        discoveryPipelineService = new PolicyDiscoveryPipelineService(policyRepository);
        publicationService = new PolicyPublicationService(policyRepository, recommendationService);
        catalogController = new PolicyCatalogController(
                discoveryPipelineService, publicationService, recommendationService, policyRepository
        );

        recommendationService.refreshCatalog();
    }

    // =========================================================================
    // PRIORITY P0 ADVERSARIAL CHECKS
    // =========================================================================

    @Test
    @DisplayName("P0-1: Attempt to publish policy with 100% confidence without human approval is rejected")
    void testPublishWithoutHumanApprovalIsRejected() {
        // Create an unverified draft with high confidence
        StructuredPolicy draft = StructuredPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyCode("HDFC_ADVERSARIAL_TEST")
                .policyName("HDFC Adversarial Plan")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21147V012021")
                .uinRevision("V012021")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .contractConfidenceScore(100.0)
                .pricingConfidenceScore(100.0)
                .active(false)
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Single Private Room")
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder().sumInsured(1000000).build()))
                .build();
        simulatedMongo.put(draft.getId(), draft);

        // 1. Direct Publication Gate Check: publication without reviewerName must throw IllegalArgumentException
        PolicyDiscoveryDto.PolicyVerificationRequest unapprovedRequest = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName(null) // Adversarial: Missing reviewer
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                publicationService.verifyAndPublish(draft.getId(), unapprovedRequest));
        assertTrue(ex.getMessage().contains("reviewerName"), "Error must state that reviewerName is strictly required");

        // 2. Catalog Gate Check: Even with 100% confidence, draft can NEVER enter active recommendations
        recommendationService.refreshCatalog();
        boolean foundInRecommendations = recommendationService.loadCatalog().stream()
                .anyMatch(p -> "HDFC_ADVERSARIAL_TEST".equals(p.getPolicyCode()));
        assertFalse(foundInRecommendations, "Unapproved draft must never be returned in recommendation catalog");
    }

    @Test
    @DisplayName("P0-2: Submit mismatched or counterfeit UIN -> Official identity verification fails")
    void testSubmitMismatchedUinFailsIdentityVerification() {
        // Document claims to be Bajaj Allianz, but has HDFC ERGO UIN
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Bajaj_Health_Guard.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .provider("Bajaj Allianz General Insurance")
                .irdaUin("HDFHLIP21147V012021") // Adversarial mismatch: HDF prefix on Bajaj provider
                .rawPolicyText("Bajaj Allianz Health Guard Policy. Section 4.1 PED waiting period 36 months. No room rent limit.")
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        // Verify mismatch is flagged
        assertTrue(result.getContractUncertainFields().contains("IRDAI_INSURER_MISMATCH"),
                "Ingestion must flag IRDAI_INSURER_MISMATCH");

        // Attempting to publish without reconciling UIN or provider must fail
        PolicyDiscoveryDto.PolicyVerificationRequest verifyReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Compliance Officer")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish(result.getPolicyId(), verifyReq));
        assertTrue(ex.getMessage().contains("IRDAI Insurer UIN mismatch"),
                "Publication must be blocked on insurer identity mismatch");
    }

    @Test
    @DisplayName("P0-3: Submit conflicting policy terms and source citations -> Publication blocked")
    void testConflictingTermsAndCitationsBlocksPublication() {
        // Text states room rent capped at 1%, but extracted terms specify uncapped
        StructuredPolicy draft = StructuredPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyCode("CONTRADICTION_TEST_PLAN")
                .policyName("Contradiction Test Plan")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21147V012021")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Single Private Room")
                        .roomRentCapped(false) // Extracted as false
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(36)
                        .build())
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder().sumInsured(1000000).build()))
                .contractUncertainFields(new ArrayList<>(List.of("CONTRADICTORY_CONTRACT_TERMS")))
                .build();
        simulatedMongo.put(draft.getId(), draft);

        PolicyDiscoveryDto.PolicyVerificationRequest verifyReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor Sarah")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish(draft.getId(), verifyReq));
        assertTrue(ex.getMessage().contains("reconciliation of contradictory policy terms"),
                "Must block publication until contradictory terms are reconciled");
    }

    @Test
    @DisplayName("P0-4: ROLE_USER JWT against every administrative route -> Access denied")
    void testRoleUserDeniedOnAllAdministrativeRoutes() {
        User regularUser = User.builder()
                .id("regular-user-id")
                .email("user@vidhura.ai")
                .role("ROLE_USER") // Adversarial: Standard consumer role
                .build();

        // 1. POST /api/policies/discover
        PolicyDiscoveryDto.PolicyDiscoveryRequest discReq = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("test.pdf")
                .build();
        assertThrows(AccessDeniedException.class, () ->
                catalogController.discoverPolicy(discReq, regularUser));

        // 2. POST /api/policies/{id}/verify
        PolicyDiscoveryDto.PolicyVerificationRequest verReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .build();
        assertThrows(AccessDeniedException.class, () ->
                catalogController.verifyPolicy("some-id", verReq, regularUser));

        // 3. POST /api/policies/{id}/flag
        assertThrows(AccessDeniedException.class, () ->
                catalogController.flagPolicy("some-id", "Outdated", "FLAGGED_OUTDATED", regularUser));

        // 4. POST /api/policies/cache/refresh
        assertThrows(AccessDeniedException.class, () ->
                catalogController.refreshCache(regularUser));
    }

    @Test
    @DisplayName("P0-5: Deactivate policy during concurrent requests across multiple instances -> Consistency boundary enforced")
    void testMultiInstanceConsistencyBoundaryOnDeactivation() throws Exception {
        // Setup two simulated application instances sharing the same database
        RecommendationService instance1 = recommendationService;
        RecommendationService instance2 = new RecommendationService(
                policyRepository, profileRepository, resultRepository,
                eligibilityEngine, rankingEngine, seedIngestionService
        );
        instance2.setCacheTtlMs(50L); // 50ms bounded consistency boundary for test
        instance2.refreshCatalog();

        String targetPolicyCode = "HDFC_OPTIMA_SECURE";
        assertTrue(instance2.loadCatalog().stream().anyMatch(p -> targetPolicyCode.equals(p.getPolicyCode())));

        // Concurrently run 50 requests across 10 threads on instance 2 while instance 1 deactivates the policy
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean deactivationCompleted = new AtomicBoolean(false);
        AtomicInteger postDeactivationViolationCount = new AtomicInteger(0);

        Callable<Void> recommendationTask = () -> {
            startLatch.await();
            RecommendationRequestDto req = RecommendationRequestDto.builder()
                    .primaryAge(32)
                    .insuranceType("INDIVIDUAL")
                    .annualBudget(25000.0)
                    .build();

            // Check if request occurred after deactivation AND after consistency boundary
            boolean wasDeactivated = deactivationCompleted.get();
            RecommendationResponseDto resp = instance2.generateRecommendations("user-concurrent", req);
            if (wasDeactivated) {
                boolean containsDeactivated = resp.getRankedPolicies().stream()
                        .anyMatch(c -> targetPolicyCode.equals(c.getPolicyCode()));
                if (containsDeactivated) {
                    postDeactivationViolationCount.incrementAndGet();
                }
            }
            return null;
        };

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(executor.submit(recommendationTask));
        }

        // Release threads
        startLatch.countDown();

        // Instance 1 deactivates the policy in MongoDB
        StructuredPolicy toDeactivate = simulatedMongo.values().stream()
                .filter(p -> targetPolicyCode.equals(p.getPolicyCode()))
                .findFirst().orElseThrow();
        toDeactivate.setActive(false);
        toDeactivate.setVerificationStatus("FLAGGED_OUTDATED");
        simulatedMongo.put(toDeactivate.getId(), toDeactivate);

        // Instance 2 receives cache refresh / consistency boundary expiration
        Thread.sleep(60); // exceed 50ms TTL
        instance2.refreshCatalog();
        deactivationCompleted.set(true);

        for (Future<Void> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Verify that after the consistency boundary, no recommendation uses the deactivated policy
        RecommendationRequestDto finalReq = RecommendationRequestDto.builder()
                .primaryAge(32)
                .insuranceType("INDIVIDUAL")
                .annualBudget(25000.0)
                .build();
        RecommendationResponseDto finalResp = instance2.generateRecommendations("user-post-consistency", finalReq);
        boolean presentInFinal = finalResp.getRankedPolicies().stream()
                .anyMatch(c -> targetPolicyCode.equals(c.getPolicyCode()));

        assertFalse(presentInFinal, "Deactivated policy must not appear in recommendations after consistency boundary");
        assertEquals(0, postDeactivationViolationCount.get(), "Zero post-consistency requests should return deactivated policy");
    }

    // =========================================================================
    // PRIORITY P1 ADVERSARIAL CHECKS
    // =========================================================================

    @Test
    @DisplayName("P1-6: Flagged policy remains excluded after application restart")
    void testFlaggedPolicyRemainsExcludedAfterRestart() {
        String targetCode = "ICICI_ELEVATE";
        StructuredPolicy policy = simulatedMongo.values().stream()
                .filter(p -> targetCode.equals(p.getPolicyCode()))
                .findFirst().orElseThrow();

        // Flag as outdated
        publicationService.flagPolicy(policy.getId(), "Outdated rates for FY2024", "FLAGGED_OUTDATED");

        // Simulate complete application restart by instantiating fresh RecommendationService
        RecommendationService rebootedInstance = new RecommendationService(
                policyRepository, profileRepository, resultRepository,
                eligibilityEngine, rankingEngine, seedIngestionService
        );

        List<StructuredPolicy> rebootedCatalog = rebootedInstance.loadCatalog();
        boolean exists = rebootedCatalog.stream().anyMatch(p -> targetCode.equals(p.getPolicyCode()));
        assertFalse(exists, "Flagged policy must remain excluded from catalog upon restart");
    }

    @Test
    @DisplayName("P1-7: MongoDB failure during catalog refresh preserves valid cache without data loss")
    void testMongoFailureDuringRefreshPreservesValidCache() {
        // Ensure initial catalog has 7 policies
        assertEquals(7, recommendationService.loadCatalog().size());

        // Simulate MongoDB throwing an exception during refresh
        when(policyRepository.findByActiveTrue()).thenThrow(new RuntimeException("MongoDB Connection Refused"));

        // Trigger refresh
        recommendationService.refreshCatalog();

        // Cache must still retain the 7 valid policies and not be wiped or emptied
        List<StructuredPolicy> activeAfterFailure = recommendationService.loadCatalog();
        assertNotNull(activeAfterFailure);
        assertEquals(7, activeAfterFailure.size(), "Cache must preserve active catalog snapshot on DB failure");
    }

    @Test
    @DisplayName("P1-8: Missing pricing data with complete contractual evidence keeps scores independent")
    void testDecoupledConfidenceScoresWhenPricingMissing() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("HDFC_Pure_Wording.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .provider("HDFC ERGO General Insurance")
                .irdaUin("HDFHLIP21147V012021")
                .rawPolicyText("HDFC ERGO Optima Plan. Section 4.1: Pre-existing diseases covered after 24 months. " +
                        "Section 4.2: Single private AC room with no room rent capping. Section 4.3: 0% copay on all claims.")
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);

        // Contractual clauses are fully verified (100%), but prospectus table (Form NL-25) is missing
        assertEquals(100.0, result.getContractConfidenceScore(), 0.1, "Contract confidence must be 100%");
        assertTrue(result.getPricingConfidenceScore() < 80.0, "Pricing confidence must reflect unverified rate tables");
        assertEquals("MODELED_ESTIMATE_ONLY", result.getPricingStatus());

        // Verification can approve contractual facts while retaining modeled estimate status
        PolicyDiscoveryDto.PolicyVerificationRequest verifyReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor James")
                .build();

        StructuredPolicy published = publicationService.verifyAndPublish(result.getPolicyId(), verifyReq);
        assertEquals("VERIFIED_OFFICIAL_CATALOG", published.getVerificationStatus());
        assertEquals("MODELED_ESTIMATE_ONLY", published.getPricingStatus());
    }

    @Test
    @DisplayName("P1-9: Revoke reviewer authorization while JWT remains valid -> Immediate access denial")
    void testRevokeReviewerAuthorizationImmediatelyDeniesAccess() {
        User reviewer = User.builder()
                .id("revoked-reviewer-id")
                .email("reviewer@vidhura.ai")
                .name("Reviewer Bob")
                .role("ROLE_REVIEWER")
                .build();

        // 1. Authorized call succeeds
        assertDoesNotThrow(() -> catalogController.refreshCache(reviewer));

        // 2. Admin revokes reviewer privileges in database/session
        reviewer.setRole("ROLE_USER"); // Downgraded to regular consumer

        // 3. Subsequent request with the same user instance immediately fails
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                catalogController.refreshCache(reviewer));
        assertTrue(ex.getMessage().contains("Access Denied"));
    }

    @Test
    @DisplayName("P1-10: Process same PDF twice or concurrently -> Idempotent upsert without duplicates")
    void testProcessSamePdfTwiceIsIdempotent() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Star_Comprehensive_Filing.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .provider("Star Health and Allied Insurance")
                .irdaUin("SHAHLIP21163V012021")
                .rawPolicyText("Star Health Comprehensive Insurance. Section 4.1: PED waiting period 36 months. No room rent limit.")
                .build();

        // Ingest once
        PolicyDiscoveryDto.PolicyIngestionResultDto first = discoveryPipelineService.discoverAndIngest(req);
        long countAfterFirst = simulatedMongo.values().stream()
                .filter(p -> "SHAHLIP21163V012021".equals(p.getIrdaUin()))
                .count();
        assertEquals(1, countAfterFirst);

        // Ingest second time
        PolicyDiscoveryDto.PolicyIngestionResultDto second = discoveryPipelineService.discoverAndIngest(req);
        long countAfterSecond = simulatedMongo.values().stream()
                .filter(p -> "SHAHLIP21163V012021".equals(p.getIrdaUin()))
                .count();

        assertEquals(1, countAfterSecond, "Processing identical filing twice must not create duplicate entities");
        assertEquals(first.getPolicyId(), second.getPolicyId(), "Entity ID must be preserved upon idempotent update");
    }

    // =========================================================================
    // ARCHITECTURAL SAFEGUARD CHECKS (DETAILS B, C, D)
    // =========================================================================

    @Test
    @DisplayName("Detail B: Product-specific unusual PED waiting period requires explicit reviewer confirmation")
    void testProductSpecificPedRequiresExplicitReviewerConfirmation() {
        StructuredPolicy unusualPedPolicy = StructuredPolicy.builder()
                .id(UUID.randomUUID().toString())
                .policyCode("SENIOR_DAY_ONE_PED")
                .policyName("Senior Day-One Cover")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21147V012021")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .active(false)
                .contractTerms(StructuredPolicy.ContractTerms.builder()
                        .roomRentLimit("Single Private Room")
                        .roomRentCapped(false)
                        .copayPercentage(0.0)
                        .waitingPeriodPedMonths(0) // Unusual: 0 months (Day-1 PED cover)
                        .build())
                .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder().sumInsured(1000000).build()))
                .build();
        simulatedMongo.put(unusualPedPolicy.getId(), unusualPedPolicy);

        // 1. Without explicit reviewer note confirming PED, publication is blocked
        PolicyDiscoveryDto.PolicyVerificationRequest unconfirmed = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Reviewer Jane")
                .reviewerNotes("Looks good")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish(unusualPedPolicy.getId(), unconfirmed));
        assertTrue(ex.getMessage().contains("Unusual PED waiting period"), "Must require explicit PED confirmation");

        // 2. With explicit reviewer note confirming the unusual clause, publication succeeds
        PolicyDiscoveryDto.PolicyVerificationRequest confirmed = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Reviewer Jane")
                .reviewerNotes("Confirmed day-one PED waiver clause from policy schedule rider")
                .build();

        StructuredPolicy published = publicationService.verifyAndPublish(unusualPedPolicy.getId(), confirmed);
        assertEquals("VERIFIED_OFFICIAL_CATALOG", published.getVerificationStatus());
        assertEquals(0, published.getContractTerms().getWaitingPeriodPedMonths());
    }

    @Test
    @DisplayName("Detail C: Audit log captures version provenance, reviewer identity, and state changes")
    void testAuditHistoryCapturesCompleteProvenance() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Audit_Trail_Filing.pdf")
                .sourceType("IRDAI_PUBLIC_PORTAL")
                .provider("HDFC ERGO General Insurance")
                .irdaUin("HDFHLIP21147V012021")
                .rawPolicyText("HDFC Plan. PED waiting period 36 months. Single private room.")
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto ingested = discoveryPipelineService.discoverAndIngest(req);

        // Approve and publish
        PolicyDiscoveryDto.PolicyVerificationRequest approveReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Dr. Anita Sharma")
                .reviewerNotes("Approved after cross-referencing IRDAI Registry.")
                .build();
        StructuredPolicy verified = publicationService.verifyAndPublish(ingested.getPolicyId(), approveReq);

        // Flag as outdated later
        StructuredPolicy flagged = publicationService.flagPolicy(verified.getId(), "Superseded by v2025 revision", "FLAGGED_OUTDATED");

        // Verify audit history
        List<StructuredPolicy.AuditLogEntry> history = flagged.getAuditHistory();
        assertNotNull(history);
        assertTrue(history.size() >= 3, "Audit history must record INGESTED, VERIFIED_PUBLISHED, and FLAGGED_OUTDATED");

        StructuredPolicy.AuditLogEntry publishEntry = history.stream()
                .filter(e -> "VERIFIED_PUBLISHED".equals(e.getAction()))
                .findFirst().orElseThrow();
        assertEquals("Dr. Anita Sharma", publishEntry.getPerformedBy());
        assertEquals("EXTRACTION_PENDING_REVIEW", publishEntry.getPreviousStatus());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", publishEntry.getNewStatus());
    }

    @Test
    @DisplayName("Detail D: Historical recommendation runs retain exact policy and scoring versions")
    void testHistoricalRecommendationsPreserveVersions() {
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(30)
                .insuranceType("INDIVIDUAL")
                .annualBudget(25000.0)
                .build();

        RecommendationResponseDto response = recommendationService.generateRecommendations("user-history-test", req);
        assertNotNull(response);
        assertFalse(response.getRankedPolicies().isEmpty());

        RecommendationResponseDto.RankedPolicyCard topCard = response.getRankedPolicies().get(0);
        assertNotNull(topCard.getPolicySnapshotVersion(), "Policy snapshot version must be recorded");
        assertNotNull(topCard.getScoringEngineVersion(), "Scoring engine version must be recorded");
        assertEquals("2.0-IRDAI-MULTI-POLICY", topCard.getScoringEngineVersion());
        assertNotNull(topCard.getEvaluationTimestamp(), "Evaluation timestamp must be recorded");
    }

    // =========================================================================
    // DEDICATED RELEASE CRITERIA VERIFICATION CHECKS
    // =========================================================================

    @Test
    @DisplayName("Issue 1: Global catalog version increment enforces immediate cross-instance invalidation without TTL delay")
    void testGlobalCatalogVersionIncrementForcesImmediateInvalidation() {
        // Initial state: catalog has 7 active policies
        List<StructuredPolicy> initial = recommendationService.loadCatalog();
        assertEquals(7, initial.size());
        long initialVersion = recommendationService.getLocalCatalogVersion();

        // Simulate another replica/instance deactivating a policy in MongoDB and incrementing global catalog version
        StructuredPolicy toDeactivate = simulatedMongo.values().iterator().next();
        toDeactivate.setActive(false);
        simulatedMongo.put(toDeactivate.getId(), toDeactivate);

        long newGlobalVersion = initialVersion + 5;
        simulatedMetadata.put(
                com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID,
                com.vidhura.ai.recommendation.model.CatalogMetadata.builder()
                        .id(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID)
                        .version(newGlobalVersion)
                        .activePoliciesCount(6)
                        .lastAction("DEACTIVATED: " + toDeactivate.getPolicyCode())
                        .build()
        );

        // Verify that isGlobalCatalogVersionNewer detects the version increment
        assertTrue(recommendationService.isGlobalCatalogVersionNewer());

        // Without waiting for the 60-second TTL to elapse, calling loadCatalog()
        // MUST immediately detect the version divergence, invalidate local cache, and reload from DB
        List<StructuredPolicy> refreshed = recommendationService.loadCatalog();
        assertEquals(6, refreshed.size(), "Deactivated policy must be excluded immediately without waiting for 60s TTL");
        assertEquals(newGlobalVersion, recommendationService.getLocalCatalogVersion(), "Local version must be synchronized with global version");
        assertFalse(refreshed.stream().anyMatch(p -> p.getPolicyCode().equals(toDeactivate.getPolicyCode())));
    }

    @Test
    @DisplayName("Issue 2: Distinguishes intentional empty catalog from database failure and purges revoked policies")
    void testIntentionalEmptyCatalogClearsCacheUnlikeDatabaseFailure() {
        // 1. Ensure cache is warm with 7 policies
        List<StructuredPolicy> warm = recommendationService.loadCatalog();
        assertEquals(7, warm.size());

        // 2. Scenario A: Transient Database Failure (MongoDB throws exception)
        when(policyRepository.findByActiveTrue()).thenThrow(new RuntimeException("MongoDB connection timeout / pool exhausted"));

        // Cache refresh on DB failure MUST preserve existing active cache
        recommendationService.refreshCatalog();
        List<StructuredPolicy> preserved = recommendationService.loadCatalog();
        assertEquals(7, preserved.size(), "Transient database failure must preserve warm cache without data loss");

        // 3. Scenario B: Revocation during DB failure
        // An administrator revokes a specific compromised/outdated policy
        String revokedCode = warm.get(0).getPolicyCode();
        recommendationService.markPolicyRevoked(revokedCode);

        // Even though DB is STILL failing, the revoked policy MUST be purged immediately and not resurrected
        List<StructuredPolicy> purged = recommendationService.loadCatalog();
        assertEquals(6, purged.size(), "Revoked policy must be purged immediately even while database is failing");
        assertFalse(purged.stream().anyMatch(p -> p.getPolicyCode().equalsIgnoreCase(revokedCode)),
                "Database failure must NEVER silently resurrect an explicitly revoked policy");

        // 4. Scenario C: Legitimate Administrative Revocation of ALL policies
        // DB recovers, but an admin has legitimately deactivated all policies in the database
        simulatedMongo.values().forEach(p -> p.setActive(false));
        doReturn(new ArrayList<StructuredPolicy>()).when(policyRepository).findByActiveTrue();
        doReturn((long) simulatedMongo.size()).when(policyRepository).count(); // 7 policies exist in DB, but 0 active

        recommendationService.refreshCatalog();
        List<StructuredPolicy> emptied = recommendationService.loadCatalog();
        assertTrue(emptied.isEmpty(), "When all policies are intentionally deactivated in DB, catalog must be cleared to 0 (not silently retained)");
    }

    @Test
    @DisplayName("Issue 3: Canonical Product Registry detects mismatch between UIN and product name/document text, blocking publication")
    void testCanonicalProductMismatchBlocksPublication() {
        // Submit an adversarial draft:
        // Valid IRDAI UIN syntax for HDFC Optima Secure (HDFHLIP21147V012021),
        // BUT product name is filed as an unrelated scheme and document text does not cite Optima Secure
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Mismatched_Product_Filing.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .policyName("UNOFFICIAL HEALTH SAVER SCHEME")
                .irdaUin("HDFHLIP21147V012021")
                .rawPolicyText("""
                        HDFC ERGO General Insurance Co. Ltd.
                        UNOFFICIAL HEALTH SAVER SCHEME
                        IRDAI Registration UIN: HDFHLIP21147V012021
                        Section 3. Room Rent: Single Private Room.
                        Section 4. PED waiting period 36 months.
                        """)
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto ingested = discoveryPipelineService.discoverAndIngest(req);

        // Verification: Pipeline flags CANONICAL_PRODUCT_MISMATCH because UIN HDFHLIP21147V012021
        // canonically belongs to "Optima Secure", but the document text/title lacks canonical series keywords
        assertTrue(ingested.getContractUncertainFields().stream().anyMatch(f -> f.contains("CANONICAL_PRODUCT_MISMATCH")),
                "Canonical product mismatch must be flagged when UIN does not match product document series");

        // Attempting to publish without reconciling the mismatch MUST be rejected server-side
        PolicyDiscoveryDto.PolicyVerificationRequest unverifiedApprove = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor Sarah")
                .reviewerNotes("Attempting approval without reconciliation")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish(ingested.getPolicyId(), unverifiedApprove));
        assertTrue(ex.getMessage().contains("Canonical product registry mismatch") || ex.getMessage().contains("canonical"),
                "Publication must be blocked on canonical product mismatch");

        // With explicit reviewer reconciliation override of the policyName to the canonical name, publication succeeds
        PolicyDiscoveryDto.PolicyVerificationRequest reconciledApprove = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor Sarah")
                .reviewerNotes("Reconciled against IRDAI registry schedule; confirmed as Optima Secure v2021")
                .overrideFields(Map.of("policyName", "HDFC ERGO Optima Secure"))
                .build();

        StructuredPolicy published = publicationService.verifyAndPublish(ingested.getPolicyId(), reconciledApprove);
        assertEquals("VERIFIED_OFFICIAL_CATALOG", published.getVerificationStatus());
        assertTrue(published.isActive());
    }

    @Test
    @DisplayName("Issue 4: Historical recommendations persist comprehensive ReproducibilitySnapshot enabling deterministic audit replay")
    void testReproducibilitySnapshotEnablesDeterministicAuditReplay() {
        RecommendationRequestDto req = RecommendationRequestDto.builder()
                .primaryAge(32)
                .insuranceType("INDIVIDUAL")
                .cityOrPincode("Mumbai")
                .annualBudget(30000.0)
                .budgetType("PREFERRED")
                .primaryPriority("LOWER_OUT_OF_POCKET")
                .build();

        RecommendationResponseDto response = recommendationService.generateRecommendations("user-audit-reproduce", req);
        assertNotNull(response);
        assertFalse(response.getRankedPolicies().isEmpty());

        RecommendationResponseDto.RankedPolicyCard topCard = response.getRankedPolicies().get(0);
        RecommendationResult.ReproducibilitySnapshot snap = topCard.getReproducibilitySnapshot();
        assertNotNull(snap, "Reproducibility snapshot must be persisted for every recommendation");

        // 1. Contract Terms Snapshot
        assertNotNull(snap.getContractTermsSnapshot());
        assertNotNull(snap.getContractTermsSnapshot().getRoomRentLimit());
        assertTrue(snap.getContractTermsSnapshot().getWaitingPeriodPedMonths() >= 0);

        // 2. Pricing Model Snapshot
        assertNotNull(snap.getPricingModelSnapshot());
        assertTrue(snap.getPricingModelSnapshot().getSelectedSumInsured() > 0);
        assertTrue(snap.getPricingModelSnapshot().getCalculatedAnnualPremium() > 0);
        assertEquals("Zone 1", snap.getPricingModelSnapshot().getPricingZone());

        // 3. Insurer Metrics Snapshot
        assertNotNull(snap.getInsurerMetricsSnapshot());
        assertTrue(snap.getInsurerMetricsSnapshot().getClaimSettlementRatio() > 0);
        assertTrue(snap.getInsurerMetricsSnapshot().getCashlessHospitalsCount() > 0);

        // 4. Scoring Breakdown Snapshot
        assertNotNull(snap.getScoringBreakdown());
        assertEquals(topCard.getOverallMatchScore(), snap.getScoringBreakdown().getOverallScore(), 0.001);
        assertEquals(0.45, snap.getScoringBreakdown().getOopWeight(), 0.001); // LOWER_OUT_OF_POCKET priority weight
        assertEquals(0.15, snap.getScoringBreakdown().getCostWeight(), 0.001);

        // 5. User Requirements Snapshot (Zero Sensitive PII)
        assertNotNull(snap.getInputRequirementsSnapshot());
        assertEquals(32, snap.getInputRequirementsSnapshot().getPrimaryAge());
        assertEquals("INDIVIDUAL", snap.getInputRequirementsSnapshot().getInsuranceType());
        assertEquals("Zone 1", snap.getInputRequirementsSnapshot().getPricingZone());
        assertEquals(30000.0, snap.getInputRequirementsSnapshot().getAnnualBudget());

        // Deterministic Replay: Re-calculating with the snapshotted weights and sub-scores
        // must reproduce the exact composite score
        double recomputedComposite =
                snap.getScoringBreakdown().getCostScore() * snap.getScoringBreakdown().getCostWeight() +
                snap.getScoringBreakdown().getCoverageScore() * snap.getScoringBreakdown().getCoverageWeight() +
                snap.getScoringBreakdown().getOopScore() * snap.getScoringBreakdown().getOopWeight() +
                snap.getScoringBreakdown().getReliabilityScore() * snap.getScoringBreakdown().getReliabilityWeight();

        assertEquals(topCard.getOverallMatchScore(), Math.round(recomputedComposite * 10.0) / 10.0, 0.05,
                "Audit replay using persisted snapshot parameters must reproduce the exact historical score");
    }

    // =========================================================================
    // FINAL HARDENING VERIFICATION: ISSUES A, B, AND C
    // =========================================================================

    @Test
    @DisplayName("Issue A: An empty database after bootstrap does NOT restore seed policies; requires explicit administrative reseed")
    void testEmptyDatabaseAfterBootstrapDoesNotRestoreSeedPolicies() {
        // 1. Initial catalog is bootstrapped
        assertTrue(recommendationService.isBootstrapCompleted(), "Catalog must be marked as bootstrap-completed");
        List<StructuredPolicy> initialCatalog = recommendationService.loadCatalog();
        assertFalse(initialCatalog.isEmpty(), "Initial catalog should contain active policies");

        // 2. Simulate complete database wipe/clear (e.g. migration error, accidental drop, or intentional clear)
        simulatedMongo.clear();
        assertEquals(0, policyRepository.count(), "Database is now completely empty");

        // Force a catalog reload
        recommendationService.refreshCatalog();
        List<StructuredPolicy> postWipeCatalog = recommendationService.loadCatalog();

        // Critical Safeguard: Empty database must NOT automatically restore seed policies!
        assertTrue(postWipeCatalog.isEmpty(),
                "CRITICAL: Once initialized, an empty database must not silently restore seed policies!");

        // 3. Attempting administrative reseed without required credentials/justification must fail
        assertThrows(IllegalArgumentException.class, () ->
                recommendationService.adminReseedCatalog("", "Valid note"));
        assertThrows(IllegalArgumentException.class, () ->
                recommendationService.adminReseedCatalog("Admin Dave", ""));

        // 4. Legitimate intentional recovery via adminReseedCatalog with full audit trail
        int reseededCount = recommendationService.adminReseedCatalog(
                "Compliance Officer Elena",
                "Disaster recovery restoration after catastrophic MongoDB drop");

        assertTrue(reseededCount > 0, "Administrative reseed should restore seed catalog");
        List<StructuredPolicy> restoredCatalog = recommendationService.loadCatalog();
        assertEquals(reseededCount, restoredCatalog.size(), "Restored catalog should match reseeded count");

        // Verify audit note on reseeded policies
        StructuredPolicy restoredSample = restoredCatalog.get(0);
        assertTrue(restoredSample.getAuditNotes().contains("Restored via administrative reseed"),
                "Restored policies must record administrative reseed justification");
        assertEquals("Compliance Officer Elena", restoredSample.getVerifiedBy());
    }

    @Test
    @DisplayName("Issue B: Canonical identity override with invalid data fails re-validation and blocks publication")
    void testCanonicalOverrideWithInvalidDataFailsRevalidationAndBlocksPublication() {
        // Document has HDFC UIN but non-matching fake product name
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentName("Fake_Optima_Doc.pdf")
                .sourceType("POLICY_WORDING_PDF")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .irdaUin("HDFHLIP21147V012021")
                .policyName("Counterfeit Mystery Plan")
                .rawPolicyText("Counterfeit Plan document wording. Room rent capped at 1% of SI. PED waiting period 36 months.")
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(req);
        assertTrue(result.getContractUncertainFields().contains("CANONICAL_PRODUCT_MISMATCH"),
                "Ingestion must detect canonical product mismatch");

        // Attempt 1: Reviewer attempts override with STILL INVALID data (e.g. another fake name)
        PolicyDiscoveryDto.PolicyVerificationRequest invalidOverrideReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Auditor Bob")
                .reviewerNotes("Trying to override with fake name")
                .overrideFields(Map.of("policyName", "Still Invalid Fake Plan Name"))
                .build();

        // Merely supplying an override field must NOT bypass canonical validation!
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                publicationService.verifyAndPublish(result.getPolicyId(), invalidOverrideReq));
        assertTrue(ex.getMessage().contains("Canonical identity override failed re-validation")
                || ex.getMessage().contains("CANONICAL_PRODUCT_MISMATCH"),
                "Publication must be blocked when override fails canonical re-validation");

        // Attempt 2: Reviewer supplies valid canonical product name ("Optima Secure") with justification
        PolicyDiscoveryDto.PolicyVerificationRequest validOverrideReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Senior Auditor Sarah")
                .reviewerNotes("Reconciled against IRDAI health product schedule: official filing is Optima Secure v2021")
                .overrideFields(Map.of("policyName", "Optima Secure", "roomRentCapped", false))
                .build();

        StructuredPolicy published = publicationService.verifyAndPublish(result.getPolicyId(), validOverrideReq);
        assertNotNull(published);
        assertEquals("VERIFIED_OFFICIAL_CATALOG", published.getVerificationStatus());
        assertTrue(published.isActive());
        assertEquals("Optima Secure", published.getPolicyName());

        // Verify comprehensive audit log entry captures old vs new values, reviewer, and justification
        assertFalse(published.getAuditHistory().isEmpty());
        StructuredPolicy.AuditLogEntry audit = published.getAuditHistory().get(published.getAuditHistory().size() - 1);
        assertEquals("Senior Auditor Sarah", audit.getPerformedBy());
        assertNotNull(audit.getChanges().get("overridesDetail"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) audit.getChanges().get("overridesDetail");
        assertTrue(details.containsKey("policyName"));
        @SuppressWarnings("unchecked")
        Map<String, String> nameDiff = (Map<String, String>) details.get("policyName");
        assertEquals("Counterfeit Mystery Plan", nameDiff.get("old"));
        assertEquals("Optima Secure", nameDiff.get("new"));
    }

    @Test
    @DisplayName("Issue C: Concurrent catalog version increment during DB read is fenced and retried; metadata store outage freezes version")
    void testConcurrentCatalogVersionIncrementDuringDbReadIsFencedAndRetried() {
        // 1. Initial state: verify clean initial version
        long initialVer = recommendationService.getLocalCatalogVersion();

        // 2. Test Double-Checked Snapshot Fencing:
        // Simulate a scenario where during the database read, another instance deactivates a policy
        // and advances the global catalog version from 10 to 11.
        AtomicInteger readCount = new AtomicInteger(0);
        when(policyRepository.findByActiveTrue()).thenAnswer(inv -> {
            int count = readCount.incrementAndGet();
            if (count == 1) {
                // First read attempt: another instance advances the catalog version concurrently in MongoDB!
                recommendationService.incrementGlobalCatalogVersion("DEACTIVATE: POLICY_CONCURRENT", "Instance-A", 6);
            }
            return simulatedMongo.values().stream().filter(StructuredPolicy::isActive).toList();
        });

        // Trigger refresh
        recommendationService.refreshCatalog();

        // Verify that the fence detected the race, discarded the first tainted read, retried,
        // and synchronized cleanly with the new global version!
        assertTrue(readCount.get() >= 2, "Snapshot fencing must retry when catalog version advances during read");
        assertEquals(recommendationService.getGlobalCatalogVersion(), recommendationService.getLocalCatalogVersion(),
                "Local version must match the post-retry clean global version");

        // 3. Test Metadata Store Outage Behavior:
        // When catalogMetadataRepository throws an exception (unreachable MongoDB), the service must
        // freeze local version, preserve the in-memory cache, and not fail requests.
        long frozenVersion = recommendationService.getLocalCatalogVersion();
        when(catalogMetadataRepository.findById(anyString())).thenThrow(new RuntimeException("MongoDB network timeout / partition"));

        // Should return -1L sentinel for global version
        assertEquals(-1L, recommendationService.getGlobalCatalogVersion());

        // Refreshing during outage must preserve active cache and freeze local version
        assertDoesNotThrow(() -> recommendationService.refreshCatalog());
        assertEquals(frozenVersion, recommendationService.getLocalCatalogVersion(),
                "Catalog version must remain frozen during metadata store outage");
        assertFalse(recommendationService.loadCatalog().isEmpty(),
                "Active catalog cache must be preserved during metadata store outage");
    }
}
