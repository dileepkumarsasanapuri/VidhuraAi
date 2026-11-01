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
import com.vidhura.ai.recommendation.model.PolicyDiscoveryCandidate;
import com.vidhura.ai.recommendation.model.PolicyDiscoveryRun;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryCandidateRepository;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryRunRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentRevisionRepository;
import com.vidhura.ai.recommendation.service.PolicyDiscoveryOrchestratorService;
import com.vidhura.ai.recommendation.service.PolicyDiscoverySchedulerService;
import com.vidhura.ai.recommendation.service.PolicyRegistrySyncService;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.vidhura.ai.recommendation.service.OfficialSourceAcquisitionService;
import com.vidhura.ai.recommendation.service.PolicyIngestionService;
import com.vidhura.ai.recommendation.service.PersistentArtifactStorageService;

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
    private final PolicyDiscoverySchedulerService discoverySchedulerService;
    private final PolicyDiscoveryOrchestratorService discoveryOrchestratorService;
    private final PolicyDiscoveryCandidateRepository candidateRepository;
    private final PolicyDiscoveryRunRepository runRepository;
    private final PolicyDocumentRevisionRepository revisionRepository;
    private final PolicyRegistrySyncService registrySyncService;
    private final OfficialSourceAcquisitionService acquisitionService;
    private final PolicyIngestionService policyIngestionService;
    private final PersistentArtifactStorageService persistentStorage;

    @Autowired
    public PolicyCatalogController(
            PolicyDiscoveryPipelineService discoveryPipelineService,
            PolicyPublicationService publicationService,
            RecommendationService recommendationService,
            StructuredPolicyRepository policyRepository,
            @Autowired(required = false) PolicySourceRepository sourceRepository,
            @Autowired(required = false) DiscoveredDocumentRepository documentRepository,
            @Autowired(required = false) PolicySourceMonitor sourceMonitor,
            @Autowired(required = false) RecommendationQualityBenchmarkService benchmarkService,
            @Autowired(required = false) PolicyDiscoverySchedulerService discoverySchedulerService,
            @Autowired(required = false) PolicyDiscoveryOrchestratorService discoveryOrchestratorService,
            @Autowired(required = false) PolicyDiscoveryCandidateRepository candidateRepository,
            @Autowired(required = false) PolicyDiscoveryRunRepository runRepository,
            @Autowired(required = false) PolicyDocumentRevisionRepository revisionRepository,
            @Autowired(required = false) PolicyRegistrySyncService registrySyncService,
            @Autowired(required = false) OfficialSourceAcquisitionService acquisitionService,
            @Autowired(required = false) PolicyIngestionService policyIngestionService,
            @Autowired(required = false) PersistentArtifactStorageService persistentStorage) {
        this.discoveryPipelineService = discoveryPipelineService;
        this.publicationService = publicationService;
        this.recommendationService = recommendationService;
        this.policyRepository = policyRepository;
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.sourceMonitor = sourceMonitor;
        this.benchmarkService = benchmarkService;
        this.discoverySchedulerService = discoverySchedulerService;
        this.discoveryOrchestratorService = discoveryOrchestratorService;
        this.candidateRepository = candidateRepository;
        this.runRepository = runRepository;
        this.revisionRepository = revisionRepository;
        this.registrySyncService = registrySyncService;
        this.acquisitionService = acquisitionService;
        this.policyIngestionService = policyIngestionService;
        this.persistentStorage = persistentStorage;
    }

    public PolicyCatalogController(
            PolicyDiscoveryPipelineService discoveryPipelineService,
            PolicyPublicationService publicationService,
            RecommendationService recommendationService,
            StructuredPolicyRepository policyRepository) {
        this(discoveryPipelineService, publicationService, recommendationService, policyRepository,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public PolicyCatalogController(
            PolicyDiscoveryPipelineService discoveryPipelineService,
            PolicyPublicationService publicationService,
            RecommendationService recommendationService,
            StructuredPolicyRepository policyRepository,
            PolicySourceRepository sourceRepository,
            DiscoveredDocumentRepository documentRepository,
            PolicySourceMonitor sourceMonitor,
            RecommendationQualityBenchmarkService benchmarkService) {
        this(discoveryPipelineService, publicationService, recommendationService, policyRepository,
                sourceRepository, documentRepository, sourceMonitor, benchmarkService,
                null, null, null, null, null, null, null, null, null);
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
     * Reseeds the catalog policies into MongoDB and synchronizes the policy registry.
     * Strictly restricted to ROLE_ADMIN or ROLE_REVIEWER.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/admin/reseed", "/reseed"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> reseedCatalog(
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        String userEmail = currentUser != null ? currentUser.getEmail() : "admin@vidhura.ai";
        log.info("[PolicyCatalogController] Reseed requested by {}", userEmail);
        int reseeded = recommendationService.adminReseedCatalog(userEmail, "Admin manual reseed");
        int insurersSynced = 0;
        int policiesSynced = 0;
        int sourcesSynced = 0;
        if (registrySyncService != null) {
            try {
                PolicyRegistrySyncService.SyncResult syncRes = registrySyncService.synchronizePolicyRegistry();
                insurersSynced = syncRes.insurersSynced();
                policiesSynced = syncRes.policiesSynced();
                sourcesSynced = syncRes.sourcesSynced();
            } catch (Exception e) {
                log.warn("[PolicyCatalogController] Registry sync warning: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "reseededCount", reseeded,
                "insurersSynced", insurersSynced,
                "policiesSynced", policiesSynced,
                "sourcesSynced", sourcesSynced
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

    // =========================================================================
    // PHASE 5.6: AUTOMATED POLICY DISCOVERY & OPERATOR CONTROLS
    // =========================================================================

    /**
     * Retrieves recent discovery run execution history.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping({"/discovery/runs", "/discovery-runs"})
    public ResponseEntity<ApiResponse<List<PolicyDiscoveryRun>>> getDiscoveryRuns(
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);
        List<PolicyDiscoveryRun> runs;
        try {
            runs = runRepository != null
                    ? runRepository.findTop20ByOrderByStartedAtDesc()
                    : List.of();
        } catch (Exception e) {
            log.warn("[PolicyCatalogController] Exception reading discovery runs: {}", e.getMessage());
            runs = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(runs, runs.size()));
    }

    /**
     * Triggers manual official-source discovery.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/discovery/trigger", "/trigger-discovery"})
    public ResponseEntity<ApiResponse<PolicyDiscoveryRun>> triggerDiscovery(
            @RequestBody(required = false) List<String> targetUrls,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (discoverySchedulerService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicyDiscoverySchedulerService is not available."));
        }
        log.info("[PolicyCatalogController] Manual discovery triggered by {}", currentUser != null ? currentUser.getEmail() : "SYSTEM");
        PolicyDiscoveryRun run = discoverySchedulerService.executeDiscoveryRun("MANUAL_ADMIN", targetUrls);
        return ResponseEntity.ok(ApiResponse.ok(run));
    }

    /**
     * Retrieves discovered candidate products (with optional stage filter).
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping({"/discovery/candidates", "/discovery-candidates"})
    public ResponseEntity<ApiResponse<List<PolicyDiscoveryCandidate>>> getDiscoveryCandidates(
            @RequestParam(required = false) String stage,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);
        List<PolicyDiscoveryCandidate> candidates;
        try {
            if (candidateRepository == null) {
                candidates = List.of();
            } else if (stage != null && !stage.isBlank()) {
                candidates = candidateRepository.findByLifecycleStage(stage);
            } else {
                candidates = candidateRepository.findAll();
            }
        } catch (Exception e) {
            log.warn("[PolicyCatalogController] Exception reading discovery candidates: {}", e.getMessage());
            candidates = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(candidates, candidates.size()));
    }

    /**
     * Triggers physical document acquisition for a candidate.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/discovery/candidates/{id}/acquire")
    public ResponseEntity<ApiResponse<PolicyDiscoveryCandidate>> acquireCandidate(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (discoveryOrchestratorService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicyDiscoveryOrchestratorService is not available."));
        }
        PolicyDiscoveryCandidate candidate = discoveryOrchestratorService.acquireCandidateDocument(id);
        return ResponseEntity.ok(ApiResponse.ok(candidate));
    }

    /**
     * Triggers extraction and validation for an acquired candidate.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/discovery/candidates/{id}/extract")
    public ResponseEntity<ApiResponse<PolicyDiscoveryCandidate>> extractCandidate(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (discoveryOrchestratorService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicyDiscoveryOrchestratorService is not available."));
        }
        PolicyDiscoveryCandidate candidate = discoveryOrchestratorService.extractAndValidateCandidate(id);
        return ResponseEntity.ok(ApiResponse.ok(candidate));
    }

    /**
     * Publishes a candidate policy to the active MongoDB catalog after human review.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/discovery/candidates/{id}/publish")
    public ResponseEntity<ApiResponse<PolicyDiscoveryCandidate>> publishCandidate(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (discoveryOrchestratorService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicyDiscoveryOrchestratorService is not available."));
        }
        String reviewer = currentUser != null ? currentUser.getEmail() : "ADMIN_OPERATOR";
        String notes = (body != null && body.containsKey("notes")) ? body.get("notes") : "Approved after human review.";
        PolicyDiscoveryCandidate published = discoveryOrchestratorService.publishCandidate(id, reviewer, notes);
        return ResponseEntity.ok(ApiResponse.ok(published));
    }

    /**
     * Retrieves document revisions for multi-version tracking.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping({"/discovery/revisions", "/discovery-revisions"})
    public ResponseEntity<ApiResponse<List<PolicyDocumentRevision>>> getDocumentRevisions(
            @RequestParam(required = false) String uin,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);
        List<PolicyDocumentRevision> revs;
        if (revisionRepository == null) {
            revs = List.of();
        } else if (uin != null && !uin.isBlank()) {
            revs = revisionRepository.findByIrdaUin(uin);
        } else {
            revs = revisionRepository.findAll();
        }
        return ResponseEntity.ok(ApiResponse.ok(revs, revs.size()));
    }

    /**
     * Phase 5.7: Deterministic Catalogue Authenticity and Provenance Audit.
     * Evaluates every policy currently stored in MongoDB or registered in the catalogue,
     * recomputing physical byte SHA-256 hashes, PDF page structures, and authenticity statuses.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'GUEST')")
    @GetMapping({"/catalog/audit", "/audit"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCatalogueAudit(
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAuditor(currentUser);

        List<StructuredPolicy> policies = policyRepository != null ? policyRepository.findAll() : List.of();
        if (policies.isEmpty() && policyIngestionService != null) {
            policies = policyIngestionService.getAllCatalogPolicies();
        }

        List<Map<String, Object>> auditItems = new ArrayList<>();
        int verifiedOfficialCount = 0;
        int syntheticFixtureCount = 0;
        int integrityFailures = 0;

        for (StructuredPolicy p : policies) {
            Map<String, Object> item = new HashMap<>();
            item.put("policyCode", p.getPolicyCode());
            item.put("policyName", p.getPolicyName());
            item.put("provider", p.getProvider());
            item.put("irdaUin", p.getIrdaUin());
            item.put("uinRevision", p.getUinRevision());
            item.put("officialDocumentUrl", p.getOfficialDocumentUrl());
            item.put("policyWordingDocument", p.getPolicyWordingDocument());
            item.put("recordedChecksum", p.getDocumentChecksum());
            item.put("syntheticFixture", p.isSyntheticFixture());
            item.put("verificationStatus", p.getVerificationStatus());
            item.put("reviewerApprovalStatus", p.getReviewerApprovalStatus());
            item.put("active", p.isActive());

            // Check physical file resolution
            String docName = p.getPolicyWordingDocument();
            File f = null;
            if (docName != null) {
                f = new File("src/main/resources/evidence/policies/" + docName);
                if (!f.exists() && persistentStorage != null && p.getDocumentChecksum() != null) {
                    f = persistentStorage.resolveArtifact(p.getDocumentChecksum()).orElse(null);
                }
            }

            boolean fileExists = (f != null && f.exists());
            item.put("physicalFileExists", fileExists);

            String recomputedHash = null;
            boolean hashesMatch = false;
            int pageCount = -1;
            long fileSizeBytes = 0;

            if (fileExists) {
                fileSizeBytes = f.length();
                recomputedHash = PersistentArtifactStorageService.computeFileSha256(f);
                hashesMatch = p.getDocumentChecksum() != null && p.getDocumentChecksum().equalsIgnoreCase(recomputedHash);
                try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(f)) {
                    pageCount = doc.getNumberOfPages();
                } catch (Exception ignored) {}
            }

            item.put("fileSizeBytes", fileSizeBytes);
            item.put("pageCount", pageCount);
            item.put("recomputedChecksum", recomputedHash);
            item.put("hashesMatch", hashesMatch);

            String authStatus;
            if (p.isSyntheticFixture() || "TEST_FIXTURE_OR_SYNTHETIC".equalsIgnoreCase(p.getDocumentAuthenticityStatus()) || (pageCount <= 2 && fileSizeBytes < 10000)) {
                authStatus = "TEST_FIXTURE_OR_SYNTHETIC";
                syntheticFixtureCount++;
            } else if (!hashesMatch && fileExists) {
                authStatus = "ARTIFACT_INTEGRITY_FAILURE";
                integrityFailures++;
            } else if (!fileExists) {
                authStatus = "SOURCE_UNAVAILABLE";
            } else if (p.getIrdaUin() != null && !p.getIrdaUin().startsWith("UNKNOWN") && hashesMatch && pageCount > 5) {
                authStatus = "VERIFIED_OFFICIAL_SOURCE";
                verifiedOfficialCount++;
            } else {
                authStatus = "UNVERIFIED_SOURCE";
            }

            item.put("documentAuthenticityStatus", authStatus);
            item.put("recommendationEligible", recommendationService != null && recommendationService.isPublicationReady(p));
            auditItems.add(item);
        }

        Map<String, Object> summary = Map.of(
                "totalAudited", auditItems.size(),
                "verifiedOfficialSourceCount", verifiedOfficialCount,
                "syntheticFixtureCount", syntheticFixtureCount,
                "integrityFailureCount", integrityFailures,
                "auditTimestamp", Instant.now().toString(),
                "auditItems", auditItems
        );

        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Phase 5.7: Explicit rejection of unverified or suspect discovery candidates.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/discovery/candidates/{id}/reject")
    public ResponseEntity<ApiResponse<PolicyDiscoveryCandidate>> rejectCandidate(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (discoveryOrchestratorService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicyDiscoveryOrchestratorService is not available."));
        }
        String reviewer = currentUser != null ? currentUser.getEmail() : "ADMIN_OPERATOR";
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Rejected by reviewer.";
        PolicyDiscoveryCandidate rejected = discoveryOrchestratorService.rejectCandidate(id, reviewer, reason);
        return ResponseEntity.ok(ApiResponse.ok(rejected));
    }

    /**
     * Phase 5.7: Administrative withdrawal of published policy with cache invalidation.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping({"/catalog/{policyCode}/withdraw", "/{policyCode}/withdraw"})
    public ResponseEntity<ApiResponse<StructuredPolicy>> withdrawPolicy(
            @PathVariable String policyCode,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (discoveryOrchestratorService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("PolicyDiscoveryOrchestratorService is not available."));
        }
        String reviewer = currentUser != null ? currentUser.getEmail() : "ADMIN_OPERATOR";
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Administrative policy withdrawal.";
        StructuredPolicy withdrawn = discoveryOrchestratorService.withdrawPolicy(policyCode, reviewer, reason);
        return ResponseEntity.ok(ApiResponse.ok(withdrawn));
    }

    /**
     * Phase 5.7: Controlled Real-Source Smoke Test endpoint.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @PostMapping("/discovery/smoke-test")
    public ResponseEntity<ApiResponse<OfficialSourceAcquisitionService.LiveSmokeTestResult>> runLiveSmokeTest(
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {
        assertReviewerOrAdmin(currentUser);
        if (acquisitionService == null) {
            return ResponseEntity.status(503).body(ApiResponse.err("OfficialSourceAcquisitionService is not available."));
        }
        String targetUrl = (body != null && body.containsKey("targetUrl"))
                ? body.get("targetUrl")
                : "https://uiic.co.in/sites/default/files/uploads/product/Individual%20Health%20Insurance%20Policy%20-%20Policy%20Wordings.pdf";
        OfficialSourceAcquisitionService.LiveSmokeTestResult res = acquisitionService.runControlledSourceSmokeTest(targetUrl);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }
}
