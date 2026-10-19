package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import com.vidhura.ai.recommendation.service.PolicyDiscoveryPipelineService;
import com.vidhura.ai.recommendation.service.PolicyPublicationService;
import com.vidhura.ai.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security & Role-Based Authorization Tests for Policy Catalog Administration.
 *
 * Verifies that:
 * 1. Unauthenticated or standard users (ROLE_USER) cannot access administrative endpoints.
 * 2. Only authorized reviewers (ROLE_REVIEWER or ROLE_ADMIN) can discover, verify, flag, or refresh policies.
 * 3. Authenticated reviewer identity is bound directly to the audit log, preventing spoofing.
 */
@ExtendWith(MockitoExtension.class)
public class PolicyCatalogSecurityTest {

    @Mock
    private PolicyDiscoveryPipelineService discoveryPipelineService;

    @Mock
    private PolicyPublicationService publicationService;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private StructuredPolicyRepository policyRepository;

    @Mock
    private com.vidhura.ai.recommendation.repository.PolicySourceRepository sourceRepository;

    @Mock
    private com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository documentRepository;

    @Mock
    private com.vidhura.ai.recommendation.service.PolicySourceMonitor sourceMonitor;

    @Mock
    private com.vidhura.ai.recommendation.benchmark.RecommendationQualityBenchmarkService benchmarkService;

    private PolicyCatalogController controller;

    private User standardUser;
    private User reviewerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        controller = new PolicyCatalogController(
                discoveryPipelineService,
                publicationService,
                recommendationService,
                policyRepository,
                sourceRepository,
                documentRepository,
                sourceMonitor,
                benchmarkService
        );

        standardUser = User.builder()
                .id("user-1")
                .name("Standard Consumer")
                .email("consumer@gmail.com")
                .role("ROLE_USER")
                .build();

        reviewerUser = User.builder()
                .id("reviewer-1")
                .name("Dr. Aditi Verma")
                .email("aditi.verma@insurance-audits.in")
                .role("ROLE_REVIEWER")
                .build();

        adminUser = User.builder()
                .id("admin-1")
                .name("System Administrator")
                .email("admin@vidhura.ai")
                .role("ROLE_ADMIN")
                .build();
    }

    @Test
    void testStandardUserCannotDiscoverPolicies() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .policyName("Test Plan")
                .rawPolicyText("Some policy wording")
                .build();

        assertThrows(AccessDeniedException.class, () -> controller.discoverPolicy(req, standardUser),
                "Standard users must not be permitted to ingest policy documents");
        verifyNoInteractions(discoveryPipelineService);
    }

    @Test
    void testUnauthenticatedUserCannotDiscoverPolicies() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest req = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .policyName("Test Plan")
                .rawPolicyText("Some policy wording")
                .build();

        assertThrows(AccessDeniedException.class, () -> controller.discoverPolicy(req, null),
                "Unauthenticated callers must be rejected");
        verifyNoInteractions(discoveryPipelineService);
    }

    @Test
    void testStandardUserCannotVerifyOrPublishPolicy() {
        PolicyDiscoveryDto.PolicyVerificationRequest req = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerName("Fraudulent Reviewer")
                .build();

        assertThrows(AccessDeniedException.class, () -> controller.verifyPolicy("policy-123", req, standardUser),
                "Standard users must not be permitted to publish policies");
        verifyNoInteractions(publicationService);
    }

    @Test
    void testStandardUserCannotFlagOrDeactivatePolicy() {
        assertThrows(AccessDeniedException.class, () -> controller.flagPolicy("policy-123", "Outdated", "FLAGGED_OUTDATED", standardUser),
                "Standard users must not be permitted to deactivate policies");
        verifyNoInteractions(publicationService);
    }

    @Test
    void testStandardUserCannotTriggerCacheRefresh() {
        assertThrows(AccessDeniedException.class, () -> controller.refreshCache(standardUser),
                "Standard users must not be permitted to trigger cache refreshes");
        verifyNoInteractions(recommendationService);
    }

    @Test
    void testReviewerCanDiscoverAndVerifyPolicies() {
        PolicyDiscoveryDto.PolicyDiscoveryRequest discoverReq = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .policyName("Star Health Assure")
                .rawPolicyText("IRDAI Registration UIN: SHAHLIP21123V012022")
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto ingestionResult = PolicyDiscoveryDto.PolicyIngestionResultDto.builder()
                .policyId("star-123")
                .policyName("Star Health Assure")
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .build();

        when(discoveryPipelineService.discoverAndIngest(any())).thenReturn(ingestionResult);

        ResponseEntity<ApiResponse<PolicyDiscoveryDto.PolicyIngestionResultDto>> discoverResp =
                controller.discoverPolicy(discoverReq, reviewerUser);

        assertNotNull(discoverResp);
        assertEquals(200, discoverResp.getStatusCode().value());
        assertEquals(Boolean.TRUE, discoverResp.getBody().getSuccess());

        // Now reviewer verifies
        PolicyDiscoveryDto.PolicyVerificationRequest verifyReq = PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                .decision("APPROVE")
                .reviewerNotes("Verified against IRDAI portal.")
                .build();

        StructuredPolicy verifiedPolicy = StructuredPolicy.builder()
                .id("star-123")
                .policyName("Star Health Assure")
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .verifiedBy("Dr. Aditi Verma (aditi.verma@insurance-audits.in)")
                .build();

        when(publicationService.verifyAndPublish(eq("star-123"), any())).thenReturn(verifiedPolicy);

        ResponseEntity<ApiResponse<StructuredPolicy>> verifyResp =
                controller.verifyPolicy("star-123", verifyReq, reviewerUser);

        assertNotNull(verifyResp);
        assertEquals(200, verifyResp.getStatusCode().value());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", verifyResp.getBody().getData().getVerificationStatus());

        // Confirm reviewer identity is bound directly from the session
        assertEquals("Dr. Aditi Verma (aditi.verma@insurance-audits.in)", verifyReq.getReviewerName());
    }

    @Test
    void testAdminCanFlagPolicyAndRefreshCache() {
        StructuredPolicy flaggedPolicy = StructuredPolicy.builder()
                .id("care-123")
                .verificationStatus("FLAGGED_OUTDATED")
                .active(false)
                .build();

        when(publicationService.flagPolicy(eq("care-123"), any(), any(), any())).thenReturn(flaggedPolicy);

        ResponseEntity<ApiResponse<StructuredPolicy>> flagResp =
                controller.flagPolicy("care-123", "Replaced by V02", "FLAGGED_OUTDATED", adminUser);

        assertNotNull(flagResp);
        assertEquals(200, flagResp.getStatusCode().value());
        assertFalse(flagResp.getBody().getData().isActive());

        when(recommendationService.getCatalog()).thenReturn(List.of());

        ResponseEntity<ApiResponse<Map<String, Object>>> refreshResp =
                controller.refreshCache(adminUser);

        assertNotNull(refreshResp);
        assertEquals(200, refreshResp.getStatusCode().value());
        verify(recommendationService).refreshCatalog();
    }

    @Test
    void testPublicCanReadCatalogAndStats() {
        StructuredPolicy internalPolicy = StructuredPolicy.builder()
                .id("pol-1")
                .policyCode("HDFC_OPTIMA")
                .policyName("HDFC ERGO Optima Secure")
                .verifiedBy("Dr. Aditi Verma (aditi.verma@insurance-audits.in)")
                .active(true)
                .build();

        // Public / non-administrative endpoints do not require reviewer or admin role
        when(recommendationService.loadCatalog()).thenReturn(List.of(internalPolicy));
        ResponseEntity<ApiResponse<List<StructuredPolicy>>> catalogResp = controller.getCatalog("ALL");
        assertEquals(200, catalogResp.getStatusCode().value());
        assertNotNull(catalogResp.getBody());
        assertEquals(1, catalogResp.getBody().getData().size());
        StructuredPolicy publicPolicy = catalogResp.getBody().getData().get(0);
        assertEquals("Catalog reviewer", publicPolicy.getVerifiedBy());
        assertNotEquals("IRDAI Compliance Verified", publicPolicy.getVerifiedBy());
        assertNotEquals("Dr. Aditi Verma (aditi.verma@insurance-audits.in)", publicPolicy.getVerifiedBy());

        when(publicationService.getCatalogStats()).thenReturn(new PolicyDiscoveryDto.CatalogStatsDto(7, 7, 0, 0, 0));
        ResponseEntity<ApiResponse<PolicyDiscoveryDto.CatalogStatsDto>> statsResp = controller.getCatalogStats();
        assertEquals(200, statsResp.getStatusCode().value());
    }

    @Test
    void testStandardUserCannotAccessGovernanceEndpoints() {
        // 1. getDrafts
        assertThrows(AccessDeniedException.class, () -> controller.getDrafts(standardUser));

        // 2. getSources
        assertThrows(AccessDeniedException.class, () -> controller.getSources(standardUser));

        // 3. registerSource
        com.vidhura.ai.recommendation.model.PolicySource newSource = com.vidhura.ai.recommendation.model.PolicySource.builder()
                .sourceName("Test")
                .sourceUrl("https://example.com/test.pdf")
                .build();
        assertThrows(AccessDeniedException.class, () -> controller.registerSource(newSource, standardUser));

        // 4. crawlSource
        assertThrows(AccessDeniedException.class, () -> controller.crawlSource("src-1", false, standardUser));

        // 5. crawlAllSources
        assertThrows(AccessDeniedException.class, () -> controller.crawlAllSources(false, standardUser));

        // 6. getDiscoveredDocuments
        assertThrows(AccessDeniedException.class, () -> controller.getDiscoveredDocuments(standardUser));

        // 7. rollbackPolicy
        assertThrows(AccessDeniedException.class, () -> controller.rollbackPolicy("pol-1", "reason", standardUser));

        // 8. getQualityBenchmarkReport
        assertThrows(AccessDeniedException.class, () -> controller.getQualityBenchmarkReport(standardUser));
    }

    @Test
    void testReviewerCanAccessGovernanceEndpoints() {
        when(policyRepository.findAll()).thenReturn(List.of());
        when(sourceRepository.findAll()).thenReturn(List.of());
        when(documentRepository.findTop20ByOrderByFetchedAtDesc()).thenReturn(List.of());
        when(sourceMonitor.pollSingleSource(eq("src-1"), anyBoolean())).thenReturn("SYNCED");

        // Reviewer can view drafts
        ResponseEntity<ApiResponse<List<StructuredPolicy>>> draftsResp = controller.getDrafts(reviewerUser);
        assertEquals(200, draftsResp.getStatusCode().value());

        // Reviewer can view sources
        ResponseEntity<ApiResponse<List<com.vidhura.ai.recommendation.model.PolicySource>>> sourcesResp = controller.getSources(reviewerUser);
        assertEquals(200, sourcesResp.getStatusCode().value());

        // Reviewer can crawl source
        ResponseEntity<ApiResponse<Map<String, Object>>> crawlResp = controller.crawlSource("src-1", false, reviewerUser);
        assertEquals(200, crawlResp.getStatusCode().value());
        assertEquals("SYNCED", crawlResp.getBody().getData().get("outcome"));

        // Reviewer can view documents
        ResponseEntity<ApiResponse<List<com.vidhura.ai.recommendation.model.DiscoveredDocument>>> docsResp = controller.getDiscoveredDocuments(reviewerUser);
        assertEquals(200, docsResp.getStatusCode().value());
    }

    @Test
    void testReviewerCanRollbackPolicy() {
        StructuredPolicy restored = StructuredPolicy.builder()
                .id("pol-prev")
                .policyCode("HDFC_OPTIMA_V01")
                .active(true)
                .verificationStatus("VERIFIED_OFFICIAL_CATALOG")
                .build();

        when(publicationService.rollbackRevision(eq("pol-active"), anyString(), eq("Defect detected in V02")))
                .thenReturn(restored);

        ResponseEntity<ApiResponse<StructuredPolicy>> rollbackResp =
                controller.rollbackPolicy("pol-active", "Defect detected in V02", reviewerUser);

        assertEquals(200, rollbackResp.getStatusCode().value());
        assertTrue(rollbackResp.getBody().getData().isActive());
        assertEquals("pol-prev", rollbackResp.getBody().getData().getId());
    }
}
