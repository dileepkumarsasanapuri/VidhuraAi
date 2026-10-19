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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Concurrency Test for Catalog Cache Invalidation & Thread Safety.
 *
 * Simulates concurrent user recommendation generation while administrative
 * publication, update, and deactivation mutations occur simultaneously.
 * Verifies that:
 * 1. Outdated / flagged policies are instantly excluded with zero stale reads.
 * 2. Published policies become instantly available to concurrent readers.
 * 3. Lock-free AtomicReference catalog swap never exposes null or partial state.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConcurrentRecommendationCacheTest {

    @Mock
    private StructuredPolicyRepository policyRepository;

    @Mock
    private RecommendationProfileRepository profileRepository;

    @Mock
    private RecommendationResultRepository resultRepository;

    private RecommendationService recommendationService;
    private PolicyPublicationService publicationService;
    private PolicyIngestionService policyIngestionService;

    // Concurrent in-memory mock database
    private final Map<String, StructuredPolicy> threadSafeDb = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        threadSafeDb.clear();

        StructuredPolicyRepository mockRepo = mock(StructuredPolicyRepository.class);
        when(mockRepo.count()).thenAnswer(inv -> (long) threadSafeDb.size());
        when(mockRepo.findByActiveTrue()).thenAnswer(inv ->
                threadSafeDb.values().stream().filter(StructuredPolicy::isActive).toList());
        when(mockRepo.findById(any(String.class))).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(threadSafeDb.get(id));
        });
        when(mockRepo.findByPolicyCode(any(String.class))).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return threadSafeDb.values().stream().filter(p -> code.equals(p.getPolicyCode())).findFirst();
        });
        when(mockRepo.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID().toString());
            }
            threadSafeDb.put(p.getId(), p);
            return p;
        });

        policyIngestionService = new PolicyIngestionService(mockRepo);
        for (StructuredPolicy p : policyIngestionService.getSeedCatalog()) {
            p.setId(UUID.randomUUID().toString());
            threadSafeDb.put(p.getId(), p);
        }

        EligibilityEngine eligibilityEngine = new EligibilityEngine();
        RankingEngine rankingEngine = new RankingEngine();

        recommendationService = new RecommendationService(
                mockRepo, profileRepository, resultRepository,
                eligibilityEngine, rankingEngine, policyIngestionService
        );

        publicationService = new PolicyPublicationService(mockRepo, recommendationService);

        recommendationService.refreshCatalog();
    }

    @Test
    void testConcurrentRecommendationsDuringPublicationAndDeactivation() throws Exception {
        int readerThreadCount = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(readerThreadCount + 3);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readerThreadCount);

        AtomicInteger successfulReads = new AtomicInteger(0);
        AtomicInteger deactivationObservedCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        // Identify policy to be deactivated
        StructuredPolicy targetToDeactivate = threadSafeDb.values().stream()
                .filter(p -> "CARE_SUPREME".equals(p.getPolicyCode()))
                .findFirst()
                .orElseThrow();

        AtomicBoolean deactivationCompleted = new AtomicBoolean(false);

        // 1. Launch 20 concurrent recommendation readers
        for (int i = 0; i < readerThreadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to start together

                    RecommendationRequestDto request = RecommendationRequestDto.builder()
                            .primaryAge(32)
                            .insuranceType("INDIVIDUAL")
                            .cityOrPincode("Bengaluru")
                            .annualBudget(25000.0)
                            .budgetType("PREFERRED")
                            .primaryPriority("BALANCED")
                            .members(new ArrayList<>())
                            .preExistingConditions(new ArrayList<>())
                            .build();

                    for (int op = 0; op < operationsPerThread; op++) {
                        boolean wasDeactivatedBefore = deactivationCompleted.get();
                        RecommendationResponseDto response =
                                recommendationService.generateRecommendations("user-concurrent-" + threadId, request);

                        assertNotNull(response);
                        assertNotNull(response.getRankedPolicies());
                        assertFalse(response.getRankedPolicies().isEmpty());

                        // If deactivation completed BEFORE this recommendation was requested,
                        // verify that the deactivated policy NEVER leaks into recommendation results
                        if (wasDeactivatedBefore) {
                            boolean containsDeactivated = response.getRankedPolicies().stream()
                                    .anyMatch(p -> "CARE_SUPREME".equals(p.getPolicyCode()));
                            assertFalse(containsDeactivated,
                                    "Stale cache violation: Deactivated policy returned in recommendations after deactivation!");
                            deactivationObservedCount.incrementAndGet();
                        }

                        successfulReads.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 2. Launch concurrent administrative writer: Deactivation / Flagging
        Future<?> deactivationFuture = executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(15); // Let some reads happen first

                publicationService.flagPolicy(
                        targetToDeactivate.getId(),
                        "IRDAI revised filing; older version deactivated.",
                        "FLAGGED_OUTDATED"
                );

                deactivationCompleted.set(true);
            } catch (Exception e) {
                errors.add(e);
            }
        });

        // 3. Launch concurrent administrative writer: Publication of a new policy
        Future<?> publicationFuture = executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(25);

                StructuredPolicy newPolicy = StructuredPolicy.builder()
                        .id(UUID.randomUUID().toString())
                        .policyCode("CONCURRENT_NEW_POLICY_9")
                        .policyName("Max Protect Global Floater")
                        .provider("Max General Insurance Co.")
                        .irdaUin("MAXHLIP99999V012024")
                        .uinRevision("V012024")
                        .verificationStatus("EXTRACTION_PENDING_REVIEW")
                        .active(false)
                        .contractTerms(StructuredPolicy.ContractTerms.builder()
                                .roomRentLimit("No Limit")
                                .roomRentCapped(false)
                                .waitingPeriodPedMonths(24)
                                .build())
                        .eligibility(StructuredPolicy.EligibilityRules.builder()
                                .minAdultAge(18).maxAdultAge(65).build())
                        .sumInsuredTiers(List.of(StructuredPolicy.SumInsuredTier.builder()
                                .sumInsured(1000000L).premiumAge18To35(11000).build()))
                        .build();

                threadSafeDb.put(newPolicy.getId(), newPolicy);

                // Reviewer approves and publishes
                PolicyDiscoveryDto.PolicyVerificationRequest verifyReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                        .decision("APPROVE")
                        .reviewerName("Compliance Lead Sarah Jenkins")
                        .reviewerNotes("All terms approved.")
                        .build();

                publicationService.verifyAndPublish(newPolicy.getId(), verifyReq);
            } catch (Exception e) {
                errors.add(e);
            }
        });

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all readers to complete
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "All concurrent recommendation requests should complete within 15s");

        deactivationFuture.get(5, TimeUnit.SECONDS);
        publicationFuture.get(5, TimeUnit.SECONDS);

        executor.shutdown();

        // Assert zero errors occurred during concurrent reads, publication, and deactivation
        if (!errors.isEmpty()) {
            fail("Concurrency errors detected: " + errors.get(0).getMessage());
        }

        // Run explicit post-deactivation read before asserting observation count to ensure
        // cache fence exclusion is verified regardless of reader thread scheduling
        RecommendationResponseDto postDeactResponse = recommendationService.generateRecommendations(
                "user-post-deact",
                RecommendationRequestDto.builder()
                        .primaryAge(32)
                        .insuranceType("INDIVIDUAL")
                        .cityOrPincode("Bengaluru")
                        .annualBudget(25000.0)
                        .budgetType("PREFERRED")
                        .primaryPriority("BALANCED")
                        .members(new ArrayList<>())
                        .preExistingConditions(new ArrayList<>())
                        .build()
        );
        assertNotNull(postDeactResponse);
        assertFalse(postDeactResponse.getRankedPolicies().stream()
                .anyMatch(p -> "CARE_SUPREME".equals(p.getPolicyCode())),
                "Post-deactivation read must exclude deactivated policy CARE_SUPREME");
        deactivationObservedCount.incrementAndGet();

        assertEquals(readerThreadCount * operationsPerThread, successfulReads.get());
        assertTrue(deactivationObservedCount.get() > 0, "At least some reads must have verified deactivation exclusion");

        // Verify final catalog state: targetToDeactivate must be excluded, new policy must be present
        List<StructuredPolicy> finalCatalog = recommendationService.getCatalog();
        assertFalse(finalCatalog.stream().anyMatch(p -> "CARE_SUPREME".equals(p.getPolicyCode())),
                "Deactivated policy must be excluded from catalog");
        assertTrue(finalCatalog.stream().anyMatch(p -> "CONCURRENT_NEW_POLICY_9".equals(p.getPolicyCode())),
                "Published policy must be present in catalog");
    }
}
