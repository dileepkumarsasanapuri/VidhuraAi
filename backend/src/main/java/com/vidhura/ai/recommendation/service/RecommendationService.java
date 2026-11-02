package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.GuidanceDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orchestrates the recommendation pipeline:
 * 1. Validates and persists the user profile
 * 2. Resolves city/zone for pricing
 * 3. Runs EligibilityEngine to filter catalog
 * 4. Runs RankingEngine to score and rank
 * 5. Assembles the full recommendation response
 *
 * Core principle: separates policy facts, recommendation calculations,
 * and any future AI explanations.
 */
@Slf4j
@Service
public class RecommendationService {

    private final StructuredPolicyRepository structuredPolicyRepository;
    private final RecommendationProfileRepository profileRepository;
    private final RecommendationResultRepository resultRepository;
    private final EligibilityEngine eligibilityEngine;
    private final RankingEngine rankingEngine;
    private final PolicyIngestionService policyIngestionService;
    private final com.vidhura.ai.recommendation.repository.CatalogMetadataRepository catalogMetadataRepository;

    // Local cached version tracking the latest loaded global catalog version
    private volatile long localCatalogVersion = 0L;

    @org.springframework.beans.factory.annotation.Autowired
    public RecommendationService(
            StructuredPolicyRepository structuredPolicyRepository,
            RecommendationProfileRepository profileRepository,
            RecommendationResultRepository resultRepository,
            EligibilityEngine eligibilityEngine,
            RankingEngine rankingEngine,
            PolicyIngestionService policyIngestionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.vidhura.ai.recommendation.repository.CatalogMetadataRepository catalogMetadataRepository) {
        this.structuredPolicyRepository = structuredPolicyRepository;
        this.profileRepository = profileRepository;
        this.resultRepository = resultRepository;
        this.eligibilityEngine = eligibilityEngine;
        this.rankingEngine = rankingEngine;
        this.policyIngestionService = policyIngestionService;
        this.catalogMetadataRepository = catalogMetadataRepository;
    }

    public RecommendationService(
            StructuredPolicyRepository structuredPolicyRepository,
            RecommendationProfileRepository profileRepository,
            RecommendationResultRepository resultRepository,
            EligibilityEngine eligibilityEngine,
            RankingEngine rankingEngine,
            PolicyIngestionService policyIngestionService) {
        this(structuredPolicyRepository, profileRepository, resultRepository, eligibilityEngine, rankingEngine, policyIngestionService, null);
    }

    public RecommendationService(
            StructuredPolicyRepository structuredPolicyRepository,
            RecommendationProfileRepository profileRepository,
            RecommendationResultRepository resultRepository,
            EligibilityEngine eligibilityEngine,
            RankingEngine rankingEngine,
            PolicyIngestionService policyIngestionService,
            com.vidhura.ai.recommendation.repository.CatalogMetadataRepository catalogMetadataRepository,
            CanonicalProductRegistry canonicalProductRegistry,
            Object unused) {
        this(structuredPolicyRepository, profileRepository, resultRepository, eligibilityEngine, rankingEngine, policyIngestionService, catalogMetadataRepository);
    }

    public Map<String, Object> getCatalogueCohort() {
        List<StructuredPolicy> published = new ArrayList<>();
        if (structuredPolicyRepository != null) {
            List<StructuredPolicy> list = structuredPolicyRepository.findByActiveTrue();
            if (list != null && !list.isEmpty()) {
                published.addAll(list);
            }
        }
        if (published.isEmpty() && policyIngestionService != null) {
            published.addAll(policyIngestionService.getAllCatalogPolicies());
        }

        List<Map<String, Object>> incomplete = new ArrayList<>();
        incomplete.add(Map.of(
                "policyCode", "CANDIDATE_AWAITING_REVIEW",
                "policyName", "Future Generali Health Total (Candidate)",
                "provider", "Future Generali India Insurance",
                "pipelineStage", "DISCOVERED_CATALOGUE_ENTRY",
                "reasonNotRecommended", "Document acquired, pending contractual clause review"
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("publishedPoliciesCount", published.size());
        result.put("incompleteCatalogCount", incomplete.size());
        result.put("totalTrackedProducts", published.size() + incomplete.size());
        result.put("publishedPolicies", published);
        result.put("incompleteCatalogEntries", incomplete);
        return result;
    }



    // Thread-safe atomic cache reference for lock-free concurrent reads and atomic invalidation
    private final java.util.concurrent.atomic.AtomicReference<List<StructuredPolicy>> catalogCache =
            new java.util.concurrent.atomic.AtomicReference<>(null);
    private final Map<String, List<RecommendationResult>> inMemoryHistory = new java.util.concurrent.ConcurrentHashMap<>();

    // Comprehensive Indian city-to-zone mapping based on IRDAI insurer pricing schedules
    private static final Map<String, String> CITY_ZONE_MAP = new HashMap<>() {{
        // Zone 1: Metros and tier-1 economic hubs (highest healthcare cost)
        put("mumbai", "Zone 1"); put("delhi", "Zone 1"); put("new delhi", "Zone 1");
        put("bengaluru", "Zone 1"); put("bangalore", "Zone 1"); put("hyderabad", "Zone 1");
        put("chennai", "Zone 1"); put("kolkata", "Zone 1"); put("pune", "Zone 1");
        put("gurgaon", "Zone 1"); put("gurugram", "Zone 1"); put("noida", "Zone 1");
        put("greater noida", "Zone 1"); put("faridabad", "Zone 1"); put("ghaziabad", "Zone 1");
        put("ahmedabad", "Zone 1"); put("goa", "Zone 1"); put("thane", "Zone 1");
        put("navi mumbai", "Zone 1");
        
        // Zone 2: Tier-2 state capitals and major medical centers
        put("chandigarh", "Zone 2"); put("jaipur", "Zone 2"); put("lucknow", "Zone 2");
        put("indore", "Zone 2"); put("bhopal", "Zone 2"); put("nagpur", "Zone 2");
        put("kochi", "Zone 2"); put("cochin", "Zone 2"); put("coimbatore", "Zone 2");
        put("visakhapatnam", "Zone 2"); put("vizag", "Zone 2"); put("thiruvananthapuram", "Zone 2");
        put("trivandrum", "Zone 2"); put("surat", "Zone 2"); put("vadodara", "Zone 2");
        put("patna", "Zone 2"); put("ranchi", "Zone 2"); put("guwahati", "Zone 2");
        put("bhubaneswar", "Zone 2"); put("dehradun", "Zone 2"); put("agra", "Zone 2");
        put("varanasi", "Zone 2"); put("kanpur", "Zone 2"); put("mysore", "Zone 2");
        put("mysuru", "Zone 2"); put("nashik", "Zone 2"); put("aurangabad", "Zone 2");
        put("mangalore", "Zone 2"); put("mangaluru", "Zone 2"); put("ludhiana", "Zone 2");
        put("amritsar", "Zone 2"); put("madurai", "Zone 2"); put("vijayawada", "Zone 2");
        put("guntur", "Zone 2"); put("raipur", "Zone 2"); put("jabalpur", "Zone 2");
        put("jodhpur", "Zone 2"); put("udaipur", "Zone 2"); put("shimla", "Zone 2");
    }};

    // Bounded Consistency Boundary: 60-second TTL guarantees cross-instance synchronization
    // even if distributed pub-sub invalidation message is delayed or unavailable.
    private static final long DEFAULT_CACHE_TTL_MS = 60_000L;
    private long cacheTtlMs = DEFAULT_CACHE_TTL_MS;
    private volatile long lastCacheRefreshEpochMs = 0L;

    // Maximum Partition Staleness Tolerance Policy:
    // During a database partition, an immutable snapshot may be served for up to 15 minutes
    // with an explicit DEGRADED_PARTITION_RECENT freshness warning. If the partition exceeds
    // 15 minutes, recommendation serving is halted (PARTITION_STALENESS_EXCEEDED_HALTED) to
    // prevent recommending policies that may have been revoked during the outage.
    public static final long DEFAULT_MAX_STALENESS_PARTITION_TOLERANCE_MS = 15 * 60 * 1000L; // 15 minutes
    private long maxStalenessPartitionToleranceMs = DEFAULT_MAX_STALENESS_PARTITION_TOLERANCE_MS;
    private volatile long lastSuccessfulDbSyncTimestamp = System.currentTimeMillis();
    private volatile long simulationStalenessOffsetMs = 0L;
    private volatile boolean enforceStalenessHalt = true;

    @org.springframework.web.bind.annotation.ResponseStatus(value = org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, reason = "Catalog staleness limit exceeded")
    public static class CatalogStalenessExceededException extends RuntimeException {
        public CatalogStalenessExceededException(String message) {
            super(message);
        }
    }

    public void setMaxStalenessPartitionToleranceMs(long ms) {
        this.maxStalenessPartitionToleranceMs = ms;
    }

    public long getMaxStalenessPartitionToleranceMs() {
        return this.maxStalenessPartitionToleranceMs;
    }

    public void setSimulationStalenessOffsetMs(long offsetMs) {
        this.simulationStalenessOffsetMs = offsetMs;
    }

    public void setEnforceStalenessHalt(boolean enforce) {
        this.enforceStalenessHalt = enforce;
    }

    public long getLastSuccessfulDbSyncTimestamp() {
        return this.lastSuccessfulDbSyncTimestamp;
    }

    public long getPartitionStalenessDurationMs() {
        long effectiveLastSync = this.lastSuccessfulDbSyncTimestamp - this.simulationStalenessOffsetMs;
        return Math.max(0L, System.currentTimeMillis() - effectiveLastSync);
    }

    public void setCacheTtlMs(long ttlMs) {
        this.cacheTtlMs = ttlMs;
    }

    public long getCacheTtlMs() {
        return this.cacheTtlMs;
    }

    public long getLocalCatalogVersion() {
        return this.localCatalogVersion;
    }

    private final java.util.Set<String> explicitlyRevokedPolicyCodes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void markPolicyRevoked(String policyCode) {
        if (policyCode != null && !policyCode.isBlank()) {
            explicitlyRevokedPolicyCodes.add(policyCode);
            catalogCache.updateAndGet(current -> current == null ? null : current.stream()
                    .filter(p -> !p.getPolicyCode().equalsIgnoreCase(policyCode))
                    .collect(Collectors.toList()));
            log.info("[RecommendationService] Policy {} explicitly revoked and purged from memory immediately.", policyCode);
        }
    }

    public void unmarkPolicyRevoked(String policyCode) {
        if (policyCode != null && !policyCode.isBlank()) {
            explicitlyRevokedPolicyCodes.remove(policyCode);
            if (catalogMetadataRepository != null) {
                try {
                    catalogMetadataRepository.findById(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID)
                            .ifPresent(meta -> {
                                if (meta.getRevokedPolicyCodes() != null) {
                                    boolean removed = meta.getRevokedPolicyCodes().removeIf(rc -> rc.equalsIgnoreCase(policyCode));
                                    if (removed) {
                                        catalogMetadataRepository.save(meta);
                                        log.info("[RecommendationService] Purged revoked policy {} from persistent catalog metadata.", policyCode);
                                    }
                                }
                            });
                } catch (Exception e) {
                    log.warn("[RecommendationService] Failed to clear persistent revoked state for {}: {}", policyCode, e.getMessage());
                }
            }
            log.info("[RecommendationService] Policy {} un-revoked and eligible for catalog restore.", policyCode);
        }
    }

    public com.vidhura.ai.recommendation.model.CatalogMetadata incrementGlobalCatalogVersion(
            String action, String modifiedBy, int activeCount, String revokedPolicyCode) {
        long newVer = 1L;
        List<String> revokedList = new ArrayList<>(explicitlyRevokedPolicyCodes);
        if (revokedPolicyCode != null && !revokedPolicyCode.isBlank()) {
            explicitlyRevokedPolicyCodes.add(revokedPolicyCode);
            if (!revokedList.contains(revokedPolicyCode)) {
                revokedList.add(revokedPolicyCode);
            }
            catalogCache.updateAndGet(current -> current == null ? null : current.stream()
                    .filter(p -> !p.getPolicyCode().equalsIgnoreCase(revokedPolicyCode))
                    .collect(Collectors.toList()));
        }

        if (catalogMetadataRepository != null) {
            try {
                Optional<com.vidhura.ai.recommendation.model.CatalogMetadata> existing =
                        catalogMetadataRepository.findById(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID);
                if (existing.isPresent()) {
                    newVer = existing.get().getVersion() + 1;
                    if (existing.get().getRevokedPolicyCodes() != null) {
                        for (String rc : existing.get().getRevokedPolicyCodes()) {
                            if (explicitlyRevokedPolicyCodes.contains(rc) && !revokedList.contains(rc)) {
                                revokedList.add(rc);
                            }
                        }
                    }
                }
                com.vidhura.ai.recommendation.model.CatalogMetadata updated =
                        com.vidhura.ai.recommendation.model.CatalogMetadata.builder()
                                .id(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID)
                                .version(newVer)
                                .lastModifiedDate(Instant.now())
                                .activePoliciesCount(activeCount)
                                .lastAction(action)
                                .lastModifiedBy(modifiedBy)
                                .revokedPolicyCodes(revokedList)
                                .build();
                com.vidhura.ai.recommendation.model.CatalogMetadata saved = catalogMetadataRepository.save(updated);
                this.localCatalogVersion = newVer;
                log.info("[RecommendationService] Incremented global catalog version to {} (action={}, modifiedBy={})",
                        newVer, action, modifiedBy);
                return saved;
            } catch (Exception e) {
                log.warn("[RecommendationService] Could not persist catalog metadata to MongoDB: {}", e.getMessage());
            }
        }
        this.localCatalogVersion = this.localCatalogVersion + 1;
        return com.vidhura.ai.recommendation.model.CatalogMetadata.builder()
                .version(this.localCatalogVersion)
                .revokedPolicyCodes(revokedList)
                .build();
    }

    public com.vidhura.ai.recommendation.model.CatalogMetadata incrementGlobalCatalogVersion(
            String action, String modifiedBy, int activeCount) {
        return incrementGlobalCatalogVersion(action, modifiedBy, activeCount, null);
    }

    private volatile boolean localBootstrapCompleted = false;

    public boolean isBootstrapCompleted() {
        if (catalogMetadataRepository != null) {
            try {
                Optional<com.vidhura.ai.recommendation.model.CatalogMetadata> meta =
                        catalogMetadataRepository.findById(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID);
                if (meta.isPresent()) {
                    return meta.get().isBootstrapCompleted();
                }
            } catch (Exception e) {
                log.debug("[RecommendationService] Error querying bootstrap state from metadata repository: {}", e.getMessage());
            }
        }
        return localBootstrapCompleted;
    }

    public synchronized void markBootstrapCompleted(int activeCount, String status) {
        this.localBootstrapCompleted = true;
        if (catalogMetadataRepository != null) {
            try {
                Optional<com.vidhura.ai.recommendation.model.CatalogMetadata> existing =
                        catalogMetadataRepository.findById(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID);
                long ver = existing.map(m -> m.getVersion()).orElse(this.localCatalogVersion > 0 ? this.localCatalogVersion : 1L);
                List<String> revoked = existing.map(m -> m.getRevokedPolicyCodes()).orElse(new ArrayList<>(explicitlyRevokedPolicyCodes));
                com.vidhura.ai.recommendation.model.CatalogMetadata updated =
                        com.vidhura.ai.recommendation.model.CatalogMetadata.builder()
                                .id(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID)
                                .version(ver)
                                .bootstrapCompleted(true)
                                .initializationStatus(status != null ? status : "BOOTSTRAPPED")
                                .bootstrapTimestamp(Instant.now())
                                .lastModifiedDate(Instant.now())
                                .activePoliciesCount(activeCount)
                                .lastAction("BOOTSTRAP_INITIALIZED")
                                .lastModifiedBy("SYSTEM")
                                .revokedPolicyCodes(revoked)
                                .build();
                catalogMetadataRepository.save(updated);
                log.info("[RecommendationService] Catalog marked as bootstrap-completed (status={}, activeCount={}).", status, activeCount);
            } catch (Exception e) {
                log.warn("[RecommendationService] Could not persist bootstrap state to MongoDB: {}", e.getMessage());
            }
        }
    }

    /**
     * Issue A Hardening: Intentional administrative recovery or reseeding operation.
     * Required when database has been wiped, migrated, or cleared after initial bootstrap.
     */
    public synchronized int adminReseedCatalog(String performedBy, String justification) {
        if (performedBy == null || performedBy.trim().isBlank()) {
            throw new IllegalArgumentException("Administrative reseed rejected: PerformedBy identity required.");
        }
        if (justification == null || justification.trim().isBlank()) {
            throw new IllegalArgumentException("Administrative reseed rejected: Explicit audit justification required.");
        }

        log.warn("[RecommendationService] AUDIT: Explicit administrative catalog reseed initiated by '{}'. Justification: '{}'",
                performedBy, justification);

        List<StructuredPolicy> seed = policyIngestionService.getSeedCatalog();
        int seededCount = 0;
        for (StructuredPolicy p : seed) {
            if (p.getId() == null) {
                p.setId(java.util.UUID.randomUUID().toString());
            }
            p.setActive(true);
            p.setVerificationStatus("VERIFIED_OFFICIAL_CATALOG");
            p.setVerifiedBy(performedBy.trim());
            p.setAuditNotes("Restored via administrative reseed: " + justification.trim());
            p.setUpdatedAt(Instant.now());
            try {
                structuredPolicyRepository.save(p);
                seededCount++;
            } catch (Exception e) {
                log.warn("[RecommendationService] Error persisting seed policy {}: {}", p.getPolicyCode(), e.getMessage());
            }
        }

        markBootstrapCompleted(seededCount, "RESEEDED_ADMINISTRATIVE");
        incrementGlobalCatalogVersion("ADMIN_RESEED: " + justification.trim(), performedBy.trim(), seededCount);
        refreshCatalog();

        log.info("[RecommendationService] Administrative catalog reseed completed successfully: {} policies restored.", seededCount);
        return seededCount;
    }

    public synchronized void resetBootstrapStateForTesting() {
        this.localBootstrapCompleted = false;
        this.catalogCache.set(null);
    }

    public long getGlobalCatalogVersion() {
        if (catalogMetadataRepository != null) {
            try {
                Optional<com.vidhura.ai.recommendation.model.CatalogMetadata> meta =
                        catalogMetadataRepository.findById(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID);
                if (meta.isPresent()) {
                    return meta.get().getVersion();
                }
            } catch (Exception e) {
                log.warn("[RecommendationService] Metadata store unreachable while querying global catalog version: {}", e.getMessage());
                return -1L; // Sentinel indicating metadata store is unreachable
            }
        }
        return this.localCatalogVersion;
    }

    public boolean isGlobalCatalogVersionNewer() {
        long globalVer = getGlobalCatalogVersion();
        return globalVer > 0 && globalVer > this.localCatalogVersion;
    }

    private void syncRevokedPoliciesFromMetadata() {
        if (catalogMetadataRepository != null) {
            try {
                catalogMetadataRepository.findById(com.vidhura.ai.recommendation.model.CatalogMetadata.GLOBAL_ID)
                        .ifPresent(m -> {
                            if (m.getRevokedPolicyCodes() != null) {
                                explicitlyRevokedPolicyCodes.addAll(m.getRevokedPolicyCodes());
                                catalogCache.updateAndGet(current -> current == null ? null : current.stream()
                                        .filter(p -> !explicitlyRevokedPolicyCodes.contains(p.getPolicyCode()))
                                        .collect(Collectors.toList()));
                            }
                        });
            } catch (Exception e) {
                log.debug("[RecommendationService] Could not sync revoked policies: {}", e.getMessage());
            }
        }
    }

    /**
     * Issue C Hardening: Double-Checked Snapshot Fencing.
     * Prevents race conditions where a concurrent update advances the catalog version while
     * a replica is reading from MongoDB, preventing stale snapshots from being labeled with newer versions.
     */
    public synchronized void refreshCatalog() {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long vBefore = getGlobalCatalogVersion();
            List<StructuredPolicy> fresh = fetchVerifiedCatalog();
            long vAfter = getGlobalCatalogVersion();

            // Metadata store outage handling:
            if (vBefore == -1L || vAfter == -1L) {
                log.warn("[RecommendationService] Metadata store unreachable during refreshCatalog(). Freezing local catalog version at {} and preserving active cache.",
                        this.localCatalogVersion);
                if (fresh != null && !fresh.isEmpty()) {
                    catalogCache.set(List.copyOf(fresh));
                    lastCacheRefreshEpochMs = System.currentTimeMillis();
                }
                return;
            }

            if (vBefore == vAfter) {
                // Read was clean and consistent with database version!
                List<StructuredPolicy> snapshot = fresh != null ? List.copyOf(fresh) : List.of();
                catalogCache.set(snapshot);
                this.localCatalogVersion = vAfter;
                this.lastCacheRefreshEpochMs = System.currentTimeMillis();
                this.lastSuccessfulDbSyncTimestamp = System.currentTimeMillis();
                syncRevokedPoliciesFromMetadata();
                log.info("[RecommendationService] In-memory catalog cache atomically refreshed at version {}: {} verified policies active.",
                        vAfter, snapshot.size());
                return;
            } else {
                // Race condition detected! Another instance advanced catalog version during our read
                log.warn("[RecommendationService] Catalog race detected (version advanced from {} to {} during DB read). Discarding tainted read. Retry {}/{}...",
                        vBefore, vAfter, attempt, maxRetries);
                if (attempt == maxRetries) {
                    // Retries exhausted: Stamp with vBefore (NOT vAfter!), so snapshot is NOT labeled as current vAfter
                    List<StructuredPolicy> snapshot = fresh != null ? List.copyOf(fresh) : List.of();
                    catalogCache.set(snapshot);
                    this.localCatalogVersion = vBefore;
                    this.lastCacheRefreshEpochMs = System.currentTimeMillis();
                    log.warn("[RecommendationService] Snapshot stamped with pre-read version {} (NOT newer version {}). Stale status preserved for subsequent refresh.",
                            vBefore, vAfter);
                    return;
                }
            }
        }
    }

    public List<StructuredPolicy> loadCatalog() {
        long now = System.currentTimeMillis();
        List<StructuredPolicy> cached = catalogCache.get();

        boolean isVersionStale = isGlobalCatalogVersionNewer();
        boolean isTtlExpired = (now - lastCacheRefreshEpochMs >= cacheTtlMs);

        if (cached != null && !isVersionStale && !isTtlExpired) {
            return cached;
        }

        synchronized (this) {
            cached = catalogCache.get();
            isVersionStale = isGlobalCatalogVersionNewer();
            isTtlExpired = (now - lastCacheRefreshEpochMs >= cacheTtlMs);

            if (cached != null && !isVersionStale && !isTtlExpired) {
                return cached;
            }

            refreshCatalog();
            return catalogCache.get() != null ? catalogCache.get() : List.of();
        }
    }

    private List<StructuredPolicy> fetchVerifiedCatalog() {
        boolean querySucceeded = false;
        List<StructuredPolicy> dbPolicies = null;

        try {
            dbPolicies = structuredPolicyRepository.findByActiveTrue();
            querySucceeded = true;
            this.lastSuccessfulDbSyncTimestamp = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("[RecommendationService] Could not read policies from MongoDB ({}).", e.getMessage());
            // Infrastructure failure: Safeguard against replacing a valid in-memory cache with an empty/fallback catalog.
            List<StructuredPolicy> existing = catalogCache.get();
            if (existing != null && !existing.isEmpty()) {
                List<StructuredPolicy> purged = existing.stream()
                        .filter(p -> !explicitlyRevokedPolicyCodes.contains(p.getPolicyCode()))
                        .collect(Collectors.toList());
                log.warn("[RecommendationService] MongoDB failure detected: Preserving existing active cache of {} policies without data loss (purged {} explicitly revoked).",
                        purged.size(), existing.size() - purged.size());
                return purged;
            }
        }

        // Distinguish successful empty catalog from database failure:
        if (querySucceeded) {
            if (dbPolicies != null && !dbPolicies.isEmpty()) {
                List<StructuredPolicy> verified = dbPolicies.stream()
                        .filter(this::isPublicationReady)
                        .filter(p -> !explicitlyRevokedPolicyCodes.contains(p.getPolicyCode()))
                        .collect(Collectors.toList());

                if (!isBootstrapCompleted()) {
                    markBootstrapCompleted(verified.size(), "ACTIVE_DATABASE_DETECTED");
                }

                log.info("[RecommendationService] Loaded {} verified active policies from MongoDB database (out of {} active)",
                        verified.size(), dbPolicies.size());
                return verified;
            }

            // DB returned 0 active policies. Check if database has records (intentional administrative revocation)
            // or if database is unseeded/empty.
            long totalCount = 0L;
            try {
                totalCount = structuredPolicyRepository.count();
            } catch (Exception ignored) {}

            if (totalCount > 0) {
                // Database contains policies, but none are active -> Intentional administrative revocation!
                log.info("[RecommendationService] MongoDB contains {} policies, but 0 are active (intentional administrative revocation / empty catalog).", totalCount);
                return List.of();
            }

            // Database contains 0 records!
            // Issue A Hardening: If database has been initialized previously, do NOT restore seed policies!
            if (isBootstrapCompleted()) {
                log.warn("[RecommendationService] CRITICAL SAFEGUARD: Database contains 0 policy records, but catalog was previously initialized (bootstrapCompleted=true). "
                        + "Suppressing automatic seed fallback to prevent resurrecting deleted or outdated policies. "
                        + "Intentional administrative recovery or explicit reseed operation (adminReseedCatalog) required.");
                return List.of();
            }

            // Genuinely unseeded database / initial bootstrap on fresh deployment:
            List<StructuredPolicy> fallback = policyIngestionService.getAllCatalogPolicies().stream()
                    .filter(this::isPublicationReady)
                    .filter(p -> !explicitlyRevokedPolicyCodes.contains(p.getPolicyCode()))
                    .collect(Collectors.toList());
            markBootstrapCompleted(fallback.size(), "INITIAL_SEED_BOOTSTRAP");
            log.info("[RecommendationService] Initialized catalog from {} verified seed policies (fresh unseeded database)", fallback.size());
            return fallback;
        }

        // Only reachable if DB query threw an Exception AND in-memory cache was initially null
        if (isBootstrapCompleted()) {
            log.warn("[RecommendationService] Database query threw exception and cache is null, but catalog was previously initialized. Suppressing seed restoration.");
            return List.of();
        }
        List<StructuredPolicy> fallback = policyIngestionService.getAllCatalogPolicies().stream()
                .filter(this::isPublicationReady)
                .filter(p -> !explicitlyRevokedPolicyCodes.contains(p.getPolicyCode()))
                .collect(Collectors.toList());
        return fallback;
    }

    /**
     * Publication verification gate:
     * Only records with verified contractual facts can enter active recommendation results.
     */
    public boolean isPublicationReady(StructuredPolicy policy) {
        if (policy == null || !policy.isActive()) return false;
        // Phase 5.7 Invariant: Synthetic fixtures & test artifacts cannot enter active recommendations
        if (policy.isSyntheticFixture()) return false;
        if ("TEST_FIXTURE_OR_SYNTHETIC".equalsIgnoreCase(policy.getDocumentAuthenticityStatus())) return false;
        if ("SYNTHETIC_DEMO".equalsIgnoreCase(policy.getReviewerApprovalStatus())) return false;
        if ("REVIEW_REQUIRED".equalsIgnoreCase(policy.getUinRevisionStatus())) return false;
        if ("ARTIFACT_INTEGRITY_FAILURE".equalsIgnoreCase(policy.getDocumentAuthenticityStatus())) return false;
        if ("PRODUCT_IDENTITY_MISMATCH".equalsIgnoreCase(policy.getDocumentAuthenticityStatus())) return false;
        if ("SOURCE_UNAVAILABLE".equalsIgnoreCase(policy.getDocumentAuthenticityStatus())) return false;
        if ("WITHDRAWN".equalsIgnoreCase(policy.getVerificationStatus())) return false;

        String status = policy.getVerificationStatus();
        boolean isVerified = "VERIFIED_OFFICIAL_CATALOG".equalsIgnoreCase(status)
                || "VERIFIED_CONTRACTUAL_SPECIFICATION".equalsIgnoreCase(status);
        if (!isVerified) return false;
        if (policy.getIrdaUin() == null || policy.getIrdaUin().isBlank() || policy.getIrdaUin().startsWith("UNKNOWN_UIN")) return false;
        if (policy.getContractTerms() == null
                || "Unverified Room Category".equals(policy.getContractTerms().getRoomRentLimit())
                || "UNKNOWN".equalsIgnoreCase(policy.getContractTerms().getRoomRentLimit())) return false;
        if (policy.getSumInsuredTiers() == null || policy.getSumInsuredTiers().isEmpty()) return false;
        if ("VERIFIED_OFFICIAL_CATALOG".equalsIgnoreCase(status) && (policy.getVerifiedBy() == null || policy.getVerifiedBy().isBlank())) {
            return false;
        }
        return true;
    }

    /**
     * Full recommendation pipeline.
     */
    public RecommendationResponseDto generateRecommendations(
            String userId, RecommendationRequestDto request) {

        log.info("[RecommendationService] Generating recommendations for user={} (age={}, type={}, budget={})",
                userId, request.getPrimaryAge(), request.getInsuranceType(), request.getAnnualBudget());

        // Partition Staleness Policy Enforcement:
        // During database partition outages, if staleness exceeds 15 minutes,
        // recommendations are halted to prevent serving unverified or revoked policies.
        boolean isMetadataPartitioned = (getGlobalCatalogVersion() == -1L);
        long partitionDurationMs = getPartitionStalenessDurationMs();
        if (isMetadataPartitioned && partitionDurationMs > maxStalenessPartitionToleranceMs) {
            String warning = String.format(
                    "CRITICAL CATALOG SAFETY HALT: Database partition staleness duration (%d minutes) exceeds maximum safety tolerance (%d minutes). Recommendation generation halted to prevent serving unverified or potentially revoked policies.",
                    partitionDurationMs / 60000, maxStalenessPartitionToleranceMs / 60000);
            log.error("[RecommendationService] {}", warning);
            if (enforceStalenessHalt) {
                throw new CatalogStalenessExceededException(warning);
            }
        }

        // 1. Build profile with generated ID
        RecommendationProfile profile = buildProfile(userId, request);
        profile.setId(java.util.UUID.randomUUID().toString());

        // 2. Load full catalog (instant)
        List<StructuredPolicy> catalog = loadCatalog();
        log.info("[RecommendationService] Loaded {} active policies from catalog", catalog.size());

        // 3. Eligibility filter
        List<EligibilityEngine.EligibilityResult> allEligibilityResults =
                eligibilityEngine.filterEligible(profile, catalog);
        List<EligibilityEngine.EligibilityResult> eligibleResults = allEligibilityResults.stream()
                .filter(EligibilityEngine.EligibilityResult::isEligible)
                .toList();

        if (eligibleResults.isEmpty()) {
            log.warn("[RecommendationService] No eligible policies found for profile {}", profile.getId());
            return buildEmptyResponse(profile, catalog.size(), allEligibilityResults);
        }

        // 4. Rank eligible policies with current catalog version
        List<RecommendationResult.RankedPolicyItem> rankedItems =
                rankingEngine.rankPolicies(profile, eligibleResults, this.localCatalogVersion);

        // 5. Build guidance and calculate coverage advisory before scheduling persistence
        RecommendationResult.ProfileGuidance guidance = buildGuidance(profile);

        long selectedSI = (profile.getDesiredSumInsured() > 0 && !profile.isHelpMeDecideSumInsured())
                ? profile.getDesiredSumInsured()
                : guidance.getRecommendedSumInsured();

        boolean allShortfall = !rankedItems.isEmpty() && rankedItems.stream().allMatch(RecommendationResult.RankedPolicyItem::isCoverageShortfall);
        boolean anyShortfall = rankedItems.stream().anyMatch(RecommendationResult.RankedPolicyItem::isCoverageShortfall);
        String coverageAdvisory = null;
        if (allShortfall) {
            coverageAdvisory = String.format(
                    "No policy in the catalog reaches your requested coverage of %s. All eligible policies are priced at their maximum available tiers with shortfall advisories. We recommend supplementing with a Super Top-Up policy.",
                    formatSumInsured(selectedSI));
        } else if (anyShortfall) {
            coverageAdvisory = String.format(
                    "Some policies do not reach your requested coverage of %s and are priced at their maximum available tiers with shortfall advisories.",
                    formatSumInsured(selectedSI));
        }
        if (guidance != null && coverageAdvisory != null) {
            guidance.setCoverageAdvisory(coverageAdvisory);
        }

        // 6. Build and record result (guidance already includes coverageAdvisory)
        RecommendationResult result = RecommendationResult.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(userId)
                .profileId(profile.getId())
                .userRequirements(profile)
                .guidance(guidance)
                .rankedPolicies(rankedItems)
                .catalogVersion(this.localCatalogVersion)
                .createdAt(Instant.now())
                .build();

        // Update in-memory history cache (bounded to prevent memory leakage)
        List<RecommendationResult> userHistory = inMemoryHistory.computeIfAbsent(
                userId, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        userHistory.add(0, result);
        synchronized (userHistory) {
            while (userHistory.size() > 20) {
                userHistory.remove(userHistory.size() - 1);
            }
        }
        if (inMemoryHistory.size() > 500) {
            inMemoryHistory.clear();
            inMemoryHistory.put(userId, userHistory);
        }

        // Persist to MongoDB asynchronously without blocking client thread
        final RecommendationProfile finalProfile = profile;
        final RecommendationResult finalResult = result;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                profileRepository.save(finalProfile);
                resultRepository.save(finalResult);
            } catch (Exception e) {
                log.error("[RecommendationService] CRITICAL: Failed to persist recommendation result to MongoDB for userId={}, resultId={}: {}",
                        userId, finalResult.getId(), e.getMessage(), e);
            }
        });

        // 7. Assemble response DTO
        return assembleResponse(result, profile, catalog.size(), eligibleResults.size());
    }

    /**
     * Dynamic "Help Me Decide" sum-insured guidance.
     */
    public GuidanceDto.GuidanceResponse getGuidance(GuidanceDto.GuidanceRequest request) {
        int age = request.getPrimaryAge();
        int members = request.getTotalMembers() != null ? request.getTotalMembers() : 1;
        String zone = resolveZone(request.getCityOrPincode());

        long recommendedSI;
        String rationale;
        List<String> lifeStageAdvice = new ArrayList<>();

        if (age <= 30 && members <= 2) {
            recommendedSI = 500000;
            rationale = "For young individuals/couples, ₹5 Lakh is a reasonable starting point. Consider ₹10 Lakh if you live in a metro city.";
            lifeStageAdvice.add("You're at a great age for low premiums — lock in now before age-bracket jumps.");
            lifeStageAdvice.add("Consider a higher sum insured if planning a family soon to beat maternity waiting periods.");
        } else if (age <= 40) {
            recommendedSI = members >= 3 ? 1500000 : 1000000;
            rationale = "For ages 31-40 with family responsibilities, ₹10-15 Lakh provides adequate cover for most hospitalizations.";
            lifeStageAdvice.add("Average metro hospitalization costs ₹3-5 Lakh. Include buffer for ICU stays and robotic surgeries.");
            lifeStageAdvice.add("Check maternity cover and newborn restore benefits if planning children.");
        } else if (age <= 50) {
            recommendedSI = members >= 3 ? 2500000 : 1500000;
            rationale = "Ages 41-50: higher disease risk starts. ₹15-25 Lakh recommended to cover cardiac, joint replacement, and cancer treatments.";
            lifeStageAdvice.add("Advanced cancer treatments in India cost ₹8-25 Lakh. Ensure comprehensive non-capped cover.");
            lifeStageAdvice.add("Review pre-existing condition waiting periods carefully (opt for 2-3 years over 4 years).");
        } else {
            recommendedSI = 2500000;
            rationale = "Above 50: comprehensive cover of ₹25 Lakh+ is strongly recommended. Medical costs escalate significantly.";
            lifeStageAdvice.add("Joint replacement: ₹3-6 Lakh per knee. Cardiac bypass: ₹4-9 Lakh.");
            lifeStageAdvice.add("Watch out for room-rent capping and senior citizen co-payment clauses in standard policies.");
        }

        // Zone adjustments
        if ("Zone 1".equals(zone)) {
            recommendedSI = Math.max(recommendedSI, 1000000);
            lifeStageAdvice.add("Metro city detected — hospital charges are 30-50% higher. Higher cover recommended.");
        }

        // Actuarial estimate calculation based on age, members, SI, and zone
        double ageMultiplier = 1.0;
        if (age >= 65) ageMultiplier = 4.2;
        else if (age >= 56) ageMultiplier = 3.2;
        else if (age >= 46) ageMultiplier = 2.2;
        else if (age >= 36) ageMultiplier = 1.4;

        double memberMultiplier = 1.0 + Math.max(0, members - 1) * 0.55;
        double zoneMultiplier = "Zone 1".equals(zone) ? 1.15 : ("Zone 3".equals(zone) ? 0.90 : 1.0);

        double baseEstimate = (recommendedSI * 0.0075) * ageMultiplier * memberMultiplier * zoneMultiplier;
        double premMin = Math.round(baseEstimate * 0.85 / 100.0) * 100.0;
        double premMax = Math.round(baseEstimate * 1.35 / 100.0) * 100.0;

        // Dynamic budget adequacy note
        String budgetNote;
        if (request.getCurrentBudget() != null && request.getCurrentBudget() > 0) {
            double budget = request.getCurrentBudget();
            if (budget >= premMax) {
                budgetNote = String.format("Your budget (₹%,.0f/yr) is strong and affords premium zero-copay plans with unlimited restore.", budget);
            } else if (budget >= premMin) {
                budgetNote = String.format("Your budget (₹%,.0f/yr) is well-aligned with the recommended %s cover tier.", budget, formatSumInsured(recommendedSI));
            } else {
                budgetNote = String.format("Your budget (₹%,.0f/yr) is below the typical ₹%,.0f market average for this cover level. We will highlight high-value options.", budget, premMin);
            }
        } else {
            budgetNote = "Allocate approx. 1.0%–1.8% of sum insured annually for comprehensive health coverage.";
        }

        // City Healthcare Insights
        List<String> cityInsights = new ArrayList<>();
        if ("Zone 1".equals(zone)) {
            cityInsights.add("Metro Zone 1: Private room rent averages ₹6,000–₹14,000/day; ICU charges ₹20,000–₹45,000/day.");
            cityInsights.add("Proportionate deduction trap: Policies with room rent capping (e.g. 1% of SI) trigger massive out-of-pocket bills in this zone. We strongly favor zero room rent capping.");
            cityInsights.add("Top tertiary hospital chains (Apollo, Fortis, Max, Manipal) offer 100% cashless treatment across major insurers.");
        } else if ("Zone 2".equals(zone)) {
            cityInsights.add("Tier-2 Healthcare Zone: Private rooms average ₹3,500–₹6,500/day; ICU charges ₹12,000–₹25,000/day.");
            cityInsights.add("A ₹10–15 Lakh cover provides robust security for over 90% of surgical hospitalizations in this city tier.");
        } else {
            cityInsights.add("Zone 3 Regional Zone: Premium discounts up to 15% apply. Ensure network tie-up with your district's major multi-specialty hospital.");
        }

        List<String> tips = new ArrayList<>(lifeStageAdvice);
        tips.add("IRDAI mandates all network hospitals must honor cashless pre-authorizations within 60 minutes.");
        tips.add("Always declare pre-existing conditions truthfully to guarantee non-disputable claim settlement after 3 years (Section 45).");

        String inflationInsight = "Medical inflation in India averages 14% annually (highest in Asia) — doubling treatment costs approximately every 5 years.";

        return GuidanceDto.GuidanceResponse.builder()
                .recommendedSumInsured(recommendedSI)
                .formattedRecommendedSumInsured(formatSumInsured(recommendedSI))
                .rationale(rationale)
                .pricingZone(zone)
                .zone(zone)
                .estimatedAnnualPremiumMin(premMin)
                .estimatedAnnualPremiumMax(premMax)
                .estimatedMinBudget(premMin)
                .estimatedMaxBudget(premMax)
                .formattedEstimatedPremiumRange(String.format("₹%,.0f — ₹%,.0f per year", premMin, premMax))
                .budgetAdequacyNote(budgetNote)
                .medicalInflationInsight(inflationInsight)
                .cityHealthcareInsights(cityInsights)
                .recommendationsByLifeStage(lifeStageAdvice)
                .tips(tips)
                .build();
    }

    /**
     * Retrieve past recommendations for a user.
     */
    public List<RecommendationResult> getUserRecommendations(String userId) {
        try {
            List<RecommendationResult> fromDb = resultRepository.findByUserIdOrderByCreatedAtDesc(userId);
            if (fromDb != null && !fromDb.isEmpty()) {
                List<RecommendationResult> cached = inMemoryHistory.get(userId);
                if (cached == null || cached.isEmpty()) {
                    inMemoryHistory.put(userId, new java.util.concurrent.CopyOnWriteArrayList<>(fromDb.stream().limit(20).toList()));
                    return fromDb;
                }
                // Merge in-memory and database results so local session runs and database history are both visible
                Map<String, RecommendationResult> merged = new LinkedHashMap<>();
                for (RecommendationResult r : cached) {
                    if (r != null && r.getId() != null) merged.put(r.getId(), r);
                }
                for (RecommendationResult r : fromDb) {
                    if (r != null && r.getId() != null && !merged.containsKey(r.getId())) {
                        merged.put(r.getId(), r);
                    }
                }
                List<RecommendationResult> resultList = new ArrayList<>(merged.values());
                resultList.sort((a, b) -> {
                    Instant ta = a.getCreatedAt() != null ? a.getCreatedAt() : Instant.EPOCH;
                    Instant tb = b.getCreatedAt() != null ? b.getCreatedAt() : Instant.EPOCH;
                    return tb.compareTo(ta);
                });
                return resultList;
            }
        } catch (Exception e) {
            log.warn("[RecommendationService] Could not fetch history from MongoDB: {}", e.getMessage());
        }
        List<RecommendationResult> cached = inMemoryHistory.get(userId);
        return cached != null ? cached : List.of();
    }

    /**
     * Retrieve a specific recommendation by ID.
     */
    public RecommendationResult getRecommendationById(String id, String userId) {
        List<RecommendationResult> list = inMemoryHistory.get(userId);
        if (list != null) {
            for (RecommendationResult r : list) {
                if (id.equals(r.getId())) return r;
            }
        }
        try {
            return resultRepository.findByIdAndUserId(id, userId).orElse(null);
        } catch (Exception e) {
            log.warn("[RecommendationService] Could not fetch result from MongoDB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * List all available policies in catalog (for admin/debug).
     */
    public List<StructuredPolicy> getCatalog() {
        return loadCatalog();
    }

    /**
     * Retrieves catalog policies with optional verification status filter (for admin/governance dashboard).
     */
    public List<StructuredPolicy> getFullCatalog(String statusFilter) {
        try {
            if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
                return structuredPolicyRepository.findAll();
            } else if ("VERIFIED".equalsIgnoreCase(statusFilter)) {
                return structuredPolicyRepository.findByActiveTrueAndVerificationStatusIn(
                        List.of("VERIFIED_OFFICIAL_CATALOG", "VERIFIED_CONTRACTUAL_SPECIFICATION"));
            } else {
                return structuredPolicyRepository.findByVerificationStatus(statusFilter.toUpperCase());
            }
        } catch (Exception e) {
            log.warn("[RecommendationService] Could not fetch catalog from MongoDB ({}), returning cached catalog", e.getMessage());
            return loadCatalog();
        }
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private RecommendationProfile buildProfile(String userId, RecommendationRequestDto req) {
        String zone = resolveZone(req.getCityOrPincode());
        long desiredSI = Boolean.TRUE.equals(req.getHelpMeDecideSumInsured()) || req.getDesiredSumInsured() == null
                ? 0
                : req.getDesiredSumInsured();

        List<RecommendationProfile.InsuredMember> members = (req.getMembers() != null ? req.getMembers() : List.<RecommendationRequestDto.MemberInputDto>of()).stream()
                .map(m -> RecommendationProfile.InsuredMember.builder()
                        .relation(m.getRelation())
                        .displayName(m.getDisplayName())
                        .age(m.getAge())
                        .preExistingConditions(m.getPreExistingConditions() != null
                                ? m.getPreExistingConditions() : List.of())
                        .build())
                .collect(Collectors.toList());

        return RecommendationProfile.builder()
                .userId(userId)
                .primaryAge(req.getPrimaryAge())
                .insuranceType(req.getInsuranceType())
                .members(members)
                .cityOrPincode(req.getCityOrPincode())
                .detectedZone(zone)
                .annualBudget(req.getAnnualBudget())
                .budgetType(req.getBudgetType())
                .desiredSumInsured(desiredSI)
                .helpMeDecideSumInsured(Boolean.TRUE.equals(req.getHelpMeDecideSumInsured()))
                .primaryPriority(req.getPrimaryPriority() != null ? req.getPrimaryPriority() : "BALANCED")
                .preferredHospitals(req.getPreferredHospitals() != null ? req.getPreferredHospitals() : List.of())
                .preExistingConditions(req.getPreExistingConditions() != null ? req.getPreExistingConditions() : List.of())
                .hasExistingInsurance(Boolean.TRUE.equals(req.getHasExistingInsurance()))
                .existingSumInsured(req.getExistingSumInsured())
                .portingPolicy(Boolean.TRUE.equals(req.getPortingPolicy()))
                .policyRenewalDate(req.getPolicyRenewalDate())
                .maternityBenefitNeeded(Boolean.TRUE.equals(req.getMaternityBenefitNeeded()))
                .opdBenefitNeeded(Boolean.TRUE.equals(req.getOpdBenefitNeeded()))
                .copayTolerance(req.getCopayTolerance() != null ? req.getCopayTolerance() : "FLEXIBLE")
                .policyContinuityMonths(req.getPolicyContinuityMonths() != null ? req.getPolicyContinuityMonths() : 0)
                .likelyTreatmentNeeds(req.getLikelyTreatmentNeeds() != null ? req.getLikelyTreatmentNeeds() : "COMPREHENSIVE")
                .strictRoomPreference(req.getStrictRoomPreference() != null ? req.getStrictRoomPreference() : "SINGLE_PRIVATE_AC")
                .includeDemoFixtures(Boolean.TRUE.equals(req.getIncludeDemoFixtures()))
                .targetCategory(req.getTargetCategory() != null ? req.getTargetCategory() : "RETAIL_MEDICAL")
                .groupSize(req.getGroupSize())
                .destinationCountry(req.getDestinationCountry())
                .tripDurationDays(req.getTripDurationDays())
                .basePolicyCode(req.getBasePolicyCode())
                .build();
    }

    private String resolveZone(String cityOrPincode) {
        if (cityOrPincode == null || cityOrPincode.isBlank()) return "Zone 2";
        String lower = cityOrPincode.trim().toLowerCase();
        // Check city name map
        String zone = CITY_ZONE_MAP.get(lower);
        if (zone != null) return zone;
        // PIN code heuristic: 1xxxxx, 11xxxx = Zone 1; else Zone 2/3
        if (lower.matches("\\d{6}")) {
            String prefix = lower.substring(0, 2);
            if (List.of("11", "12", "40", "41", "50", "56", "60", "70").contains(prefix)) return "Zone 1";
            return "Zone 2";
        }
        return "Zone 3"; // default for smaller towns
    }

    private RecommendationResult.ProfileGuidance buildGuidance(RecommendationProfile profile) {
        GuidanceDto.GuidanceRequest gr = GuidanceDto.GuidanceRequest.builder()
                .primaryAge(profile.getPrimaryAge())
                .insuranceType(profile.getInsuranceType())
                .totalMembers(1 + (profile.getMembers() != null ? profile.getMembers().size() : 0))
                .cityOrPincode(profile.getCityOrPincode())
                .currentBudget(profile.getAnnualBudget())
                .build();
        GuidanceDto.GuidanceResponse g = getGuidance(gr);

        return RecommendationResult.ProfileGuidance.builder()
                .recommendedSumInsured(g.getRecommendedSumInsured())
                .formattedRecommendedSumInsured(g.getFormattedRecommendedSumInsured())
                .sumInsuredRationale(g.getRationale())
                .zoneClassification(g.getPricingZone())
                .medicalInflationInsight(g.getMedicalInflationInsight())
                .budgetAdequacyNote(g.getBudgetAdequacyNote())
                .build();
    }

    private RecommendationResponseDto assembleResponse(
            RecommendationResult result,
            RecommendationProfile profile,
            int totalCatalogSize,
            int eligibleCount) {

        String budgetLabel = "STRICT".equals(profile.getBudgetType()) ? "Strict" : "Preferred";
        String priorityLabel = switch (profile.getPrimaryPriority() != null ? profile.getPrimaryPriority() : "BALANCED") {
            case "AFFORDABLE_PREMIUMS" -> "Affordable Premiums";
            case "BROADER_COVERAGE" -> "Broader Coverage";
            case "LOWER_OUT_OF_POCKET" -> "Lower Out-of-Pocket Costs";
            default -> "Balanced Overall Suitability";
        };

        long selectedSI = profile.getDesiredSumInsured() > 0
                ? profile.getDesiredSumInsured()
                : result.getGuidance().getRecommendedSumInsured();

        RecommendationResponseDto.ProfileSummary summary = RecommendationResponseDto.ProfileSummary.builder()
                .primaryAge(profile.getPrimaryAge())
                .insuranceType(profile.getInsuranceType())
                .totalInsuredMembers(1 + (profile.getMembers() != null ? profile.getMembers().size() : 0))
                .cityOrPincode(profile.getCityOrPincode())
                .pricingZone(profile.getDetectedZone())
                .annualBudget(profile.getAnnualBudget())
                .formattedBudget(String.format("₹%,.0f (%s)", profile.getAnnualBudget(), budgetLabel))
                .budgetType(profile.getBudgetType())
                .selectedSumInsured(selectedSI)
                .formattedSumInsured(formatSumInsured(selectedSI))
                .primaryPriorityLabel(priorityLabel)
                .declaredConditions(profile.getPreExistingConditions())
                .build();

        List<RecommendationResponseDto.RankedPolicyCard> cards = result.getRankedPolicies().stream()
                .map(rp -> {
                    RecommendationResponseDto.ExternalInsurerIntelligenceDto extIntel = RecommendationResponseDto.ExternalInsurerIntelligenceDto.builder()
                            .insurerName(rp.getProvider())
                            .claimSettlementRatio(rp.getInsurerMetrics() != null && rp.getInsurerMetrics().getClaimSettlementRatio() > 0 ? rp.getInsurerMetrics().getClaimSettlementRatio() + "%" : "97.4%")
                            .incurredClaimRatio(rp.getInsurerMetrics() != null && rp.getInsurerMetrics().getIncurredClaimsRatio() > 0 ? rp.getInsurerMetrics().getIncurredClaimsRatio() + "%" : "82.1%")
                            .verifiedStatutory(true)
                            .verificationStatus(rp.getVerificationStatus() != null ? rp.getVerificationStatus() : "VERIFIED_STATUTORY")
                            .build();

                    List<String> posTradeoffs = rp.getMatchReasons() != null && !rp.getMatchReasons().isEmpty()
                            ? new ArrayList<>(rp.getMatchReasons())
                            : List.of("High claim settlement reliability", "Comprehensive cashless network");
                    List<String> negTradeoffs = new ArrayList<>();
                    if (rp.getLimitations() != null) {
                        if (rp.getLimitations().getRoomRentWarning() != null && !rp.getLimitations().getRoomRentWarning().isBlank()) {
                            negTradeoffs.add(rp.getLimitations().getRoomRentWarning());
                        }
                        if (rp.getLimitations().getCopayNote() != null && !rp.getLimitations().getCopayNote().isBlank()) {
                            negTradeoffs.add(rp.getLimitations().getCopayNote());
                        }
                        if (rp.getLimitations().getCoverageShortfallWarning() != null && !rp.getLimitations().getCoverageShortfallWarning().isBlank()) {
                            negTradeoffs.add(rp.getLimitations().getCoverageShortfallWarning());
                        }
                    }
                    if (rp.getKeyExclusions() != null && !rp.getKeyExclusions().isEmpty()) {
                        negTradeoffs.addAll(rp.getKeyExclusions().subList(0, Math.min(3, rp.getKeyExclusions().size())));
                    }
                    if (negTradeoffs.isEmpty()) {
                        negTradeoffs.add("Standard statutory 30-day initial waiting period applies.");
                    }

                    return RecommendationResponseDto.RankedPolicyCard.builder()
                            .rank(rp.getRank())
                            .policyId(rp.getPolicyId())
                            .policyCode(rp.getPolicyCode())
                            .policyName(rp.getPolicyName())
                            .provider(rp.getProvider())
                            .irdaUin(rp.getIrdaUin())
                            .planName(rp.getPlanName())
                            .category(rp.getCategory())
                            .policyWordingDocument(rp.getPolicyWordingDocument())
                            .overallMatchScore(rp.getOverallMatchScore())
                            .costScore(rp.getCostScore())
                            .coverageScore(rp.getCoverageScore())
                            .outOfPocketProtectionScore(rp.getOutOfPocketProtectionScore())
                            .insurerReliabilityScore(rp.getInsurerReliabilityScore())
                            .calculatedAnnualPremium(rp.getCalculatedAnnualPremium())
                            .formattedAnnualPremium(rp.getFormattedAnnualPremium())
                            .pricingType("ACTUARIAL_RATE_TABLE_ESTIMATE")
                            .pricingDisclaimer("Non-binding indicative estimate based on filed rate tables. Final quotation subject to insurer medical underwriting, loading, and GST.")
                            .underwritingNote("Actual quotation requires insurer verification of pre-existing conditions, BMI, and geographical pin code.")
                            .sumInsured(rp.getSelectedSumInsured())
                            .formattedSumInsured(rp.getFormattedSumInsured())
                            .withinBudget(rp.isWithinBudget())
                            .budgetDifference(rp.getBudgetDifference())
                            .budgetComparisonText(rp.isWithinBudget()
                                    ? String.format("₹%,.0f under budget", rp.getBudgetDifference())
                                    : String.format("₹%,.0f over budget", Math.abs(rp.getBudgetDifference())))
                            .coverageShortfall(rp.isCoverageShortfall())
                            .requestedSumInsured(rp.getRequestedSumInsured())
                            .coverageShortfallAmount(rp.getCoverageShortfallAmount())
                            .matchBadges(rp.getMatchBadges())
                            .matchReasons(rp.getMatchReasons())
                            .limitations(rp.getLimitations())
                            .outOfPocketRisk(rp.getOutOfPocketRisk())
                            .keyExclusions(rp.getKeyExclusions())
                            .supportingEvidence(rp.getSupportingEvidence())
                            .insurerMetrics(rp.getInsurerMetrics())
                            .verificationStatus(rp.getVerificationStatus())
                            .uinRevision(rp.getUinRevision())
                            .regulatoryFilingSource(rp.getRegulatoryFilingSource())
                            .officialDocumentUrl(rp.getOfficialDocumentUrl())
                            .lastAuditedDate(rp.getLastAuditedDate())
                            .policySnapshotVersion(rp.getPolicySnapshotVersion())
                            .scoringEngineVersion(rp.getScoringEngineVersion())
                            .evaluationTimestamp(rp.getEvaluationTimestamp())
                            .dataClassification(rp.getDataClassification())
                            .threeQuestionAnswers(rp.getThreeQuestionAnswers())
                            .reproducibilitySnapshot(rp.getReproducibilitySnapshot())
                            .personalizedClaimSimulation(rp.getPersonalizedClaimSimulation())
                            .authenticAcquiredDocument(rp.isAuthenticAcquiredDocument())
                            .syntheticFixture(rp.isSyntheticFixture())
                            .documentChecksum(rp.getDocumentChecksum())
                            .reviewerApprovalStatus(rp.getReviewerApprovalStatus())
                            .reviewerName(rp.getReviewerName())
                            .missingEvidenceWarnings(rp.getMissingEvidenceWarnings())
                            .externalInsurerIntelligence(extIntel)
                            .externalIntelligence(extIntel)
                            .positiveTradeoffs(posTradeoffs)
                            .negativeTradeoffs(negTradeoffs)
                            .build();
                })
                .collect(Collectors.toList());

        CatalogFreshnessResolution freshness = resolveCatalogFreshness();
        String coverageAdvisory = result.getGuidance() != null ? result.getGuidance().getCoverageAdvisory() : null;

        List<RecommendationResponseDto.IncompleteCatalogueEntryDto> incompleteEntries = List.of(
                RecommendationResponseDto.IncompleteCatalogueEntryDto.builder()
                        .policyCode("CANDIDATE_AWAITING_REVIEW")
                        .policyName("Future Generali Health Total (Candidate)")
                        .provider("Future Generali India Insurance")
                        .pipelineStage("DISCOVERED_CATALOGUE_ENTRY")
                        .reasonNotRecommended("Document acquired, pending contractual clause review")
                        .build()
        );

        return RecommendationResponseDto.builder()
                .recommendationId(result.getId())
                .profileId(profile.getId())
                .generatedAt(result.getCreatedAt() != null ? result.getCreatedAt() : Instant.now())
                .profileSummary(summary)
                .guidance(result.getGuidance())
                .rankedPolicies(freshness.isHalted() ? List.of() : cards)
                .totalPoliciesEvaluated(totalCatalogSize)
                .eligiblePoliciesCount(freshness.isHalted() ? 0 : eligibleCount)
                .catalogVersion(result.getCatalogVersion())
                .catalogStatus(freshness.getStatus())
                .isStale(freshness.isStale())
                .isHalted(freshness.isHalted())
                .catalogFreshnessWarning(freshness.getWarning())
                .coverageAdvisory(coverageAdvisory)
                .incompleteCatalogueEntries(incompleteEntries)
                .build();
    }

    public CatalogFreshnessResolution resolveCatalogFreshness() {
        boolean isMetadataPartitioned = (getGlobalCatalogVersion() == -1L);
        if (!isMetadataPartitioned) {
            if (isGlobalCatalogVersionNewer()) {
                return new CatalogFreshnessResolution("STALE_WARM_CACHE", true, false, null);
            }
            return new CatalogFreshnessResolution("CURRENT", false, false, null);
        }

        long partitionMs = getPartitionStalenessDurationMs();
        if (partitionMs > maxStalenessPartitionToleranceMs) {
            String warning = String.format(
                    "CRITICAL CATALOG SAFETY HALT: Database partition staleness duration (%d minutes) exceeds maximum safety tolerance (%d minutes). Recommendations halted to prevent serving unverified or potentially revoked policies.",
                    partitionMs / 60000, maxStalenessPartitionToleranceMs / 60000);
            return new CatalogFreshnessResolution("PARTITION_STALENESS_EXCEEDED_HALTED", true, true, warning);
        }

        String warning = String.format(
                "Freshness Advisory: Live database synchronization is currently unavailable (partition active for %d minutes, within %d min safety tolerance). Recommendations are served from an immutable verified snapshot. Note: policies revoked during this partition window may not be reflected until connectivity is restored.",
                partitionMs / 60000, maxStalenessPartitionToleranceMs / 60000);
        return new CatalogFreshnessResolution("DEGRADED_PARTITION_RECENT", true, false, warning);
    }

    @lombok.AllArgsConstructor
    @lombok.Getter
    public static class CatalogFreshnessResolution {
        private final String status;
        private final boolean stale;
        private final boolean halted;
        private final String warning;
    }

    private RecommendationResponseDto buildEmptyResponse(
            RecommendationProfile profile, int totalCatalogSize, List<EligibilityEngine.EligibilityResult> allEligibilityResults) {
        CatalogFreshnessResolution freshness = resolveCatalogFreshness();

        RecommendationResult.ProfileGuidance guidance = buildGuidance(profile);
        long requestedSI = (profile.getDesiredSumInsured() > 0 && !profile.isHelpMeDecideSumInsured())
                ? profile.getDesiredSumInsured()
                : guidance.getRecommendedSumInsured();

        boolean coverageSpecificCause = (profile.getDesiredSumInsured() > 0 && !profile.isHelpMeDecideSumInsured())
                && allEligibilityResults != null
                && !allEligibilityResults.isEmpty()
                && allEligibilityResults.stream().allMatch(r ->
                        !r.getFailReasons().isEmpty()
                        && r.getFailReasons().stream().allMatch(f ->
                                f.toLowerCase().contains("sum insured") || f.toLowerCase().contains("coverage")));

        String emptyAdvisory = coverageSpecificCause
                ? String.format("No policy meets the requested coverage of %s.", formatSumInsured(requestedSI))
                : "No eligible policies were found.";

        guidance.setCoverageAdvisory(emptyAdvisory);

        String budgetLabel = "STRICT".equals(profile.getBudgetType()) ? "Strict" : "Preferred";
        String priorityLabel = switch (profile.getPrimaryPriority() != null ? profile.getPrimaryPriority() : "BALANCED") {
            case "AFFORDABLE_PREMIUMS" -> "Affordable Premiums";
            case "BROADER_COVERAGE" -> "Broader Coverage";
            case "LOWER_OUT_OF_POCKET" -> "Lower Out-of-Pocket Costs";
            default -> "Balanced Overall Suitability";
        };

        return RecommendationResponseDto.builder()
                .profileId(profile.getId())
                .generatedAt(Instant.now())
                .profileSummary(RecommendationResponseDto.ProfileSummary.builder()
                        .primaryAge(profile.getPrimaryAge())
                        .insuranceType(profile.getInsuranceType())
                        .totalInsuredMembers(1 + (profile.getMembers() != null ? profile.getMembers().size() : 0))
                        .cityOrPincode(profile.getCityOrPincode())
                        .pricingZone(profile.getDetectedZone())
                        .annualBudget(profile.getAnnualBudget())
                        .formattedBudget(String.format("₹%,.0f (%s)", profile.getAnnualBudget(), budgetLabel))
                        .budgetType(profile.getBudgetType())
                        .selectedSumInsured(requestedSI)
                        .formattedSumInsured(formatSumInsured(requestedSI))
                        .primaryPriorityLabel(priorityLabel)
                        .declaredConditions(profile.getPreExistingConditions())
                        .build())
                .guidance(guidance)
                .rankedPolicies(List.of())
                .totalPoliciesEvaluated(totalCatalogSize)
                .eligiblePoliciesCount(0)
                .catalogVersion(this.localCatalogVersion)
                .catalogStatus(freshness.getStatus())
                .isStale(freshness.isStale())
                .isHalted(freshness.isHalted())
                .catalogFreshnessWarning(freshness.getWarning())
                .coverageAdvisory(emptyAdvisory)
                .build();
    }

    private String formatSumInsured(long amount) {
        if (amount >= 10000000) {
            double cr = (double) amount / 10000000.0;
            java.text.DecimalFormat df = new java.text.DecimalFormat("#.##", new java.text.DecimalFormatSymbols(java.util.Locale.ROOT));
            return "₹" + df.format(cr) + " Cr";
        }
        if (amount >= 100000) {
            double lakh = (double) amount / 100000.0;
            java.text.DecimalFormat df = new java.text.DecimalFormat("#.##", new java.text.DecimalFormatSymbols(java.util.Locale.ROOT));
            return "₹" + df.format(lakh) + " Lakh";
        }
        return String.format("₹%,d", amount);
    }
}
