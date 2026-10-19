package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.recommendation.benchmark.RecommendationQualityBenchmarkService;
import com.vidhura.ai.recommendation.benchmark.RecommendationQualityReport;
import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import com.vidhura.ai.recommendation.repository.StructuredPolicyRepository;
import com.vidhura.ai.recommendation.service.PolicyDiscoveryPipelineService;
import com.vidhura.ai.recommendation.service.PolicyPublicationService;
import com.vidhura.ai.recommendation.service.RecommendationService;
import com.vidhura.ai.recommendation.model.DiscoveredDocument;
import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import com.vidhura.ai.recommendation.service.PolicySourceMonitor;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for policy discovery, ingestion pipeline, catalog management,
 * and human verification before publication.
 *
 * Administrative mutations (discover, verify, flag, cache refresh, source crawl) are
 * strictly restricted to authenticated reviewers with ROLE_ADMIN or ROLE_REVIEWER.
 */
@Slf4j
@RestController
@RequestMapping("/api/policies")
public class PolicyCatalogController {

    private final PolicyDiscoveryPipelineService discoveryPipelineService;
    private final PolicyPublicationService publicationService;
    private final RecommendationService recommendationService;
    private final StructuredPolicyRepository policyRepository;
    private final PolicySourceRepository sourceRepository;
    private final DiscoveredDocumentRepository documentRepository;
    private final PolicySourceMonitor sourceMonitor;
    private final RecommendationQualityBenchmarkService benchmarkService;

    @Autowired
    public PolicyCatalogController(
            PolicyDiscoveryPipelineService discoveryPipelineService,
            PolicyPublicationService publicationService,
            RecommendationService recommendationService,
            StructuredPolicyRepository policyRepository,
            @Autowired(required = false) PolicySourceRepository sourceRepository,
            @Autowired(required = false) DiscoveredDocumentRepository documentRepository,
            @Autowired(required = false) PolicySourceMonitor sourceMonitor,
            @Autowired(required = false) RecommendationQualityBenchmarkService benchmarkService) {
        this.discoveryPipelineService = discoveryPipelineService;
        this.publicationService = publicationService;
        this.recommendationService = recommendationService;
        this.policyRepository = policyRepository;
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.sourceMonitor = sourceMonitor;
        this.benchmarkService = benchmarkService;
    }

    public PolicyCatalogController(
            PolicyDiscoveryPipelineService discoveryPipelineService,
            PolicyPublicationService publicationService,
            RecommendationService recommendationService,
            StructuredPolicyRepository policyRepository) {
        this(discoveryPipelineService, publicationService, recommendationService, policyRepository, null, null, null, null);
    }

    private void assertReviewerOrAdmin(User user) {
        if (user == null || user.getRole() == null
                || (!"ROLE_ADMIN".equals(user.getRole()) && !"ROLE_REVIEWER".equals(user.getRole()))) {
            throw new AccessDeniedException("Access Denied: Only authorized reviewers or administrators can modify or publish catalog policies.");
        }
    }

    private void assertReviewerOrAuditor(User user) {
        if (user == null || user.getRole() == null
                || (!"ROLE_ADMIN".equals(user.getRole()) && !"ROLE_REVIEWER".equals(user.getRole()) && !"ROLE_GUEST".equals(user.getRole()))) {
            throw new AccessDeniedException("Access Denied: Only authorized reviewers, administrators, or audit guests can access staged catalog records.");
        }
    }

    private boolean isReviewerOrAdmin(User user) {
        return user != null && user.getRole() != null
                && ("ROLE_ADMIN".equals(user.getRole()) || "ROLE_REVIEWER".equals(user.getRole()));
    }

    private StructuredPolicy sanitizeForPublic(StructuredPolicy p) {
        if (p == null) return null;
        return StructuredPolicy.builder()
                .id(p.getId())
                .policyCode(p.getPolicyCode())
                .policyName(p.getPolicyName())
                .provider(p.getProvider())
                .irdaUin(p.getIrdaUin())
                .uinRevision(p.getUinRevision())
                .planName(p.getPlanName())
                .category(p.getCategory())
                .policyWordingDocument(p.getPolicyWordingDocument())
                .officialDocumentUrl(p.getOfficialDocumentUrl())
                .active(p.isActive())
                .verificationStatus(p.getVerificationStatus())
                .regulatoryFilingSource(p.getRegulatoryFilingSource())
                .lastAuditedDate(p.getLastAuditedDate())
                .contractConfidenceScore(p.getContractConfidenceScore())
                .pricingConfidenceScore(p.getPricingConfidenceScore())
                .pricingStatus(p.getPricingStatus())
                .confidenceScore(p.getConfidenceScore())
                .verifiedBy("Catalog reviewer")
                .contractTerms(p.getContractTerms())
                .insurerMetrics(p.getInsurerMetrics())
                .sumInsuredTiers(p.getSumInsuredTiers())
                .exclusions(p.getExclusions())
                .documentEvidence(p.getDocumentEvidence())
                .publicationDate(p.getPublicationDate())
                .versionDate(p.getVersionDate())
                .build();
    }

    /**
     * Lists policies in the catalog. Public users only receive verified, published policies
     * with sanitized reviewer identity and omitted internal audit logs.
     */
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<StructuredPolicy>>> getCatalog(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @AuthenticationPrincipal User currentUser) {

        boolean isStaff = isReviewerOrAdmin(currentUser);
        List<StructuredPolicy> policies;
        if (isStaff) {
            policies = recommendationService.getFullCatalog(status);
        } else {
            policies = recommendationService.loadCatalog().stream()
                    .map(this::sanitizeForPublic)
                    .toList();
        }
        return ResponseEntity.ok(ApiResponse.ok(policies, policies.size()));
    }

    public ResponseEntity<ApiResponse<List<StructuredPolicy>>> getCatalog(String status) {
        return getCatalog(status, null);
    }

    /**
     * Retrieves catalog governance statistics across verification states.
     */
    @GetMapping("/catalog/stats")
    public ResponseEntity<ApiResponse<PolicyDiscoveryDto.CatalogStatsDto>> getCatalogStats() {
        PolicyDiscoveryDto.CatalogStatsDto stats = publicationService.getCatalogStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    /**
     * Retrieves single structured policy details by ID.
     * Public callers only receive published policies with redacted reviewer PII and audit history.
     */
    @GetMapping("/catalog/{id}")
    public ResponseEntity<ApiResponse<StructuredPolicy>> getPolicyById(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        boolean isStaff = isReviewerOrAdmin(currentUser);

        return policyRepository.findById(id)
                .filter(p -> isStaff || (p.isActive() && recommendationService.isPublicationReady(p)))
                .map(p -> ResponseEntity.ok(ApiResponse.ok(isStaff ? p : sanitizeForPublic(p))))
                .orElse(ResponseEntity.status(404).body(ApiResponse.err("Policy not found with id: " + id)));
    }

    /**
     * Ingests and extracts a policy document or filing into the standard schema.
     * Evaluates completeness, computes confidence score, and flags uncertain fields for human review.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/discover")
    public ResponseEntity<ApiResponse<PolicyDiscoveryDto.PolicyIngestionResultDto>> discoverPolicy(
            @Valid @RequestBody PolicyDiscoveryDto.PolicyDiscoveryRequest request,
            @AuthenticationPrincipal User currentUser) {

        assertReviewerOrAdmin(currentUser);
        log.info("[PolicyCatalogController] Discovered policy ingestion request by user {}: {}",
                currentUser.getEmail(), request.getPolicyName());
        PolicyDiscoveryDto.PolicyIngestionResultDto result = discoveryPipelineService.discoverAndIngest(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Human verification and publication gate:
     * Approves, verifies, and publishes a policy into the active recommendation catalog,
     * or flags it for revision if rejected.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/{id}/verify", "/verify/{id}", "/catalog/{id}/verify"})
    public ResponseEntity<ApiResponse<StructuredPolicy>> verifyPolicy(
            @PathVariable String id,
            @RequestBody PolicyDiscoveryDto.PolicyVerificationRequest request,
            @AuthenticationPrincipal User currentUser) {

        assertReviewerOrAdmin(currentUser);

        // Bind verified reviewer identity from authenticated session
        if (currentUser.getName() != null && !currentUser.getName().isBlank()) {
            request.setReviewerName(currentUser.getName() + " (" + currentUser.getEmail() + ")");
        } else {
            request.setReviewerName(currentUser.getEmail());
        }

        log.info("[PolicyCatalogController] Verification decision for policy id={} by reviewer {}: {}",
                id, request.getReviewerName(), request.getDecision());
        StructuredPolicy verified = publicationService.verifyAndPublish(id, request);
        return ResponseEntity.ok(ApiResponse.ok(verified));
    }

    /**
     * Flags a policy as outdated or incomplete, excluding it from recommendations.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/{id}/flag", "/flag/{id}", "/catalog/{id}/flag"})
    public ResponseEntity<ApiResponse<StructuredPolicy>> flagPolicy(
            @PathVariable String id,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false, defaultValue = "FLAGGED_OUTDATED") String status,
            @AuthenticationPrincipal User currentUser) {

        assertReviewerOrAdmin(currentUser);
        log.warn("[PolicyCatalogController] Flagging policy id={} as {} by reviewer {}", id, status, currentUser.getEmail());
        StructuredPolicy flagged = publicationService.flagPolicy(id, reason, status, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(flagged));
    }

    /**
     * Rolls back an active policy revision and restores the previously superseded revision.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/{id}/rollback", "/rollback/{id}", "/catalog/{id}/rollback"})
    public ResponseEntity<ApiResponse<StructuredPolicy>> rollbackPolicy(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "Manual rollback triggered by reviewer") String reason,
            @AuthenticationPrincipal User currentUser) {

        assertReviewerOrAdmin(currentUser);
        String reviewer = (currentUser.getName() != null && !currentUser.getName().isBlank())
                ? currentUser.getName() + " (" + currentUser.getEmail() + ")"
                : currentUser.getEmail();

        log.warn("[PolicyCatalogController] Rollback requested for policy id={} by reviewer {}", id, reviewer);
        StructuredPolicy rolledBack = publicationService.rollbackRevision(id, reviewer, reason);
        return ResponseEntity.ok(ApiResponse.ok(rolledBack));
    }

    /**
     * Forces immediate cache invalidation and reload from MongoDB.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/cache/refresh", "/refresh"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshCache(
            @AuthenticationPrincipal User currentUser) {

        assertReviewerOrAdmin(currentUser);
        log.info("[PolicyCatalogController] Cache refresh requested by {}", currentUser.getEmail());
        recommendationService.refreshCatalog();
        int count = recommendationService.getCatalog().size();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "Policy catalog cache successfully refreshed from MongoDB.",
                "activeVerifiedPoliciesCount", count
        )));
    }

    /**
     * Lists staged draft policies awaiting human verification.
     * Allowed for Reviewers, Admins, and Audit Guests.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<List<StructuredPolicy>>> getDrafts(
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);
        List<StructuredPolicy> drafts;
        try {
            drafts = policyRepository.findAll().stream()
                    .filter(p -> !p.isActive() || "EXTRACTION_PENDING_REVIEW".equalsIgnoreCase(p.getVerificationStatus())
                            || "DRAFT_DISCOVERED".equalsIgnoreCase(p.getVerificationStatus()))
                    .toList();
        } catch (Exception e) {
            log.warn("[PolicyCatalogController] Exception reading drafts: {}", e.getMessage());
            drafts = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(drafts, drafts.size()));
    }

    /**
     * Lists all registered official insurer and IRDAI policy sources.
     * Allowed for Reviewers, Admins, and Audit Guests.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping("/sources")
    public ResponseEntity<ApiResponse<List<PolicySource>>> getSources(
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);
        List<PolicySource> sources;
        try {
            sources = sourceRepository != null ? sourceRepository.findAll() : List.of();
            if ((sources == null || sources.isEmpty()) && sourceMonitor != null) {
                sources = sourceMonitor.getDefaultSeedSources();
            }
        } catch (Exception e) {
            log.warn("[PolicyCatalogController] Exception reading sources, using default seeds: {}", e.getMessage());
            sources = sourceMonitor != null ? sourceMonitor.getDefaultSeedSources() : List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(sources, sources.size()));
    }

    /**
     * Registers a new official insurer document source for monitoring.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/sources")
    public ResponseEntity<ApiResponse<PolicySource>> registerSource(
            @RequestBody PolicySource source,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (source.getId() == null || source.getId().isBlank()) {
            source.setId("src-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (source.getCreatedAt() == null) {
            source.setCreatedAt(Instant.now());
        }
        source.setUpdatedAt(Instant.now());
        PolicySource saved = sourceRepository != null ? sourceRepository.save(source) : source;
        log.info("[PolicyCatalogController] Registered new source '{}' by {}", saved.getSourceName(), currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    /**
     * Registers a manually curated official insurer document source.
     * When automated portal discovery encounters CDN bot protections or when a reviewer
     * registers a newly published official policy wording URL:
     * - Enforces approved official domain allowlist.
     * - Enforces pinned IP connection and SSRF defenses.
     * - Downloads real document and validates %PDF- magic bytes signature.
     * - Parses IRDAI statutory UIN (alphanumeric or slash regulatory format).
     * - Cross-checks insurer entity prefix alignment.
     * - Stages draft policy as EXTRACTION_PENDING_REVIEW with publication gate strictly preserved.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/sources/register-curated")
    public ResponseEntity<ApiResponse<PolicySourceMonitor.CuratedSourceResponse>> registerCuratedOfficialSource(
            @RequestBody PolicySourceMonitor.CuratedSourceRequest request,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (sourceMonitor == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicySourceMonitor service is unavailable."));
        }
        String reviewer = currentUser != null ? currentUser.getEmail() : "admin@vidhura.ai";
        log.info("[PolicyCatalogController] Curated source registration requested by {} for URL: {}",
                reviewer, request != null ? request.getSourceUrl() : "null");
        try {
            PolicySourceMonitor.CuratedSourceResponse response =
                    sourceMonitor.registerCuratedOfficialSource(request, reviewer);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err(e.getMessage()));
        } catch (Exception e) {
            log.error("[PolicyCatalogController] Curated source registration failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.err("Failed to register curated official source: " + e.getMessage()));
        }
    }

    /**
     * Triggers immediate background crawl for a specific source.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/sources/{id}/crawl", "/crawl/{id}"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlSource(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "false") boolean force,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (sourceMonitor == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicySourceMonitor service is unavailable."));
        }
        String outcome = sourceMonitor.pollSingleSource(id, force);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "sourceId", id,
                "outcome", outcome
        )));
    }

    /**
     * Discovers and registers new official insurer sources into the monitored registry.
     * Evaluates official domain provenance, SSRF safety, scheme, and deduplication.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/sources/discover", "/discover-sources"})
    public ResponseEntity<ApiResponse<PolicySourceMonitor.DiscoveryReport>> discoverSources(
            @RequestBody(required = false) List<String> candidateUrls,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (sourceMonitor == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicySourceMonitor service is unavailable."));
        }
        log.info("[PolicyCatalogController] Source discovery triggered by reviewer {}", currentUser.getEmail());
        PolicySourceMonitor.DiscoveryReport report = sourceMonitor.discoverNewCandidateSources(candidateUrls);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    /**
     * Overloaded method for backward-compatible invocations from service tests.
     */
    public ResponseEntity<ApiResponse<PolicySourceMonitor.CrawlSummary>> crawlAllSources(
            boolean force,
            User currentUser) {
        return crawlAllSources(force, true, currentUser);
    }

    /**
     * Triggers immediate two-stage discovery crawl across all active official policy sources.
     * Stage 1: Official Source Discovery (find & register new official insurer products).
     * Stage 2: Targeted Document Change Detection & Ingestion via conditional requests and SHA-256.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/sources/crawl-all", "/crawl-all"})
    public ResponseEntity<ApiResponse<PolicySourceMonitor.CrawlSummary>> crawlAllSources(
            @RequestParam(required = false, defaultValue = "false") boolean force,
            @RequestParam(required = false, defaultValue = "true") boolean discoverNewSources,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (sourceMonitor == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicySourceMonitor service is unavailable."));
        }
        log.info("[PolicyCatalogController] Two-stage crawl-all triggered by reviewer {} (discoverNewSources={}, force={})",
                currentUser.getEmail(), discoverNewSources, force);
        PolicySourceMonitor.CrawlSummary summary = sourceMonitor.executeTwoStageDiscoveryAndCrawl(discoverNewSources, force);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Retrieves recent discovered document archive logs and extraction statuses.
     * Allowed for Reviewers, Admins, and Audit Guests.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DiscoveredDocument>>> getDiscoveredDocuments(
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);
        List<DiscoveredDocument> docs;
        try {
            docs = documentRepository != null ? documentRepository.findTop20ByOrderByFetchedAtDesc() : List.of();
        } catch (Exception e) {
            log.warn("[PolicyCatalogController] Exception reading discovered documents: {}", e.getMessage());
            docs = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(docs, docs.size()));
    }

    /**
     * Executes the Recommendation Quality Evaluation Benchmark Suite.
     * Evaluates mandatory constraint compliance, ranking agreement, and claim math.
     * Allowed for Reviewers, Admins, and Audit Guests for interactive evaluation.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping({"/benchmark/quality-report", "/quality-report"})
    public ResponseEntity<ApiResponse<RecommendationQualityReport>> getQualityBenchmarkReport(
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);
        if (benchmarkService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("RecommendationQualityBenchmarkService is not available."));
        }
        log.info("[PolicyCatalogController] Running Recommendation Quality Benchmark Suite requested by {}", currentUser.getEmail());
        RecommendationQualityReport report = benchmarkService.runBenchmarkSuite();
        return ResponseEntity.ok(ApiResponse.ok(report));
    }
}
