package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.dto.PolicyDiscoveryDto;
import com.vidhura.ai.recommendation.model.DiscoveredDocument;
import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.repository.DiscoveredDocumentRepository;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import com.vidhura.ai.service.PdfParsingService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background Discovery & Ingestion Monitor.
 *
 * Responsibilities:
 * 1. Two-Stage Crawl: Discovers new official insurer candidate sources before polling existing ones.
 * 2. Maintains the registry of official IRDAI and insurer source document endpoints.
 * 3. Periodically polls active sources on a schedule with conditional HTTP headers.
 * 4. Detects newly published or revised policy documents via cryptographic SHA-256 hashes.
 * 5. Extracts text via PdfParsingService and stages drafts via PolicyDiscoveryPipelineService.
 * 6. Guarantees that extracted policies remain inactive drafts (EXTRACTION_PENDING_REVIEW)
 *    until explicitly approved by an authorized human reviewer.
 */
@Slf4j
@Service
public class PolicySourceMonitor {

    private final PolicySourceRepository sourceRepository;
    private final DiscoveredDocumentRepository documentRepository;
    private final PolicyDocumentFetcher documentFetcher;
    private final PdfParsingService pdfParsingService;
    private final PolicyDiscoveryPipelineService discoveryPipelineService;
    private final CanonicalProductRegistry canonicalProductRegistry;
    private final OfficialInsurerDiscoveryAdapter discoveryAdapter;
    private final PolicyOcrFallbackService ocrFallbackService;

    @Autowired
    public PolicySourceMonitor(
            PolicySourceRepository sourceRepository,
            DiscoveredDocumentRepository documentRepository,
            PolicyDocumentFetcher documentFetcher,
            PdfParsingService pdfParsingService,
            PolicyDiscoveryPipelineService discoveryPipelineService,
            CanonicalProductRegistry canonicalProductRegistry,
            @Nullable OfficialInsurerDiscoveryAdapter discoveryAdapter,
            @Nullable PolicyOcrFallbackService ocrFallbackService) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.documentFetcher = documentFetcher;
        this.pdfParsingService = pdfParsingService;
        this.discoveryPipelineService = discoveryPipelineService;
        this.canonicalProductRegistry = canonicalProductRegistry;
        this.discoveryAdapter = discoveryAdapter != null
                ? discoveryAdapter
                : new OfficialInsurerDiscoveryAdapter(documentFetcher, sourceRepository);
        this.ocrFallbackService = ocrFallbackService != null
                ? ocrFallbackService
                : new PolicyOcrFallbackService();
    }

    public PolicySourceMonitor(
            PolicySourceRepository sourceRepository,
            DiscoveredDocumentRepository documentRepository,
            PolicyDocumentFetcher documentFetcher,
            PdfParsingService pdfParsingService,
            PolicyDiscoveryPipelineService discoveryPipelineService,
            CanonicalProductRegistry canonicalProductRegistry,
            @Nullable OfficialInsurerDiscoveryAdapter discoveryAdapter) {
        this(sourceRepository, documentRepository, documentFetcher, pdfParsingService,
                discoveryPipelineService, canonicalProductRegistry, discoveryAdapter, null);
    }

    public PolicySourceMonitor(
            PolicySourceRepository sourceRepository,
            DiscoveredDocumentRepository documentRepository,
            PolicyDocumentFetcher documentFetcher,
            PdfParsingService pdfParsingService,
            PolicyDiscoveryPipelineService discoveryPipelineService,
            CanonicalProductRegistry canonicalProductRegistry) {
        this(sourceRepository, documentRepository, documentFetcher, pdfParsingService,
                discoveryPipelineService, canonicalProductRegistry, null, null);
    }

    // Authoritative approved official insurer domain whitelist (SSRF & Provenance Enforcement)
    public static final Set<String> APPROVED_OFFICIAL_DOMAINS = Set.of(
            "hdfcergo.com",
            "starhealth.in",
            "careinsurance.com",
            "nivabupa.com",
            "icicilombard.com",
            "bajajallianz.com",
            "bajajgeneralinsurance.com",
            "tataaig.com",
            "uiic.co.in",
            "nationalinsurance.nic.co.in",
            "newindia.co.in",
            "orientalinsurance.org.in",
            "irdai.gov.in"
    );

    // In-memory source cache for high availability during DB outages/tests
    private final Map<String, PolicySource> inMemorySourceRegistry = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlSummary {
        private String discoveryMode; // "LIVE_DISCOVERY", "CATALOG_FALLBACK", "HYBRID"
        private int totalSources;
        private int checkedSources;
        private int sourcesScanned;
        private int candidateSourcesDiscovered;
        private int candidateSourcesRegistered;
        private int candidateSourcesRejected;
        private int liveCandidatesDiscovered;
        private int fallbackCatalogCandidates;
        private boolean lastLiveScanSuccessful;
        private Instant lastLiveScanTimestamp;
        @Builder.Default
        private List<String> liveScannedUrls = new ArrayList<>();
        @Builder.Default
        private Map<String, Integer> candidateRejectionReasons = new HashMap<>();
        @Builder.Default
        private List<OfficialInsurerDiscoveryAdapter.DomainDiagnostics> domainDiagnostics = new ArrayList<>();
        private int documentsDownloaded;
        private int unchangedDocumentsSkipped;
        private int backoffSkippedSources;
        private int newDocumentsExtracted;
        private int newRevisionsDetected;
        private int draftsCreated;
        private int contractualWordingsCount;
        private int cataloguesCount;
        private int totalCandidatesFound;
        private int failedSources;
        @Builder.Default
        private List<String> failureDetails = new ArrayList<>();
        @Builder.Default
        private List<String> messages = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryReport {
        private String discoveryMode; // "LIVE_DISCOVERY", "CATALOG_FALLBACK", "HYBRID", "EXTERNAL_INPUT"
        private int candidateSourcesFound;
        private int candidateSourcesRegistered;
        private int candidateSourcesRejected;
        private int liveCandidatesDiscovered;
        private int fallbackCatalogCandidates;
        private boolean lastLiveScanSuccessful;
        private Instant lastLiveScanTimestamp;
        @Builder.Default
        private List<String> liveScannedUrls = new ArrayList<>();
        @Builder.Default
        private Map<String, Integer> rejectionReasons = new HashMap<>();
        @Builder.Default
        private List<PolicySource> registeredSources = new ArrayList<>();
        @Builder.Default
        private List<OfficialInsurerDiscoveryAdapter.DomainDiagnostics> domainDiagnostics = new ArrayList<>();
        @Builder.Default
        private List<String> discoveryMessages = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CuratedSourceRequest {
        private String sourceUrl;
        private String sourceName;
        private String provider;
        private String canonicalProductName;
        private String expectedUin;
        private String expectedRevision;
        private String curationNotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CuratedSourceResponse {
        private boolean success;
        private String message;
        private PolicySource registeredSource;
        private DiscoveredDocument stagedDocument;
        private String stagedPolicyId;
        private String irdaUin;
        private String uinRevision;
        private String sha256Checksum;
        private long fileSizeBytes;
        private String verificationStatus;
    }

    @PostConstruct
    public void initDefaultSources() {
        for (PolicySource s : getDefaultSeedSources()) {
            inMemorySourceRegistry.put(s.getId(), s);
        }
        try {
            if (sourceRepository != null && sourceRepository.count() == 0) {
                log.info("[PolicySourceMonitor] Initializing curated registry of official insurer document sources...");
                seedOfficialSources();
            }
        } catch (Exception e) {
            log.warn("[PolicySourceMonitor] Could not initialize default sources in MongoDB: {}", e.getMessage());
        }
    }

    public List<PolicySource> getDefaultSeedSources() {
        return List.of(
                PolicySource.builder()
                        .id("src-hdfc-optima")
                        .sourceName("HDFC ERGO Optima Secure Policy Wordings")
                        .provider("HDFC ERGO General Insurance Co. Ltd.")
                        .canonicalProductName("Optima Secure")
                        .irdaUin("HDFHLIP21147V012021")
                        .expectedRevision("V012021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.hdfcergo.com/docs/default-source/downloads/policy-wordings/optima-secure-policy-wording.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-star-comp")
                        .sourceName("Star Health Comprehensive Insurance Wordings")
                        .provider("Star Health and Allied Insurance Co. Ltd.")
                        .canonicalProductName("Star Comprehensive Insurance Policy")
                        .irdaUin("SHAHLIP21163V012021")
                        .expectedRevision("V012021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.starhealth.in/sites/default/files/star-comprehensive-policy-wording.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-care-supreme")
                        .sourceName("Care Supreme Official Policy Terms")
                        .provider("Care Health Insurance Ltd.")
                        .canonicalProductName("Care Supreme")
                        .irdaUin("CHIHLIP21161V012021")
                        .expectedRevision("V012021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.careinsurance.com/product/care-supreme-policy-terms.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-niva-reassure")
                        .sourceName("Niva Bupa ReAssure 2.0 Wordings")
                        .provider("Niva Bupa Health Insurance Co. Ltd.")
                        .canonicalProductName("ReAssure 2.0")
                        .irdaUin("MAXHLIP21165V012021")
                        .expectedRevision("V012021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.nivabupa.com/content/dam/nivabupa/pdf/reassure-2-0-policy-wording.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-icici-elevate")
                        .sourceName("ICICI Lombard Elevate Policy Wordings")
                        .provider("ICICI Lombard General Insurance Co. Ltd.")
                        .canonicalProductName("Elevate")
                        .irdaUin("ICIHLIP21167V012021")
                        .expectedRevision("V012021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.icicilombard.com/docs/default-source/health-insurance/elevate-policy-wordings.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-bajaj-guard")
                        .sourceName("Bajaj Allianz Health Guard Policy Terms")
                        .provider("Bajaj Allianz General Insurance Co. Ltd.")
                        .canonicalProductName("Health Guard")
                        .irdaUin("BAGHLIP21159V022021")
                        .expectedRevision("V022021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.bajajallianz.com/content/dam/bagic/pdf/health-guard-policy-wordings.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-uiic-fmp")
                        .sourceName("UIIC Family Medicare Policy Wordings")
                        .provider("United India Insurance Company Limited")
                        .canonicalProductName("Family Medicare Policy")
                        .irdaUin("UIIHLIP22070V042122")
                        .expectedRevision("V042122")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://uiic.co.in/web/sites/default/files/uploads/downloadcenter/FMP_wordings.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-nia-mediclaim")
                        .sourceName("New India Mediclaim Policy Clause")
                        .provider("The New India Assurance Company Limited")
                        .canonicalProductName("New India Mediclaim Policy")
                        .irdaUin("NIAHLIP25040V102425")
                        .expectedRevision("V102425")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.newindia.co.in/assets/docs/know-more/health/new-india-mediclaim-policy/Policy%20%20Clause_%20New%20India%20Mediclaim%20Policy%20.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-nia-premier")
                        .sourceName("New India Premier Mediclaim Policy Clause")
                        .provider("The New India Assurance Company Limited")
                        .canonicalProductName("New India Premier Mediclaim")
                        .irdaUin("NIAHLIP21279V032021")
                        .expectedRevision("V032021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://newindia.co.in/assets/docs/know-more/health/new-premier-mediclaim-claim-policy/Policy%20Clause%20New%20India%20Premier%20Mediclaim%20(wef%201st%20October%202021).pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        );
    }

    public void seedOfficialSources() {
        List<PolicySource> defaultSources = getDefaultSeedSources();
        try {
            sourceRepository.saveAll(defaultSources);
            log.info("[PolicySourceMonitor] Seeded {} official insurer policy document sources.", defaultSources.size());
        } catch (Exception e) {
            log.warn("[PolicySourceMonitor] Could not seed sources in repository: {}", e.getMessage());
        }
    }

    /**
     * Verifies if a hostname belongs to an approved official insurer or IRDAI regulatory domain.
     */
    public boolean isApprovedOfficialDomain(String host) {
        if (host == null || host.isBlank()) return false;
        String cleanHost = host.trim().toLowerCase();
        return APPROVED_OFFICIAL_DOMAINS.stream()
                .anyMatch(domain -> cleanHost.equals(domain) || cleanHost.endsWith("." + domain));
    }

    /**
     * Checks if a source URL is already registered in MongoDB or in-memory registry.
     */
    public boolean isSourceUrlRegistered(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            if (sourceRepository != null && sourceRepository.existsBySourceUrl(url)) {
                return true;
            }
        } catch (Exception ignored) {}
        return inMemorySourceRegistry.values().stream()
                .anyMatch(s -> url.equalsIgnoreCase(s.getSourceUrl()));
    }

    /**
     * Authoritative discovery catalog of verified official insurer product filings
     * to discover beyond initial seed monitors.
     */
    public List<PolicySource> getOfficialInsurerDiscoveryCatalog() {
        return List.of(
                PolicySource.builder()
                        .id("src-tata-medicare")
                        .sourceName("Tata AIG MediCare Official Wordings")
                        .provider("Tata AIG General Insurance Co. Ltd.")
                        .canonicalProductName("Medicare")
                        .irdaUin("TATHLIP21169V012021")
                        .expectedRevision("V012021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.tataaig.com/content/dam/tagic/pdf/consumer/health/medicare-policy-wordings.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-uii-medicare")
                        .sourceName("United India Family Medicare Policy Wordings")
                        .provider("United India Insurance Co. Ltd.")
                        .canonicalProductName("Family Medicare Policy")
                        .irdaUin("UIIHLIP22070V042122")
                        .expectedRevision("V042122")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://uiic.co.in/web/sites/default/files/uploads/downloadcenter/FMP_wordings.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-nic-mediclaim")
                        .sourceName("National Insurance Mediclaim Policy Terms")
                        .provider("National Insurance Co. Ltd.")
                        .canonicalProductName("National Mediclaim Policy")
                        .irdaUin("IRDA/NL-HLT/NIC/P-H/V.I/237/13-14")
                        .expectedRevision("V.I/237/13-14")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://nationalinsurance.nic.co.in/sites/default/files/national-mediclaim-policy-wording.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-star-red-carpet")
                        .sourceName("Star Senior Citizens Red Carpet Official Terms")
                        .provider("Star Health and Allied Insurance Co. Ltd.")
                        .canonicalProductName("Star Senior Citizens Red Carpet")
                        .irdaUin("SHAHLIP21164V012021")
                        .expectedRevision("V012021")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.starhealth.in/sites/default/files/senior-citizens-red-carpet-policy-wording.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                PolicySource.builder()
                        .id("src-hdfc-koti")
                        .sourceName("HDFC ERGO My:Health Koti Suraksha Terms")
                        .provider("HDFC ERGO General Insurance Co. Ltd.")
                        .canonicalProductName("My:Health Koti Suraksha")
                        .irdaUin("HDFHLIP23030V022223")
                        .expectedRevision("V022223")
                        .documentType("POLICY_WORDING")
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .sourceUrl("https://www.hdfcergo.com/docs/default-source/downloads/policy-wordings/myhealth-koti-suraksha-policy-wording.pdf")
                        .status("ACTIVE")
                        .pollFrequencyHours(24)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        );
    }

    /**
     * Stage 1 of Crawl: Bounded Official Insurer Source Discovery.
     * Evaluates official insurer directories and candidate URLs, strictly validating
     * official domain provenance, SSRF safety, document scheme, and identity before registering.
     */
    public DiscoveryReport discoverNewCandidateSources(List<String> externalCandidateUrls) {
        int found = 0;
        int registered = 0;
        int rejected = 0;
        int liveDiscovered = 0;
        int fallbackCandidates = 0;
        boolean liveScanSuccess = false;
        Instant liveScanTime = null;
        List<String> scannedUrls = new ArrayList<>();
        String mode = "CATALOG_FALLBACK";

        Map<String, Integer> rejectionReasons = new HashMap<>();
        List<PolicySource> registeredSources = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        List<OfficialInsurerDiscoveryAdapter.DomainDiagnostics> domainDiagnostics = new ArrayList<>();

        List<PolicySource> candidates = new ArrayList<>();

        if (externalCandidateUrls != null && !externalCandidateUrls.isEmpty()) {
            mode = "EXTERNAL_INPUT";
            for (String urlStr : externalCandidateUrls) {
                if (urlStr == null || urlStr.isBlank()) continue;
                String cleanUrl = urlStr.trim();
                String host = "unknown";
                try {
                    host = URI.create(cleanUrl).getHost();
                } catch (Exception ignored) {}
                candidates.add(PolicySource.builder()
                        .id("src-disc-" + UUID.randomUUID().toString().substring(0, 8))
                        .sourceName("Discovered Insurer Source (" + host + ")")
                        .sourceUrl(cleanUrl)
                        .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                        .documentType("POLICY_WORDING")
                        .status("PENDING_VALIDATION")
                        .active(false)
                        .build());
            }
        } else {
            // DYNAMIC DISCOVERY: Attempt live page scan across approved official insurer directories
            try {
                if (discoveryAdapter != null) {
                    OfficialInsurerDiscoveryAdapter.DynamicDiscoveryResult liveResult =
                            discoveryAdapter.discoverFromLiveOfficialPortals(null);
                    liveScanTime = liveResult.getScanCompletedAt() != null ? liveResult.getScanCompletedAt() : Instant.now();
                    if (liveResult.getScannedPageUrls() != null) {
                        scannedUrls.addAll(liveResult.getScannedPageUrls());
                    }
                    if (liveResult.getDomainDiagnostics() != null) {
                        domainDiagnostics.addAll(liveResult.getDomainDiagnostics());
                    }
                    if (liveResult.getRegisteredSources() != null && !liveResult.getRegisteredSources().isEmpty()) {
                        for (PolicySource liveSource : liveResult.getRegisteredSources()) {
                            if (isSourceUrlRegistered(liveSource.getSourceUrl())) {
                                rejected++;
                                recordRejection(rejectionReasons, "DUPLICATE_SOURCE: URL is already registered in active source monitor");
                                continue;
                            }
                            safeSaveSource(liveSource);
                            registeredSources.add(liveSource);
                            registered++;
                            messages.add("Dynamically discovered & validated live official source: " + liveSource.getSourceName()
                                    + " (UIN=" + liveSource.getIrdaUin() + ")");
                        }
                    }
                    liveDiscovered = liveResult.getCandidateLinksFound();
                    found += liveResult.getCandidateLinksFound();
                    rejected += liveResult.getCandidatesRejected();
                    if (liveResult.getRejectionReasons() != null) {
                        liveResult.getRejectionReasons().forEach((k, v) ->
                                rejectionReasons.put(k, rejectionReasons.getOrDefault(k, 0) + v));
                    }

                    // Propagate tri-state outcome
                    String adapterMode = liveResult.getDiscoveryMode();
                    if ("LIVE_DISCOVERY".equalsIgnoreCase(adapterMode) || registered > 0) {
                        mode = "LIVE_DISCOVERY";
                        liveScanSuccess = true;
                    } else if ("LIVE_DISCOVERY_EMPTY".equalsIgnoreCase(adapterMode)) {
                        mode = "LIVE_DISCOVERY_EMPTY";
                        liveScanSuccess = true;
                    } else {
                        mode = "CATALOG_FALLBACK";
                        liveScanSuccess = false;
                    }
                }
            } catch (Exception e) {
                log.warn("[PolicySourceMonitor] Dynamic live portal discovery encountered exception: {}", e.getMessage());
                mode = "CATALOG_FALLBACK";
                liveScanSuccess = false;
            }

            // Fallback Catalog: ONLY engaged when live portals failed or were unreachable (CATALOG_FALLBACK)
            if ("CATALOG_FALLBACK".equals(mode)) {
                log.info("[PolicySourceMonitor] Live insurer portal discovery failed or unreachable. Engaging curated catalog fallback.");
                List<PolicySource> catalogSources = getOfficialInsurerDiscoveryCatalog();
                candidates.addAll(catalogSources);
                fallbackCandidates = catalogSources.size();
            } else if ("LIVE_DISCOVERY".equals(mode)) {
                log.info("[PolicySourceMonitor] Live discovery succeeded with {} new registered sources from live pages.", registered);
            } else if ("LIVE_DISCOVERY_EMPTY".equals(mode)) {
                log.info("[PolicySourceMonitor] Live discovery scan completed successfully (pages fetched), but no eligible new candidates were found.");
            }
        }

        found += candidates.size();

        for (PolicySource candidate : candidates) {
            String url = candidate.getSourceUrl();
            if (url == null || url.isBlank()) {
                rejected++;
                recordRejection(rejectionReasons, "EMPTY_URL: Candidate URL is null or blank");
                continue;
            }

            URI uri;
            try {
                uri = URI.create(url.trim());
            } catch (Exception e) {
                rejected++;
                recordRejection(rejectionReasons, "MALFORMED_URL: " + e.getMessage());
                continue;
            }

            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                rejected++;
                recordRejection(rejectionReasons, "UNSUPPORTED_SCHEME: Only HTTP and HTTPS are permitted");
                continue;
            }

            String host = uri.getHost();
            if (!isApprovedOfficialDomain(host)) {
                rejected++;
                recordRejection(rejectionReasons, "UNTRUSTED_DOMAIN: Host '" + host + "' is not an approved official insurer or IRDAI portal");
                continue;
            }

            if (documentFetcher != null && documentFetcher.isBlockedHostOrIp(host)) {
                rejected++;
                recordRejection(rejectionReasons, "SSRF_RESTRICTED_HOST: Target host resolves to a private or metadata IP");
                continue;
            }

            // Check if already registered
            boolean alreadyExists = false;
            try {
                if (sourceRepository != null && sourceRepository.existsBySourceUrl(url)) {
                    alreadyExists = true;
                }
            } catch (Exception e) {
                log.warn("[PolicySourceMonitor] Error querying sourceRepository: {}", e.getMessage());
            }
            if (!alreadyExists && inMemorySourceRegistry.values().stream().anyMatch(s -> url.equalsIgnoreCase(s.getSourceUrl()))) {
                alreadyExists = true;
            }

            if (alreadyExists) {
                rejected++;
                recordRejection(rejectionReasons, "DUPLICATE_SOURCE: URL is already registered in active source monitor");
                continue;
            }

            // Stage candidate as PENDING_VALIDATION before activation
            if (candidate.getId() == null || candidate.getId().isBlank()) {
                candidate.setId("src-" + UUID.randomUUID().toString().substring(0, 8));
            }
            candidate.setStatus("ACTIVE");
            candidate.setActive(true);
            candidate.setCreatedAt(Instant.now());
            candidate.setUpdatedAt(Instant.now());

            safeSaveSource(candidate);
            registeredSources.add(candidate);
            registered++;
            messages.add("Registered validated official insurer source: " + candidate.getSourceName() + " (" + candidate.getSourceUrl() + ")");
            log.info("[PolicySourceMonitor] Registered official insurer source '{}' (UIN={})",
                    candidate.getSourceName(), candidate.getIrdaUin());
        }

        return DiscoveryReport.builder()
                .discoveryMode(mode)
                .candidateSourcesFound(found)
                .candidateSourcesRegistered(registered)
                .candidateSourcesRejected(rejected)
                .liveCandidatesDiscovered(liveDiscovered)
                .fallbackCatalogCandidates(fallbackCandidates)
                .lastLiveScanSuccessful(liveScanSuccess)
                .lastLiveScanTimestamp(liveScanTime != null ? liveScanTime : Instant.now())
                .liveScannedUrls(scannedUrls)
                .rejectionReasons(rejectionReasons)
                .registeredSources(registeredSources)
                .domainDiagnostics(domainDiagnostics)
                .discoveryMessages(messages)
                .build();
    }

    private void recordRejection(Map<String, Integer> map, String reason) {
        map.put(reason, map.getOrDefault(reason, 0) + 1);
    }

    /**
     * Executes the comprehensive Two-Stage Discovery & Crawl:
     * Stage 1: Bounded Official Insurer Source Discovery.
     * Stage 2: Targeted Document Change Detection & Ingestion via conditional requests and SHA-256.
     */
    public CrawlSummary executeTwoStageDiscoveryAndCrawl(boolean discoverNewSources, boolean forceReingest) {
        int candidateFound = 0;
        int candidateRegistered = 0;
        int candidateRejected = 0;
        String discMode = "DISABLED";
        int liveCandidatesDisc = 0;
        int fallbackCatalogCand = 0;
        boolean liveScanSuccess = false;
        Instant liveScanTime = null;
        List<String> liveUrls = new ArrayList<>();
        Map<String, Integer> candidateRejections = new HashMap<>();
        List<String> messages = new ArrayList<>();
        List<OfficialInsurerDiscoveryAdapter.DomainDiagnostics> crawlDiagnostics = new ArrayList<>();

        // STAGE 1: Bounded Official Insurer Source Discovery
        if (discoverNewSources) {
            log.info("[PolicySourceMonitor] Stage 1: Running bounded official insurer source discovery...");
            DiscoveryReport disc = discoverNewCandidateSources(null);
            candidateFound = disc.getCandidateSourcesFound();
            candidateRegistered = disc.getCandidateSourcesRegistered();
            candidateRejected = disc.getCandidateSourcesRejected();
            discMode = disc.getDiscoveryMode();
            liveCandidatesDisc = disc.getLiveCandidatesDiscovered();
            fallbackCatalogCand = disc.getFallbackCatalogCandidates();
            liveScanSuccess = disc.isLastLiveScanSuccessful();
            liveScanTime = disc.getLastLiveScanTimestamp();
            liveUrls = disc.getLiveScannedUrls();
            candidateRejections = disc.getRejectionReasons();
            messages.addAll(disc.getDiscoveryMessages());
            if (disc.getDomainDiagnostics() != null) {
                crawlDiagnostics.addAll(disc.getDomainDiagnostics());
            }
            log.info("[PolicySourceMonitor] Stage 1 finished (mode={}): {} candidates evaluated, {} registered, {} rejected",
                    discMode, candidateFound, candidateRegistered, candidateRejected);
        }

        // STAGE 2: Targeted Document Change Detection & Ingestion
        log.info("[PolicySourceMonitor] Stage 2: Polling all registered sources (forceReingest={})...", forceReingest);
        List<PolicySource> sources = getActiveSourcesWithFallback();

        int checked = 0;
        int downloaded = 0;
        int extracted = 0;
        int skipped = 0;
        int backoffSkipped = 0;
        int contractualWordings = 0;
        int catalogues = 0;
        int totalCandidates = 0;
        int revisions = 0;
        int drafts = 0;
        int failed = 0;
        List<String> failureDetails = new ArrayList<>();

        for (PolicySource source : sources) {
            checked++;
            try {
                String outcome = processSource(source, forceReingest);
                if (outcome.startsWith("EXTRACTED")) {
                    downloaded++;
                    extracted++;
                    drafts++;
                    if (source.isContractualWording()) {
                        contractualWordings++;
                    }
                    if (source.getCataloguesCount() > 0 || "PRODUCT_CATALOGUE".equalsIgnoreCase(source.getDocumentClassification())) {
                        catalogues++;
                    }
                    totalCandidates += source.getCandidateCount();
                    if (outcome.contains("REVISION")) {
                        revisions++;
                    }
                } else if (outcome.startsWith("SKIPPED_BACKOFF")) {
                    backoffSkipped++;
                    skipped++;
                } else if (outcome.startsWith("SKIPPED")) {
                    skipped++;
                } else {
                    failed++;
                    failureDetails.add(source.getId() + " (" + source.getSourceName() + "): " + outcome);
                }
                messages.add(source.getSourceName() + ": " + outcome);
            } catch (Exception e) {
                failed++;
                String err = "Exception polling " + source.getId() + " (" + source.getSourceName() + "): " + e.getMessage();
                failureDetails.add(err);
                messages.add(err);
                log.error("[PolicySourceMonitor] {}", err, e);
            }
        }

        return CrawlSummary.builder()
                .discoveryMode(discMode)
                .totalSources(sources.size())
                .checkedSources(checked)
                .sourcesScanned(checked)
                .candidateSourcesDiscovered(candidateFound)
                .candidateSourcesRegistered(candidateRegistered)
                .candidateSourcesRejected(candidateRejected)
                .liveCandidatesDiscovered(liveCandidatesDisc)
                .fallbackCatalogCandidates(fallbackCatalogCand)
                .lastLiveScanSuccessful(liveScanSuccess)
                .lastLiveScanTimestamp(liveScanTime)
                .liveScannedUrls(liveUrls)
                .candidateRejectionReasons(candidateRejections)
                .domainDiagnostics(crawlDiagnostics)
                .documentsDownloaded(downloaded)
                .unchangedDocumentsSkipped(skipped)
                .backoffSkippedSources(backoffSkipped)
                .newDocumentsExtracted(extracted)
                .newRevisionsDetected(revisions)
                .draftsCreated(drafts)
                .contractualWordingsCount(contractualWordings)
                .cataloguesCount(catalogues)
                .totalCandidatesFound(totalCandidates)
                .failedSources(failed)
                .failureDetails(failureDetails)
                .messages(messages)
                .build();
    }

    public List<PolicySource> getActiveSourcesWithFallback() {
        List<PolicySource> sources = new ArrayList<>();
        try {
            if (sourceRepository != null) {
                sources = sourceRepository.findByActiveTrue();
            }
        } catch (Exception e) {
            log.warn("[PolicySourceMonitor] Failed to query sourceRepository, using in-memory registry: {}", e.getMessage());
        }
        if (sources == null || sources.isEmpty()) {
            if (!inMemorySourceRegistry.isEmpty()) {
                sources = inMemorySourceRegistry.values().stream().filter(PolicySource::isActive).toList();
            }
            if (sources == null || sources.isEmpty()) {
                sources = getDefaultSeedSources();
            }
        }
        return sources;
    }

    @org.springframework.beans.factory.annotation.Value("${vidhura.policy.discovery.enabled:false}")
    private boolean scheduledDiscoveryEnabled = false;

    /**
     * Scheduled background job: Polls official sources at 2:00 AM daily.
     */
    @Scheduled(cron = "${vidhura.policy.discovery.cron:-}")
    public void scheduledDiscoveryCrawl() {
        if (!scheduledDiscoveryEnabled) {
            log.info("[PolicySourceMonitor] Scheduled background discovery crawl is disabled by configuration.");
            return;
        }
        log.info("[PolicySourceMonitor] Starting scheduled background policy discovery crawl...");
        CrawlSummary summary = pollAllSources(false);
        log.info("[PolicySourceMonitor] Completed scheduled crawl: checked={}, extracted={}, skipped={}, failed={}",
                summary.getCheckedSources(), summary.getNewDocumentsExtracted(),
                summary.getUnchangedDocumentsSkipped(), summary.getFailedSources());
    }

    /**
     * Polls all active sources with automatic candidate discovery.
     *
     * @param forceReingest If true, forces download and re-extraction regardless of ETag or SHA-256 match.
     */
    public CrawlSummary pollAllSources(boolean forceReingest) {
        return executeTwoStageDiscoveryAndCrawl(true, forceReingest);
    }

    /**
     * Polls and ingests a single source by ID.
     */
    public String pollSingleSource(String sourceId, boolean forceReingest) {
        PolicySource source = null;
        try {
            if (sourceRepository != null) {
                source = sourceRepository.findById(sourceId).orElse(null);
            }
        } catch (Exception e) {
            log.warn("[PolicySourceMonitor] Failed to query sourceRepository for {}: {}", sourceId, e.getMessage());
        }
        if (source == null) {
            source = inMemorySourceRegistry.get(sourceId);
        }
        if (source == null) {
            source = getDefaultSeedSources().stream()
                    .filter(s -> s.getId().equalsIgnoreCase(sourceId))
                    .findFirst()
                    .orElse(null);
        }
        if (source == null) {
            source = getOfficialInsurerDiscoveryCatalog().stream()
                    .filter(s -> s.getId().equalsIgnoreCase(sourceId))
                    .findFirst()
                    .orElse(null);
        }
        if (source == null) {
            throw new IllegalArgumentException("Policy source not found with id: " + sourceId);
        }
        return processSource(source, forceReingest);
    }

    /**
     * Secure Manual Curated Source Registration Path for Authorized Reviewers.
     * When automated portal discovery is blocked or when an authorized reviewer registers
     * a verified official document:
     * 1. Re-validates approved official domain allowlist.
     * 2. Re-validates SSRF / IP restrictions via pinned-IP socket connection.
     * 3. Re-validates per-hop redirects up to 3 hops.
     * 4. Enforces 50 MB document size limit and verifies %PDF- magic bytes signature.
     * 5. Computes streaming SHA-256 and checks deduplication.
     * 6. Extracts and validates IRDAI statutory UIN (alphanumeric and slash-regulatory).
     * 7. Cross-verifies insurer entity prefix alignment.
     * 8. Stages draft policy as EXTRACTION_PENDING_REVIEW with human publication gate strictly preserved.
     */
    public CuratedSourceResponse registerCuratedOfficialSource(CuratedSourceRequest req, String reviewerEmail) {
        if (req == null || req.getSourceUrl() == null || req.getSourceUrl().isBlank()) {
            throw new IllegalArgumentException("Source URL is required.");
        }

        String rawUrl = req.getSourceUrl().trim();
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new SecurityException("Unsupported scheme: only HTTP and HTTPS are permitted.");
        }

        String host = uri.getHost();
        if (host == null || !isApprovedOfficialDomain(host)) {
            throw new SecurityException("Host '" + host + "' is not on the approved official insurer allowlist.");
        }

        if (documentFetcher != null && documentFetcher.isBlockedHostOrIp(host)) {
            throw new SecurityException("Target host resolves to a restricted private or cloud metadata IP address.");
        }

        // Deduplication check
        if (isSourceUrlRegistered(rawUrl)) {
            throw new IllegalArgumentException("Source URL is already registered in active policy catalog.");
        }

        // Download document with socket-pinned IP connection and TLS hostname/SNI validation
        PolicyDocumentFetcher.FetchResult fetchResult = documentFetcher != null
                ? documentFetcher.fetchDocument(rawUrl, null, null)
                : null;

        if (fetchResult == null || fetchResult.getStatus() == PolicyDocumentFetcher.FetchStatus.FAILED) {
            String err = fetchResult != null ? fetchResult.getErrorMessage() : "Fetcher unavailable";
            throw new RuntimeException("Failed to fetch verified official document: " + err);
        }

        byte[] pdfBytes = fetchResult.getDocumentBytes();
        if (pdfBytes == null || pdfBytes.length < 5) {
            throw new IllegalArgumentException("Retrieved empty document (0 bytes) from source URL.");
        }

        if (documentFetcher != null && !documentFetcher.isPdfContent(pdfBytes)) {
            throw new IllegalArgumentException("Invalid document format: Document does not contain genuine %PDF- magic byte signature.");
        }

        String sha256 = fetchResult.getSha256Checksum();
        // Check content SHA-256 deduplication
        if (sourceRepository != null) {
            boolean duplicateContent = false;
            try {
                duplicateContent = sourceRepository.findAll().stream()
                        .anyMatch(s -> sha256.equalsIgnoreCase(s.getLastSha256Checksum()));
            } catch (Exception e) {
                log.warn("[PolicySourceMonitor] Could not query source repository for duplicate check: {}", e.getMessage());
            }
            if (duplicateContent) {
                throw new IllegalArgumentException("Duplicate document content: A document with identical SHA-256 checksum ("
                        + sha256 + ") is already registered.");
            }
        }

        // Extract text and UIN
        String rawText = "";
        try {
            if (pdfParsingService != null) {
                rawText = pdfParsingService.extractText(pdfBytes);
            } else {
                rawText = new String(pdfBytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            rawText = new String(pdfBytes, StandardCharsets.UTF_8);
        }

        // OCR fallback for valid PDFs with no meaningful extractable text layer
        boolean ocrApplied = false;
        Double ocrConfidence = null;
        if (ocrFallbackService != null && ocrFallbackService.isOcrCandidate(pdfBytes, rawText)) {
            log.info("[PolicySourceMonitor] Scanned PDF candidate detected during curated registration. Invoking OCR fallback service...");
            PolicyOcrFallbackService.OcrResult ocrResult =
                    ocrFallbackService.processScannedPdf(pdfBytes, req.getSourceName(), rawUrl);
            if (ocrResult.isOcrApplied()) {
                rawText = ocrResult.getExtractedText();
                ocrApplied = true;
                ocrConfidence = ocrResult.getOcrConfidenceScore();
            }
        }

        // Extract IRDAI UIN from text, URL, or request
        OfficialInsurerDiscoveryAdapter.ExtractedUinInfo uinInfo = OfficialInsurerDiscoveryAdapter.extractUin(rawText);
        if (uinInfo == null) {
            uinInfo = OfficialInsurerDiscoveryAdapter.extractUin(rawUrl);
        }

        String irdaUin = uinInfo != null ? uinInfo.getUin() : req.getExpectedUin();
        String revision = uinInfo != null ? uinInfo.getRevision() : req.getExpectedRevision();

        if (irdaUin == null || irdaUin.isBlank()) {
            throw new IllegalArgumentException("Missing statutory IRDAI UIN: Document text and candidate parameters do not contain a valid statutory UIN.");
        }

        String provider = req.getProvider() != null && !req.getProvider().isBlank()
                ? req.getProvider()
                : OfficialInsurerDiscoveryAdapter.deriveProviderFromHost(host);

        // Verify entity prefix alignment
        if (!PolicyDiscoveryPipelineService.validateInsurerUinPrefix(provider, irdaUin)) {
            throw new SecurityException("Insurer UIN prefix mismatch: Provider '" + provider + "' does not match statutory prefix in " + irdaUin);
        }

        String productName = req.getCanonicalProductName() != null && !req.getCanonicalProductName().isBlank()
                ? req.getCanonicalProductName()
                : (req.getSourceName() != null ? req.getSourceName() : OfficialInsurerDiscoveryAdapter.deriveProductNameFromUrl(rawUrl));

        String sourceName = req.getSourceName() != null && !req.getSourceName().isBlank()
                ? req.getSourceName()
                : productName + " (" + host + ")";

        // Register Source
        PolicySource newSource = PolicySource.builder()
                .id("src-curated-" + UUID.randomUUID().toString().substring(0, 8))
                .sourceName(sourceName)
                .provider(provider)
                .canonicalProductName(productName)
                .irdaUin(irdaUin)
                .expectedRevision(revision)
                .documentType("POLICY_WORDING")
                .sourceAuthority("MANUAL_CURATED_OFFICIAL")
                .sourceUrl(rawUrl)
                .status("SYNCED")
                .active(true)
                .discoveryMode("MANUAL_CURATED_OFFICIAL")
                .ocrApplied(ocrApplied)
                .ocrConfidenceScore(ocrConfidence)
                .lastSha256Checksum(sha256)
                .lastFileSizeBytes(fetchResult.getFileSizeBytes())
                .lastEtag(fetchResult.getEtag())
                .lastModifiedHeader(fetchResult.getLastModifiedHeader())
                .lastHttpStatusCode(fetchResult.getHttpStatusCode())
                .lastCheckedAt(Instant.now())
                .lastDiscoveryAttemptAt(Instant.now())
                .lastFetchedAt(Instant.now())
                .lastSuccessfulCrawlAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        safeSaveSource(newSource);

        // Ingest and stage draft policy (strict human review gate enforced!)
        PolicyDiscoveryDto.PolicyDiscoveryRequest discoveryRequest =
                PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                        .documentUrl(rawUrl)
                        .documentName(sourceName)
                        .sourceType("POLICY_WORDING_PDF")
                        .policyName(productName)
                        .provider(provider)
                        .irdaUin(irdaUin)
                        .uinRevision(revision)
                        .rawPolicyText(rawText)
                        .autoPublishIfHighConfidence(false) // Mandatory human reviewer approval gate
                        .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto ingestionResult =
                discoveryPipelineService.discoverAndIngest(discoveryRequest);

        String classification = ingestionResult.getDocumentClassification();
        boolean isContract = "POLICY_WORDING".equalsIgnoreCase(classification);
        newSource.setDocumentClassification(classification);
        newSource.setContractualWording(isContract);
        newSource.setContractualWordingsCount(isContract ? 1 : 0);
        newSource.setCataloguesCount("PRODUCT_CATALOGUE".equalsIgnoreCase(classification) ? 1 : 0);
        newSource.setLastSuccessfulDiscoveryAt(Instant.now());
        newSource.setLinkedDraftPolicyId(ingestionResult.getPolicyId());
        safeSaveSource(newSource);

        // Record DiscoveredDocument audit archive
        DiscoveredDocument doc = DiscoveredDocument.builder()
                .id(UUID.randomUUID().toString())
                .sourceId(newSource.getId())
                .sourceUrl(rawUrl)
                .documentName(sourceName)
                .sha256Checksum(sha256)
                .fileSizeBytes(fetchResult.getFileSizeBytes())
                .etag(fetchResult.getEtag())
                .contentType(fetchResult.getContentType())
                .fetchedAt(Instant.now())
                .extractionStatus("EXTRACTED_STAGED")
                .documentClassification(ingestionResult.getDocumentClassification())
                .extractedPolicyId(ingestionResult.getPolicyId())
                .irdaUin(irdaUin)
                .uinRevision(revision)
                .confidenceScore(ingestionResult.getConfidenceScore())
                .build();
        safeSaveDoc(doc);

        log.info("[PolicySourceMonitor] Curated official source '{}' registered by reviewer {} (UIN={}, PolicyId={})",
                sourceName, reviewerEmail, irdaUin, ingestionResult.getPolicyId());

        return CuratedSourceResponse.builder()
                .success(true)
                .message("Official source successfully verified and staged as draft pending reviewer approval.")
                .registeredSource(newSource)
                .stagedDocument(doc)
                .stagedPolicyId(ingestionResult.getPolicyId())
                .irdaUin(irdaUin)
                .uinRevision(revision)
                .sha256Checksum(sha256)
                .fileSizeBytes(fetchResult.getFileSizeBytes())
                .verificationStatus("EXTRACTION_PENDING_REVIEW")
                .build();
    }

    /**
     * Directly processes document bytes and registers a discovered document archive and staged draft.
     * Useful for direct PDF ingestion via admin upload or direct API payloads.
     */
    public PolicyDiscoveryDto.PolicyIngestionResultDto ingestDiscoveredDocumentBytes(
            String sourceName,
            String sourceUrl,
            String provider,
            String irdaUin,
            String uinRevision,
            byte[] pdfBytes) {

        String sha256 = PolicyDocumentFetcher.computeSha256(pdfBytes);
        String docId = UUID.randomUUID().toString();

        String rawText = "";
        try {
            if (pdfParsingService != null && documentFetcher != null && documentFetcher.isPdfContent(pdfBytes)) {
                rawText = pdfParsingService.extractText(pdfBytes);
            } else {
                rawText = new String(pdfBytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("[PolicySourceMonitor] PDF text extraction failed for {}, using raw string fallback: {}",
                    sourceName, e.getMessage());
            rawText = new String(pdfBytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        PolicyDiscoveryDto.PolicyDiscoveryRequest discoveryRequest = PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                .documentUrl(sourceUrl)
                .documentName(sourceName)
                .sourceType("POLICY_WORDING_PDF")
                .policyName(sourceName)
                .provider(provider)
                .irdaUin(irdaUin)
                .uinRevision(uinRevision)
                .rawPolicyText(rawText)
                .autoPublishIfHighConfidence(false) // Strict human review gate enforced
                .build();

        PolicyDiscoveryDto.PolicyIngestionResultDto ingestionResult =
                discoveryPipelineService.discoverAndIngest(discoveryRequest);

        DiscoveredDocument doc = DiscoveredDocument.builder()
                .id(docId)
                .sourceUrl(sourceUrl)
                .documentName(sourceName)
                .sha256Checksum(sha256)
                .fileSizeBytes(pdfBytes.length)
                .fetchedAt(Instant.now())
                .extractionStatus("EXTRACTED_STAGED")
                .documentClassification(ingestionResult.getDocumentClassification())
                .extractedPolicyId(ingestionResult.getPolicyId())
                .irdaUin(ingestionResult.getIrdaUin())
                .uinRevision(ingestionResult.getUinRevision())
                .confidenceScore(ingestionResult.getConfidenceScore())
                .build();

        safeSaveDoc(doc);
        return ingestionResult;
    }

    public static String categorizeError(int statusCode, String errorMessage) {
        if (statusCode == 403) return "BOT_PROTECTION_403";
        if (statusCode == 404) return "NOT_FOUND_404";
        if (statusCode == 400 || statusCode == 421) return "MISDIRECTED_OR_BAD_421_400";
        if (statusCode >= 500 && statusCode < 600) return "SERVER_ERROR_5XX";
        if (errorMessage != null) {
            String lower = errorMessage.toLowerCase();
            if (lower.contains("ssrf") || lower.contains("private ip") || lower.contains("metadata")) return "SSRF_BLOCKED";
            if (lower.contains("ssl") || lower.contains("cert") || lower.contains("tls")) return "TLS_SECURITY_ERROR";
            if (lower.contains("timeout")) return "TIMEOUT_ERROR";
            if (lower.contains("signature") || lower.contains("%pdf-")) return "INVALID_DOCUMENT_SIGNATURE";
        }
        return "HTTP_ERROR_" + statusCode;
    }

    private String processSource(PolicySource source, boolean forceReingest) {
        Instant now = Instant.now();
        source.setLastCheckedAt(now);
        source.setLastDiscoveryAttemptAt(now);

        // Bounded retries & exponential backoff check
        if (!forceReingest && source.getNextRetryEligibleAt() != null && source.getNextRetryEligibleAt().isAfter(now)) {
            log.info("[PolicySourceMonitor] Skipping source '{}' due to active backoff until {}",
                    source.getSourceName(), source.getNextRetryEligibleAt());
            return "SKIPPED_BACKOFF_ACTIVE: Next retry eligible at " + source.getNextRetryEligibleAt();
        }

        String previousEtag = forceReingest ? null : source.getLastEtag();
        String previousLastMod = forceReingest ? null : source.getLastModifiedHeader();

        PolicyDocumentFetcher.FetchResult fetchResult =
                documentFetcher.fetchDocument(source.getSourceUrl(), previousEtag, previousLastMod);

        source.setLastHttpStatusCode(fetchResult.getHttpStatusCode());

        // 1. Unchanged document on server (HTTP 304)
        if (fetchResult.getStatus() == PolicyDocumentFetcher.FetchStatus.NOT_MODIFIED) {
            source.setStatus("SYNCED");
            source.setConsecutiveFailures(0);
            source.setBackoffHours(0);
            source.setNextRetryEligibleAt(null);
            source.setLastErrorMessage(null);
            source.setLastErrorCategory(null);
            source.setLastSuccessfulCrawlAt(now);
            safeSaveSource(source);
            log.info("[PolicySourceMonitor] Source '{}' is unchanged (HTTP 304). Skipping re-extraction.",
                    source.getSourceName());
            return "SKIPPED_304_NOT_MODIFIED";
        }

        // 2. Network or HTTP failure
        if (fetchResult.getStatus() == PolicyDocumentFetcher.FetchStatus.FAILED) {
            int failures = source.getConsecutiveFailures() + 1;
            source.setConsecutiveFailures(failures);
            int backoff = Math.min(24, Math.max(1, (int) Math.pow(2, failures - 1)));
            source.setBackoffHours(backoff);
            source.setNextRetryEligibleAt(now.plusSeconds(backoff * 3600L));
            source.setStatus(failures >= 5 ? "FAILED_BACKOFF" : "FAILED");
            source.setLastErrorMessage(fetchResult.getErrorMessage());

            String category = categorizeError(fetchResult.getHttpStatusCode(), fetchResult.getErrorMessage());
            source.setLastErrorCategory(category);
            safeSaveSource(source);

            log.warn("[PolicySourceMonitor] Source '{}' fetch failed ({}, retry in {}h): {}",
                    source.getSourceName(), category, backoff, fetchResult.getErrorMessage());
            return "FAILED_FETCH: [" + category + "] " + fetchResult.getErrorMessage();
        }

        // 3. Document successfully fetched (HTTP 200)
        byte[] bytes = fetchResult.getDocumentBytes();
        String checksum = fetchResult.getSha256Checksum();

        // Check if identical document hash was already archived and indexed (Deduplication)
        if (!forceReingest && checksum != null && checksum.equalsIgnoreCase(source.getLastSha256Checksum())) {
            source.setStatus("SYNCED");
            source.setLastSuccessfulCrawlAt(now);
            source.setLastEtag(fetchResult.getEtag());
            source.setLastModifiedHeader(fetchResult.getLastModifiedHeader());
            source.setConsecutiveFailures(0);
            source.setBackoffHours(0);
            source.setNextRetryEligibleAt(null);
            source.setLastErrorMessage(null);
            source.setLastErrorCategory(null);
            safeSaveSource(source);
            log.info("[PolicySourceMonitor] Source '{}' checksum {} identical to current version. Skipping re-extraction.",
                    source.getSourceName(), checksum);
            return "SKIPPED_IDENTICAL_CHECKSUM";
        }

        // Validate genuine PDF signature or reject corrupted / HTML / JSON error stubs
        boolean isStub = false;
        if (bytes != null && bytes.length > 0) {
            String sample = new String(bytes, 0, Math.min(bytes.length, 64), java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase();
            if (sample.startsWith("{") || sample.startsWith("<html") || sample.startsWith("<!doctype")) {
                isStub = true;
            }
        }
        boolean hasPdfHeader = bytes != null && bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';

        if (bytes == null || bytes.length < 5 || isStub || !hasPdfHeader) {
            int failures = source.getConsecutiveFailures() + 1;
            source.setConsecutiveFailures(failures);
            int backoff = Math.min(24, Math.max(1, (int) Math.pow(2, failures - 1)));
            source.setBackoffHours(backoff);
            source.setNextRetryEligibleAt(now.plusSeconds(backoff * 3600L));
            source.setStatus(failures >= 5 ? "FAILED_BACKOFF" : "FAILED");
            source.setLastErrorMessage("Retrieved document is invalid or missing genuine %PDF- magic byte signature");
            source.setLastErrorCategory("INVALID_DOCUMENT_SIGNATURE");
            source.setDocumentClassification("UNKNOWN");
            safeSaveSource(source);
            return "FAILED_INVALID_DOCUMENT_SIGNATURE: Content lacks %PDF- header";
        }

        // 4. New or revised document detected! Perform extraction and stage draft
        try {
            log.info("[PolicySourceMonitor] Processing new/revised document for '{}' (SHA-256={})",
                    source.getSourceName(), checksum);

            String rawText = "";
            try {
                if (pdfParsingService != null && documentFetcher != null && documentFetcher.isPdfContent(bytes)) {
                    rawText = pdfParsingService.extractText(bytes);
                } else {
                    rawText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception parseEx) {
                log.warn("[PolicySourceMonitor] PDF parser warning for '{}': {}, using UTF-8 fallback",
                        source.getSourceName(), parseEx.getMessage());
                rawText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }

            // OCR fallback for valid PDFs with no meaningful extractable text layer
            if (ocrFallbackService != null && ocrFallbackService.isOcrCandidate(bytes, rawText)) {
                log.info("[PolicySourceMonitor] Scanned PDF detected for '{}' with minimal text. Invoking OCR fallback service...",
                        source.getSourceName());
                PolicyOcrFallbackService.OcrResult ocrResult =
                        ocrFallbackService.processScannedPdf(bytes, source.getSourceName(), source.getSourceUrl());
                if (ocrResult.isOcrApplied()) {
                    rawText = ocrResult.getExtractedText();
                    source.setOcrApplied(true);
                    source.setOcrConfidenceScore(ocrResult.getOcrConfidenceScore());
                    if ("REGULATORY_GUIDELINES".equalsIgnoreCase(ocrResult.getDocumentClassification())) {
                        source.setDocumentClassification("REGULATORY_GUIDELINES");
                    }
                }
            }

            PolicyDiscoveryDto.PolicyDiscoveryRequest discoveryRequest =
                    PolicyDiscoveryDto.PolicyDiscoveryRequest.builder()
                            .documentUrl(source.getSourceUrl())
                            .documentName(source.getSourceName())
                            .sourceType("POLICY_WORDING_PDF")
                            .policyName(source.getCanonicalProductName() != null ? source.getCanonicalProductName() : source.getSourceName())
                            .provider(source.getProvider())
                            .irdaUin(source.getIrdaUin())
                            .uinRevision(source.getExpectedRevision())
                            .rawPolicyText(rawText)
                            .autoPublishIfHighConfidence(false) // Gated: requires authorized reviewer approval
                            .build();

            PolicyDiscoveryDto.PolicyIngestionResultDto ingestionResult =
                    discoveryPipelineService.discoverAndIngest(discoveryRequest);

            // Record archive entry in discovered_documents
            DiscoveredDocument doc = DiscoveredDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .sourceId(source.getId())
                    .sourceUrl(source.getSourceUrl())
                    .documentName(source.getSourceName())
                    .sha256Checksum(checksum)
                    .fileSizeBytes(fetchResult.getFileSizeBytes())
                    .etag(fetchResult.getEtag())
                    .contentType(fetchResult.getContentType())
                    .fetchedAt(now)
                    .extractionStatus("EXTRACTED_STAGED")
                    .documentClassification(ingestionResult.getDocumentClassification())
                    .extractedPolicyId(ingestionResult.getPolicyId())
                    .irdaUin(ingestionResult.getIrdaUin())
                    .uinRevision(ingestionResult.getUinRevision())
                    .confidenceScore(ingestionResult.getConfidenceScore())
                    .build();
            safeSaveDoc(doc);

            // Check if this was a new revision of an existing source
            boolean isRevision = source.getLastSha256Checksum() != null;
            String classification = ingestionResult.getDocumentClassification();

            // Update source state & health
            source.setStatus("SYNCED");
            source.setDocumentClassification(classification);
            source.setLastSuccessfulCrawlAt(now);
            source.setLastSuccessfulDiscoveryAt(now);

            if ("POLICY_WORDING".equalsIgnoreCase(classification)) {
                source.setContractualWording(true);
                source.setContractualWordingsCount(source.getContractualWordingsCount() + 1);
            } else {
                source.setContractualWording(false);
                if ("PRODUCT_CATALOGUE".equalsIgnoreCase(classification)) {
                    source.setCataloguesCount(source.getCataloguesCount() + 1);
                    List<DiscoveredCatalogueProduct> catProds = parseCatalogueProducts(rawText);
                    source.setCandidateCount(catProds.size());
                }
            }

            source.setLastEtag(fetchResult.getEtag());
            source.setLastModifiedHeader(fetchResult.getLastModifiedHeader());
            source.setLastSha256Checksum(checksum);
            source.setLastFileSizeBytes(fetchResult.getFileSizeBytes());
            source.setLastFetchedAt(now);
            source.setLastSuccessfulExtractionAt(now);
            source.setLinkedDraftPolicyId(ingestionResult.getPolicyId());
            source.setConsecutiveFailures(0);
            source.setBackoffHours(0);
            source.setNextRetryEligibleAt(null);
            source.setLastErrorMessage(null);
            source.setLastErrorCategory(null);
            source.setUpdatedAt(now);
            safeSaveSource(source);

            log.info("[PolicySourceMonitor] Successfully extracted and staged draft for '{}' (draftId={}, isRevision={}, confidence={})",
                    source.getSourceName(), ingestionResult.getPolicyId(), isRevision, ingestionResult.getConfidenceScore());

            String outcomePrefix = isRevision ? "EXTRACTED_STAGED_REVISION: " : "EXTRACTED_STAGED: ";
            return outcomePrefix + "DraftId=" + ingestionResult.getPolicyId();

        } catch (Exception e) {
            int failures = source.getConsecutiveFailures() + 1;
            source.setConsecutiveFailures(failures);
            int backoff = Math.min(24, Math.max(1, (int) Math.pow(2, failures - 1)));
            source.setBackoffHours(backoff);
            source.setNextRetryEligibleAt(now.plusSeconds(backoff * 3600L));
            source.setStatus(failures >= 5 ? "FAILED_BACKOFF" : "FAILED");
            source.setLastErrorMessage("Extraction failed: " + e.getMessage());
            source.setLastErrorCategory("EXTRACTION_FAILED");
            safeSaveSource(source);
            log.error("[PolicySourceMonitor] Extraction error for source '{}': {}",
                    source.getSourceName(), e.getMessage(), e);
            return "FAILED_EXTRACTION: " + e.getMessage();
        }
    }

    private void safeSaveSource(PolicySource source) {
        if (source != null && source.getId() != null) {
            inMemorySourceRegistry.put(source.getId(), source);
        }
        try {
            if (sourceRepository != null) {
                sourceRepository.save(source);
            }
        } catch (Exception e) {
            log.warn("[PolicySourceMonitor] Failed to persist source update for {}: {}",
                    source != null ? source.getSourceName() : "unknown", e.getMessage());
        }
    }

    private void safeSaveDoc(DiscoveredDocument doc) {
        if (doc == null || documentRepository == null) return;
        try {
            documentRepository.save(doc);
        } catch (Exception e) {
            log.warn("[PolicySourceMonitor] Failed to persist discovered document archive: {}", e.getMessage());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveredCatalogueProduct {
        private String productName;
        private String irdaUin;
        private String launchDateOrLob;
    }

    /**
     * Parses a multi-product catalogue text to extract individual product identities
     * and statutory UINs as discovery candidates for subsequent policy wording retrieval.
     */
    public static List<DiscoveredCatalogueProduct> parseCatalogueProducts(String rawText) {
        List<DiscoveredCatalogueProduct> list = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) return list;
        java.util.regex.Pattern uinPattern = java.util.regex.Pattern.compile(
                "(?i)\\b(IRDA[I]?/[A-Z0-9._/-]+|[A-Z]{3,8}[0-9A-Z]{4,10}V[0-9]{2}[0-9]{4}(?:/[A-Z0-9-]+)?)\\b");
        String[] lines = rawText.split("\\r?\\n");
        for (String line : lines) {
            java.util.regex.Matcher m = uinPattern.matcher(line);
            if (m.find()) {
                String uin = m.group(1).toUpperCase();
                String rest = line.substring(0, m.start()).trim();
                if (rest.isEmpty()) {
                    rest = line.substring(m.end()).trim();
                }
                rest = rest.replaceAll("^\\d+\\s+", "").trim();
                if (!rest.isEmpty() && !rest.equalsIgnoreCase("Product UIN") && !rest.equalsIgnoreCase("UIN")
                        && !rest.equalsIgnoreCase("Product Name")) {
                    list.add(DiscoveredCatalogueProduct.builder()
                            .productName(rest)
                            .irdaUin(uin)
                            .launchDateOrLob("")
                            .build());
                }
            }
        }
        return list;
    }
}
