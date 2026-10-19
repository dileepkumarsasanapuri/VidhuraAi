package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.model.DiscoveredDocument;
import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 4 Multi-Insurer Discovery Acceptance Test Suite.
 *
 * Verifies end-to-end reliability across all configured insurance providers:
 * 1. Coverage Classification Matrix (Contract vs Catalogue vs Blocked/Obsolete).
 * 2. Source Health, Observability & Exponential Backoff Persistence.
 * 3. Publication Gate Enforcement (Catalogues and unreviewed drafts blocked).
 * 4. Deduplication & Zero-Duplicate Draft Guarantees on repeat crawls.
 * 5. Security & SSRF Protection Defenses (No bot bypass, no private IP SSRF).
 */
@DisplayName("Multi-Insurer Live Discovery Acceptance Test Suite")
public class MultiInsurerDiscoveryAcceptanceTest {

    private PolicySourceRepository sourceRepository;
    private DiscoveredDocumentRepository documentRepository;
    private StructuredPolicyRepository structuredPolicyRepository;
    private PolicyDocumentFetcher documentFetcher;
    private CanonicalProductRegistry canonicalProductRegistry;
    private PolicyDiscoveryPipelineService discoveryPipelineService;
    private PolicyPublicationService publicationService;
    private PolicySourceMonitor sourceMonitor;

    private final Map<String, StructuredPolicy> inMemoryPolicies = new HashMap<>();
    private final Map<String, PolicySource> inMemorySources = new HashMap<>();
    private final Map<String, DiscoveredDocument> inMemoryDocuments = new HashMap<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        inMemoryPolicies.clear();
        inMemorySources.clear();
        inMemoryDocuments.clear();

        sourceRepository = mock(PolicySourceRepository.class);
        documentRepository = mock(DiscoveredDocumentRepository.class);
        structuredPolicyRepository = mock(StructuredPolicyRepository.class);
        documentFetcher = mock(PolicyDocumentFetcher.class);

        when(structuredPolicyRepository.save(any(StructuredPolicy.class))).thenAnswer(inv -> {
            StructuredPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId("policy-" + UUID.randomUUID().toString().substring(0, 8));
            inMemoryPolicies.put(p.getId(), p);
            return p;
        });
        when(structuredPolicyRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(inMemoryPolicies.get(inv.getArgument(0))));
        when(structuredPolicyRepository.findByPolicyCode(anyString()))
                .thenAnswer(inv -> inMemoryPolicies.values().stream()
                        .filter(p -> inv.getArgument(0).equals(p.getPolicyCode()))
                        .findFirst());

        when(sourceRepository.save(any(PolicySource.class))).thenAnswer(inv -> {
            PolicySource s = inv.getArgument(0);
            if (s.getId() == null) s.setId("src-" + UUID.randomUUID().toString().substring(0, 8));
            inMemorySources.put(s.getId(), s);
            return s;
        });
        when(sourceRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(inMemorySources.get(inv.getArgument(0))));

        when(documentRepository.save(any(DiscoveredDocument.class))).thenAnswer(inv -> {
            DiscoveredDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId("doc-" + UUID.randomUUID().toString().substring(0, 8));
            inMemoryDocuments.put(d.getId(), d);
            return d;
        });

        canonicalProductRegistry = new CanonicalProductRegistry();
        discoveryPipelineService = new PolicyDiscoveryPipelineService(structuredPolicyRepository, canonicalProductRegistry);
        publicationService = new PolicyPublicationService(structuredPolicyRepository, null, canonicalProductRegistry);

        sourceMonitor = new PolicySourceMonitor(
                sourceRepository,
                documentRepository,
                documentFetcher,
                null,
                discoveryPipelineService,
                canonicalProductRegistry
        );

        when(documentFetcher.isPdfContent(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Acceptance 1: Product Catalogues are classified as PRODUCT_CATALOGUE and blocked from publication")
    void testProductCatalogueClassificationAndPublicationGate() {
        // UIIC & Niva Bupa live schedule document simulation
        String catalogueText = """
                UNITED INDIA INSURANCE COMPANY LIMITED
                SCHEDULE OF RETAIL HEALTH PRODUCTS (2024-2025)
                Product Name: Individual Health Insurance Policy   UIN: UIIPAIP26041V012526
                Product Name: Family Medicare Policy              UIN: IRDAI/HLT/UII/P-H/V.1/20/2021-22
                Product Name: Senior Citizen Health Insurance     UIN: UIIHLIP21140V012021
                Product Name: Super Top Up Health Insurance       UIN: UIIHLIP21141V012021
                Note: This document provides list of active retail products and their IRDAI statutory identifiers.
                """;

        byte[] catBytes = ("%PDF-1.4\n" + catalogueText).getBytes(StandardCharsets.UTF_8);
        String sha256 = PolicyDocumentFetcher.computeSha256(catBytes);

        PolicySource source = PolicySource.builder()
                .id("src-uiic-cat")
                .sourceName("United India Retail Products List")
                .provider("United India Insurance Co. Ltd.")
                .sourceUrl("https://uiic.co.in/web/sites/default/files/basic-documents/List-of-Retail-Products.pdf")
                .active(true)
                .build();
        inMemorySources.put(source.getId(), source);

        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(catBytes)
                        .sha256Checksum(sha256)
                        .fileSizeBytes(catBytes.length)
                        .build());

        String result = sourceMonitor.pollSingleSource("src-uiic-cat", false);
        assertTrue(result.startsWith("EXTRACTED_STAGED"));

        // Verify Source Health & Classification
        assertEquals("PRODUCT_CATALOGUE", source.getDocumentClassification());
        assertFalse(source.isContractualWording());
        assertEquals(1, source.getCataloguesCount());
        assertEquals(0, source.getContractualWordingsCount());
        assertTrue(source.getCandidateCount() >= 4, "Should parse individual products as discovery candidates");

        // Verify Staged Policy Draft
        StructuredPolicy staged = inMemoryPolicies.get(source.getLinkedDraftPolicyId());
        assertNotNull(staged);
        assertEquals("PRODUCT_CATALOGUE", staged.getDocumentClassification());
        assertEquals(0.0, staged.getContractConfidenceScore());
        assertEquals("EXTRACTION_PENDING_REVIEW", staged.getVerificationStatus());
        assertFalse(staged.isActive());

        // Enforce Publication Gate: Approving a catalogue MUST throw IllegalStateException!
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("senior_reviewer@vidhura.ai", "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")))
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            publicationService.verifyAndPublish(staged.getId(), PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                    .decision("APPROVE")
                    .reviewerName("senior_reviewer@vidhura.ai")
                    .reviewerNotes("Attempting approval of catalogue")
                    .build());
        });
        assertTrue(ex.getMessage().contains("Cannot publish policy"));
        assertTrue(ex.getMessage().contains("PRODUCT_CATALOGUE"));
    }

    @Test
    @DisplayName("Acceptance 2: Verified Contractual Policy Wordings can be approved by authorized reviewer")
    void testContractualPolicyWordingVerificationAndApproval() {
        String wordingText = """
                %PDF-1.4
                HDFC ERGO General Insurance Company Limited
                POLICY WORDING - OPTIMA SECURE
                IRDAI UIN: HDFHLIP21147V012021
                OPERATIVE CLAUSE:
                Subject to terms, conditions, and exclusions, the company agrees to indemnify.
                SECTION 1: SCOPE OF COVER & BENEFITS COVERED
                In-patient Hospitalization: We cover medical expenses incurred during hospital stay.
                Room Rent: Single private AC room without any cap.
                Pre-Hospitalization: 60 days. Post-Hospitalization: 180 days.
                SECTION 2: WAITING PERIODS
                Pre-Existing Diseases (PED): Waiting period of 36 months.
                Initial Waiting Period: 30 days.
                SECTION 3: GENERAL EXCLUSIONS
                Permanent exclusions include intentional self-injury and cosmetic treatments.
                Co-payment: No co-payment required across network hospitals.
                Sum Insured Restoration: 100% automatic restore upon exhaustion.
                """;

        byte[] wordingBytes = wordingText.getBytes(StandardCharsets.UTF_8);
        String sha256 = PolicyDocumentFetcher.computeSha256(wordingBytes);

        PolicySource source = PolicySource.builder()
                .id("src-hdfc-optima-wording")
                .sourceName("HDFC ERGO Optima Secure Policy Wordings")
                .provider("HDFC ERGO General Insurance Co. Ltd.")
                .canonicalProductName("Optima Secure")
                .irdaUin("HDFHLIP21147V012021")
                .expectedRevision("V012021")
                .sourceUrl("https://www.hdfcergo.com/docs/default-source/downloads/policy-wordings/optima-secure.pdf")
                .active(true)
                .build();
        inMemorySources.put(source.getId(), source);

        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(wordingBytes)
                        .sha256Checksum(sha256)
                        .fileSizeBytes(wordingBytes.length)
                        .build());

        String result = sourceMonitor.pollSingleSource("src-hdfc-optima-wording", false);
        assertTrue(result.startsWith("EXTRACTED_STAGED"));

        // Verify Source Health Tracking
        assertEquals("POLICY_WORDING", source.getDocumentClassification());
        assertTrue(source.isContractualWording());
        assertEquals(1, source.getContractualWordingsCount());
        assertEquals(0, source.getCataloguesCount());
        assertNotNull(source.getLastSuccessfulDiscoveryAt());
        assertNotNull(source.getLastSuccessfulCrawlAt());

        StructuredPolicy staged = inMemoryPolicies.get(source.getLinkedDraftPolicyId());
        assertNotNull(staged);
        assertEquals("POLICY_WORDING", staged.getDocumentClassification());
        assertTrue(staged.getContractConfidenceScore() >= 80.0);

        // Reviewer Approval Gate
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("lead_reviewer@vidhura.ai", "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")))
        );

        StructuredPolicy published = publicationService.verifyAndPublish(
                staged.getId(),
                PolicyDiscoveryDto.PolicyVerificationRequest.builder()
                        .decision("APPROVE")
                        .reviewerName("lead_reviewer@vidhura.ai")
                        .reviewerNotes("Verified official contractual terms directly from policy wording")
                        .build()
        );

        assertTrue(published.isActive());
        assertEquals("VERIFIED_OFFICIAL_CATALOG", published.getVerificationStatus());
        assertEquals("lead_reviewer@vidhura.ai", published.getVerifiedBy());
    }

    @Test
    @DisplayName("Acceptance 3: Bot-Protected 403 endpoints trigger backoff without scraping bypass")
    void testBotProtected403EndpointHandlingAndBackoff() {
        PolicySource source = PolicySource.builder()
                .id("src-star-blocked")
                .sourceName("Star Health WAF Protected")
                .provider("Star Health and Allied Insurance Co. Ltd.")
                .sourceUrl("https://www.starhealth.in/sites/default/files/star-comprehensive-policy-wording.pdf")
                .active(true)
                .consecutiveFailures(0)
                .build();
        inMemorySources.put(source.getId(), source);

        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FAILED)
                        .httpStatusCode(403)
                        .errorMessage("HTTP request failed with status code: 403 (Akamai bot protection)")
                        .build());

        String r1 = sourceMonitor.pollSingleSource("src-star-blocked", false);
        assertTrue(r1.contains("FAILED_FETCH"));
        assertTrue(r1.contains("BOT_PROTECTION_403"));

        assertEquals("BOT_PROTECTION_403", source.getLastErrorCategory());
        assertEquals(1, source.getConsecutiveFailures());
        assertEquals(1, source.getBackoffHours());
        assertNotNull(source.getNextRetryEligibleAt());
        assertEquals("FAILED", source.getStatus());

        // Subsequent poll is skipped via active backoff
        String r2 = sourceMonitor.pollSingleSource("src-star-blocked", false);
        assertTrue(r2.startsWith("SKIPPED_BACKOFF_ACTIVE"));

        // Reviewer can still register verified document via curated registration endpoint
        byte[] manualBytes = ("%PDF-1.4\nStar Comprehensive Wording\nIRDAI UIN: SHAHLIP21163V012021\n"
                + "Waiting period: 36 months for PED. Room rent: single private room.").getBytes(StandardCharsets.UTF_8);

        when(documentFetcher.isBlockedHostOrIp("www.starhealth.in")).thenReturn(false);
        when(documentFetcher.fetchDocument(eq("https://www.starhealth.in/verified-wording.pdf"), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(manualBytes)
                        .sha256Checksum("sha256-verified-manual")
                        .fileSizeBytes(manualBytes.length)
                        .build());

        PolicySourceMonitor.CuratedSourceResponse curatedRes = sourceMonitor.registerCuratedOfficialSource(
                PolicySourceMonitor.CuratedSourceRequest.builder()
                        .sourceUrl("https://www.starhealth.in/verified-wording.pdf")
                        .sourceName("Star Health Verified Terms")
                        .provider("Star Health and Allied Insurance Co. Ltd.")
                        .expectedUin("SHAHLIP21163V012021")
                        .expectedRevision("V012021")
                        .curationNotes("Reviewer validated official filing")
                        .build(),
                "curator@vidhura.ai"
        );

        assertTrue(curatedRes.isSuccess());
        assertEquals("MANUAL_CURATED_OFFICIAL", curatedRes.getRegisteredSource().getSourceAuthority());
        assertEquals("EXTRACTION_PENDING_REVIEW", curatedRes.getVerificationStatus());
    }

    @Test
    @DisplayName("Acceptance 4: Endpoints returning non-PDF responses (JSON stubs/404) are rejected")
    void testNonPdfResponseRejection() {
        PolicySource source = PolicySource.builder()
                .id("src-niva-stub")
                .sourceName("Niva Bupa ReAssure 2.0 Wordings")
                .provider("Niva Bupa Health Insurance Co. Ltd.")
                .sourceUrl("https://www.nivabupa.com/content/dam/nivabupa/pdf/reassure-2-0-policy-wording.pdf")
                .active(true)
                .build();
        inMemorySources.put(source.getId(), source);

        byte[] jsonStub = "{\"error\": \"resource not found or moved\"}".getBytes(StandardCharsets.UTF_8);

        when(documentFetcher.isPdfContent(eq(jsonStub))).thenReturn(false); // Magic bytes check fails!
        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(jsonStub)
                        .fileSizeBytes(jsonStub.length)
                        .build());

        String outcome = sourceMonitor.pollSingleSource("src-niva-stub", false);

        assertTrue(outcome.contains("FAILED_INVALID_DOCUMENT_SIGNATURE"));
        assertEquals("INVALID_DOCUMENT_SIGNATURE", source.getLastErrorCategory());
        assertEquals("UNKNOWN", source.getDocumentClassification());
        assertEquals(1, source.getConsecutiveFailures());
        assertEquals(1, source.getBackoffHours());
    }

    @Test
    @DisplayName("Acceptance 5: Deduplication across repeated crawls creates zero duplicate drafts")
    void testDeduplicationAndZeroDuplicateDrafts() {
        byte[] docBytes = ("%PDF-1.4\nCare Supreme Policy Terms\nIRDAI UIN: CHIHLIP21161V012021\n"
                + "Waiting period: 36 months. Room rent: no capping.").getBytes(StandardCharsets.UTF_8);
        String checksum = PolicyDocumentFetcher.computeSha256(docBytes);

        PolicySource source = PolicySource.builder()
                .id("src-care")
                .sourceName("Care Supreme")
                .provider("Care Health Insurance Ltd.")
                .sourceUrl("https://www.careinsurance.com/terms.pdf")
                .active(true)
                .build();
        inMemorySources.put(source.getId(), source);

        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), isNull(), isNull()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(docBytes)
                        .sha256Checksum(checksum)
                        .fileSizeBytes(docBytes.length)
                        .etag("\"etag-care-1\"")
                        .build());

        // First crawl: extracts and stages
        String r1 = sourceMonitor.pollSingleSource("src-care", false);
        assertTrue(r1.startsWith("EXTRACTED_STAGED"));
        assertEquals(1, inMemoryPolicies.size());

        // Second crawl with HTTP 304 Not Modified
        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), eq("\"etag-care-1\""), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED)
                        .httpStatusCode(304)
                        .etag("\"etag-care-1\"")
                        .build());

        String r2 = sourceMonitor.pollSingleSource("src-care", false);
        assertEquals("SKIPPED_304_NOT_MODIFIED", r2);
        assertEquals(1, inMemoryPolicies.size(), "Zero duplicate drafts must be created on 304!");

        // Third crawl: server returns 200 with identical SHA-256
        when(documentFetcher.fetchDocument(eq(source.getSourceUrl()), any(), any()))
                .thenReturn(PolicyDocumentFetcher.FetchResult.builder()
                        .status(PolicyDocumentFetcher.FetchStatus.FETCHED)
                        .httpStatusCode(200)
                        .documentBytes(docBytes)
                        .sha256Checksum(checksum)
                        .fileSizeBytes(docBytes.length)
                        .etag("\"etag-care-2\"")
                        .build());

        String r3 = sourceMonitor.pollSingleSource("src-care", false);
        assertEquals("SKIPPED_IDENTICAL_CHECKSUM", r3);
        assertEquals(1, inMemoryPolicies.size(), "Zero duplicate drafts must be created on identical checksum!");
    }

    @Test
    @DisplayName("Acceptance 6: SSRF defenses block private IP and loopback candidate URLs")
    void testSsrfDefensesOnCandidateRegistration() {
        PolicySourceMonitor.CuratedSourceRequest ssrfRequest = PolicySourceMonitor.CuratedSourceRequest.builder()
                .sourceUrl("https://www.hdfcergo.com/internal-admin.pdf")
                .expectedUin("HDFHLIP21147V012021")
                .build();

        when(documentFetcher.isBlockedHostOrIp("www.hdfcergo.com")).thenReturn(true);

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            sourceMonitor.registerCuratedOfficialSource(ssrfRequest, "reviewer@vidhura.ai");
        });
        assertTrue(ex.getMessage().contains("restricted private or cloud metadata IP"));
    }
}
