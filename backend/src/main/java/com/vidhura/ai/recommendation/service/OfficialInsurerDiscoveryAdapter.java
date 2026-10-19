package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.PolicySource;
import com.vidhura.ai.recommendation.repository.PolicySourceRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded Dynamic Discovery Adapter for Official Insurer and IRDAI Regulatory Portals.
 *
 * Responsibilities:
 * 1. Dynamically scans approved official insurer download pages and product listings.
 * 2. Extracts candidate PDF document links matching policy wording patterns.
 * 3. Enforces strict provenance: domain allowlist, redirect bounds, and SSRF defenses.
 * 4. Decouples candidate discovery from source registration by staging candidates as
 *    PENDING_VALIDATION before promoting them to ACTIVE monitoring.
 * 5. Verifies official PDF signatures (%PDF-) and extracts IRDAI UINs prior to source activation.
 */
@Slf4j
@Service
public class OfficialInsurerDiscoveryAdapter {

    private final PolicyDocumentFetcher documentFetcher;
    private final PolicySourceRepository sourceRepository;

    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_PAGE_DOWNLOAD_BYTES = 2 * 1024 * 1024; // 2 MB ceiling for HTML listing pages
    private static final int MAX_CANDIDATES_PER_PAGE = 30;

    // Approved official discovery root pages
    public static final List<String> APPROVED_DISCOVERY_ROOTS = List.of(
            "https://uiic.co.in/web",
            "https://nationalinsurance.nic.co.in/",
            "https://irdai.gov.in/",
            "https://www.nivabupa.com/",
            "https://www.hdfcergo.com/",
            "https://www.bajajgeneralinsurance.com/",
            "https://www.starhealth.in/",
            "https://www.careinsurance.com/",
            "https://www.icicilombard.com/"
    );

    // Regex for HTML anchor tags
    private static final Pattern ANCHOR_HREF_PATTERN =
            Pattern.compile("(?i)<a\\s+[^>]*href\\s*=\\s*[\"']([^\"'#\\s>]+)[\"'][^>]*>(.*?)</a>", Pattern.DOTALL);

    // Modern Alphanumeric IRDAI UIN format (Private Insurers, e.g. HDFHLIP21147V012021)
    public static final Pattern ALPHANUMERIC_UIN_PATTERN =
            Pattern.compile("(?i)\\b([A-Z0-9]{5,8}[A-Z0-9]{3,5}V[0-9]{6,8})\\b");

    // Statutory Slash-Separated IRDAI Regulatory Identifier (Public Sector / General Insurers, e.g. IRDAI/HLT/UII/P-H/V.1/20/2021-22)
    public static final Pattern SLASH_REGULATORY_UIN_PATTERN =
            Pattern.compile("(?i)\\b(IRDA[I]?/[A-Z0-9-]+/[A-Z0-9-]+/[A-Z0-9.-]+(?:/[A-Z0-9.-]+)+)\\b");

    // Unified IRDAI UIN pattern supporting both formats
    public static final Pattern IRDAI_UIN_PATTERN =
            Pattern.compile("(?i)\\b(IRDA[I]?/[A-Z0-9-]+/[A-Z0-9-]+/[A-Z0-9.-]+(?:/[A-Z0-9.-]+)+|[A-Z0-9]{5,8}[A-Z0-9]{3,5}V[0-9]{6,8})\\b");

    @Data
    @AllArgsConstructor
    public static class ExtractedUinInfo {
        private final String uin;
        private final String revision;
        private final String format; // "ALPHANUMERIC" or "SLASH_REGULATORY"
        private final String insurerCode;
    }

    /**
     * Extracts and validates statutory IRDAI UIN from text or URLs with separate parsing paths
     * for Modern Alphanumeric and Statutory Slash-Separated regulatory identifiers.
     */
    public static ExtractedUinInfo extractUin(String text) {
        if (text == null || text.isBlank()) return null;

        // 1. Separate parsing path for Statutory Slash-Formatted Regulatory Identifiers
        // e.g., IRDAI/HLT/UII/P-H/V.1/20/2021-22 or IRDA/NL-HLT/NIC/P-H/V.I/237/13-14
        Matcher slashMatcher = SLASH_REGULATORY_UIN_PATTERN.matcher(text);
        if (slashMatcher.find()) {
            String uin = slashMatcher.group(1).trim().toUpperCase();
            String[] tokens = uin.split("/");
            String insurerCode = tokens.length >= 3 ? tokens[2] : "";
            String revision = "V.1";
            for (int i = 3; i < tokens.length; i++) {
                if (tokens[i].toUpperCase().startsWith("V.") || tokens[i].toUpperCase().startsWith("V")) {
                    StringBuilder revSb = new StringBuilder();
                    for (int j = i; j < tokens.length; j++) {
                        if (revSb.length() > 0) revSb.append("/");
                        revSb.append(tokens[j]);
                    }
                    revision = revSb.toString();
                    break;
                }
            }
            return new ExtractedUinInfo(uin, revision, "SLASH_REGULATORY", insurerCode);
        }

        // 2. Separate parsing path for Modern Alphanumeric Identifiers
        // e.g., HDFHLIP21147V012021, TATHLIP21169V012021, SHAHLIP21164V012021
        Matcher alphaMatcher = ALPHANUMERIC_UIN_PATTERN.matcher(text);
        if (alphaMatcher.find()) {
            String uin = alphaMatcher.group(1).trim().toUpperCase();
            String insurerCode = uin.length() >= 3 ? uin.substring(0, 3) : "";
            int vIndex = uin.indexOf('V');
            String revision = vIndex != -1 ? uin.substring(vIndex) : "V012021";
            return new ExtractedUinInfo(uin, revision, "ALPHANUMERIC", insurerCode);
        }

        return null;
    }

    @Autowired
    public OfficialInsurerDiscoveryAdapter(
            PolicyDocumentFetcher documentFetcher,
            @Autowired(required = false) PolicySourceRepository sourceRepository) {
        this.documentFetcher = documentFetcher;
        this.sourceRepository = sourceRepository;
    }

    public OfficialInsurerDiscoveryAdapter(PolicyDocumentFetcher documentFetcher) {
        this(documentFetcher, null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainDiagnostics {
        private String rootUrl;
        private String domain;
        private String resolvedIp;
        private int httpStatusCode;
        private String responseMessage;
        private String contentType;
        private String redirectLocation;
        @Builder.Default
        private List<String> redirectChain = new ArrayList<>();
        private String errorCategory; // SUCCESS_200, REDIRECT_FOLLOWED, BOT_PROTECTION_403, OBSOLETE_ENDPOINT_404, MISDIRECTED_OR_BAD_421_400, TIMEOUT_ERROR, SSRF_BLOCKED, CONNECTION_ERROR
        private String sanitizedExcerpt;
        private int linksExtracted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DynamicDiscoveryResult {
        private String discoveryMode; // "LIVE_DISCOVERY", "CATALOG_FALLBACK", "LIVE_DISCOVERY_EMPTY"
        private int pagesScanned;
        private int pagesSuccessfullyFetched;
        private int candidateLinksFound;
        private int candidatesValidatedAndRegistered;
        private int candidatesRejected;
        @Builder.Default
        private List<String> scannedPageUrls = new ArrayList<>();
        @Builder.Default
        private Map<String, Integer> rejectionReasons = new HashMap<>();
        @Builder.Default
        private List<PolicySource> registeredSources = new ArrayList<>();
        @Builder.Default
        private List<String> discoveryLogs = new ArrayList<>();
        @Builder.Default
        private List<DomainDiagnostics> domainDiagnostics = new ArrayList<>();
        private Instant scanCompletedAt;
    }

    /**
     * Executes dynamic live page discovery across approved official root pages or supplied targets.
     */
    public DynamicDiscoveryResult discoverFromLiveOfficialPortals(List<String> targetPages) {
        List<String> pagesToScan = (targetPages != null && !targetPages.isEmpty())
                ? targetPages
                : APPROVED_DISCOVERY_ROOTS;

        int pagesScanned = 0;
        int pagesSuccessfullyFetched = 0;
        int candidateLinksFound = 0;
        int registered = 0;
        int rejected = 0;
        List<String> scannedUrls = new ArrayList<>();
        Map<String, Integer> rejectionReasons = new HashMap<>();
        List<PolicySource> registeredSources = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        List<DomainDiagnostics> domainDiagnostics = new ArrayList<>();

        for (String pageUrl : pagesToScan) {
            if (pageUrl == null || pageUrl.isBlank()) continue;

            pagesScanned++;
            scannedUrls.add(pageUrl);
            logs.add("Scanning official portal page: " + pageUrl);
            log.info("[DiscoveryAdapter] Scanning official portal: {}", pageUrl);

            DomainDiagnostics diag = DomainDiagnostics.builder()
                    .rootUrl(pageUrl)
                    .domain(extractHost(pageUrl))
                    .redirectChain(new ArrayList<>())
                    .build();

            // 1. Fetch HTML content with diagnostics from official page
            String htmlContent = fetchHtmlPageWithDiagnostics(pageUrl, logs, rejectionReasons, diag);
            domainDiagnostics.add(diag);

            if (htmlContent == null || htmlContent.isBlank()) {
                continue;
            }

            pagesSuccessfullyFetched++;

            // 2. Extract candidate policy PDF links
            List<String> candidateUrls = extractCandidateUrlsFromHtml(pageUrl, htmlContent);
            diag.setLinksExtracted(candidateUrls.size());
            candidateLinksFound += candidateUrls.size();
            logs.add("Extracted " + candidateUrls.size() + " candidate document links from " + pageUrl);

            // 3. Validate and stage each candidate
            for (String candidateUrl : candidateUrls) {
                ValidationOutcome outcome = validateAndStageCandidate(candidateUrl, pageUrl);
                if (outcome.isValid()) {
                    registered++;
                    registeredSources.add(outcome.getRegisteredSource());
                    logs.add("Successfully registered validated source: " + outcome.getRegisteredSource().getSourceName()
                            + " (UIN=" + outcome.getRegisteredSource().getIrdaUin() + ")");
                } else {
                    rejected++;
                    recordRejection(rejectionReasons, outcome.getRejectionReason());
                    logs.add("Candidate rejected (" + candidateUrl + "): " + outcome.getRejectionReason());
                }
            }
        }

        // Tri-State Outcome:
        // - LIVE_DISCOVERY: official pages were fetched and candidates were actually extracted and registered.
        // - LIVE_DISCOVERY_EMPTY: live pages were successfully scanned, but no eligible new candidates were found.
        // - CATALOG_FALLBACK: live discovery failed (portals unreachable or rejected with bot blocks/403).
        String mode;
        if (registered > 0) {
            mode = "LIVE_DISCOVERY";
        } else if (pagesSuccessfullyFetched > 0) {
            mode = "LIVE_DISCOVERY_EMPTY";
        } else {
            mode = "CATALOG_FALLBACK";
        }

        return DynamicDiscoveryResult.builder()
                .discoveryMode(mode)
                .pagesScanned(pagesScanned)
                .pagesSuccessfullyFetched(pagesSuccessfullyFetched)
                .scannedPageUrls(scannedUrls)
                .candidateLinksFound(candidateLinksFound)
                .candidatesValidatedAndRegistered(registered)
                .candidatesRejected(rejected)
                .rejectionReasons(rejectionReasons)
                .registeredSources(registeredSources)
                .discoveryLogs(logs)
                .domainDiagnostics(domainDiagnostics)
                .scanCompletedAt(Instant.now())
                .build();
    }

    /**
     * Extracts candidate PDF links from HTML text with bounds checking.
     */
    public List<String> extractCandidateUrlsFromHtml(String baseUrl, String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) return List.of();

        List<String> candidateUrls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        URI baseUri = null;
        try {
            baseUri = URI.create(baseUrl.trim());
        } catch (Exception e) {
            log.warn("[DiscoveryAdapter] Invalid base URL: {}", baseUrl);
            return List.of();
        }

        Matcher matcher = ANCHOR_HREF_PATTERN.matcher(htmlContent);
        while (matcher.find() && candidateUrls.size() < MAX_CANDIDATES_PER_PAGE) {
            String href = matcher.group(1).trim();
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:") || href.startsWith("mailto:")) {
                continue;
            }

            // Resolve relative link
            String resolvedUrl;
            try {
                resolvedUrl = baseUri.resolve(href).toString();
            } catch (Exception e) {
                continue;
            }

            // Filter for policy document patterns within approved official insurer domains
            String lower = resolvedUrl.toLowerCase();
            boolean isPolicyDocCandidate = lower.contains(".pdf")
                    || lower.contains("policy-wording")
                    || lower.contains("policy_wording")
                    || lower.contains("policywordings")
                    || lower.contains("prospectus")
                    || lower.contains("customer-information-sheet")
                    || lower.contains("list-of-retail-products")
                    || lower.contains("list-of-products");

            String extractedHost = null;
            try {
                extractedHost = URI.create(resolvedUrl).getHost();
            } catch (Exception ignored) {}

            final String host = extractedHost;
            boolean isApprovedDomain = host != null && PolicySourceMonitor.APPROVED_OFFICIAL_DOMAINS.stream()
                    .anyMatch(d -> host.equalsIgnoreCase(d) || host.toLowerCase().endsWith("." + d));

            if (isPolicyDocCandidate && isApprovedDomain && seen.add(resolvedUrl)) {
                candidateUrls.add(resolvedUrl);
            }
        }

        return candidateUrls;
    }

    /**
     * Validates a candidate URL:
     * - Domain Allowlist & Hostname boundary
     * - SSRF / Private IP protection
     * - HTTP Scheme
     * - Deduplication check against already registered sources
     * - PDF signature verification (%PDF-)
     * - IRDAI UIN and product identity validation
     */
    public ValidationOutcome validateAndStageCandidate(String candidateUrl, String discoveredFromUrl) {
        if (candidateUrl == null || candidateUrl.isBlank()) {
            return ValidationOutcome.rejected("EMPTY_URL: Candidate URL is null or blank");
        }

        URI uri;
        try {
            uri = URI.create(candidateUrl.trim());
        } catch (Exception e) {
            return ValidationOutcome.rejected("MALFORMED_URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return ValidationOutcome.rejected("UNSUPPORTED_SCHEME: Only HTTP and HTTPS are permitted");
        }

        String host = uri.getHost();
        if (host == null || !PolicySourceMonitor.APPROVED_OFFICIAL_DOMAINS.stream()
                .anyMatch(domain -> host.equalsIgnoreCase(domain) || host.toLowerCase().endsWith("." + domain))) {
            return ValidationOutcome.rejected("UNTRUSTED_DOMAIN: Host '" + host + "' is not on the approved insurer allowlist");
        }

        if (documentFetcher != null && documentFetcher.isBlockedHostOrIp(host)) {
            return ValidationOutcome.rejected("SSRF_RESTRICTED_HOST: Target host resolves to a private or cloud metadata IP");
        }

        // Deduplication check
        if (sourceRepository != null && sourceRepository.existsBySourceUrl(candidateUrl)) {
            return ValidationOutcome.rejected("DUPLICATE_SOURCE: URL is already registered in source catalog");
        }

        // Pre-stage candidate as PENDING_VALIDATION
        PolicySource stagedCandidate = PolicySource.builder()
                .id("src-dyn-" + UUID.randomUUID().toString().substring(0, 8))
                .sourceName(deriveCandidateName(candidateUrl, host))
                .provider(deriveProviderFromHost(host))
                .canonicalProductName(deriveProductNameFromUrl(candidateUrl))
                .sourceUrl(candidateUrl)
                .documentType("POLICY_WORDING")
                .sourceAuthority("OFFICIAL_INSURER_WEBSITE")
                .status("PENDING_VALIDATION")
                .active(false) // Inactive until validated
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Download document header/bytes to verify real PDF signature and extract identity
        if (documentFetcher != null) {
            PolicyDocumentFetcher.FetchResult fetchResult = documentFetcher.fetchDocument(candidateUrl, null, null);
            if (fetchResult.getStatus() == PolicyDocumentFetcher.FetchStatus.FAILED) {
                return ValidationOutcome.rejected("FETCH_FAILED: " + fetchResult.getErrorMessage());
            }

            byte[] bytes = fetchResult.getDocumentBytes();
            if (bytes == null || bytes.length < 5) {
                return ValidationOutcome.rejected("EMPTY_DOCUMENT: Retrieved 0 bytes from candidate URL");
            }

            if (!documentFetcher.isPdfContent(bytes)) {
                return ValidationOutcome.rejected("INVALID_DOCUMENT_SIGNATURE: Content does not begin with %PDF- magic bytes");
            }

            // Extract document text sample (first 16 KB) for identity verification
            String sampleText = new String(bytes, 0, Math.min(bytes.length, 16384), StandardCharsets.UTF_8);

            // 1. Freshness check: Reject withdrawn or discontinued product filings
            String upperSample = sampleText.toUpperCase();
            if (upperSample.contains("WITHDRAWN PRODUCT") || upperSample.contains("POLICY DISCONTINUED") || upperSample.contains("OBSOLETE FILING")) {
                return ValidationOutcome.rejected("DISCONTINUED_POLICY: Document indicates discontinued or withdrawn product filing");
            }

            // 2. Extract statutory IRDAI UIN using separate parsing paths for alphanumeric and slash regulatory formats
            ExtractedUinInfo uinInfo = extractUin(sampleText);
            if (uinInfo == null) {
                uinInfo = extractUin(candidateUrl);
            }

            if (uinInfo == null) {
                return ValidationOutcome.rejected("MISSING_IRDAI_UIN: Policy document must contain a statutory IRDAI UIN (alphanumeric or slash-formatted)");
            }
            String extractedUin = uinInfo.getUin();
            stagedCandidate.setIrdaUin(extractedUin);

            // 3. Verify Insurer Provider alignment with IRDAI UIN prefix
            String provider = stagedCandidate.getProvider();
            if (!PolicyDiscoveryPipelineService.validateInsurerUinPrefix(provider, extractedUin)) {
                return ValidationOutcome.rejected("INSURER_UIN_MISMATCH: Insurer entity '" + provider + "' does not match IRDAI UIN prefix in " + extractedUin);
            }

            // 4. Extract revision from UIN or document
            stagedCandidate.setExpectedRevision(uinInfo.getRevision());

            // 5. Extract canonical product name from document title if available
            Matcher prodMatcher = Pattern.compile("(?i)(?:policy\\s+name|product\\s+name|policy\\s+wording(?:s)?\\s+for)\\s*[:\\-]?\\s*([A-Za-z0-9\\s:]{4,40})").matcher(sampleText);
            if (prodMatcher.find()) {
                String refinedName = prodMatcher.group(1).trim();
                if (!refinedName.isBlank()) {
                    stagedCandidate.setCanonicalProductName(refinedName);
                }
            }

            // Candidate successfully verified! Promote to ACTIVE
            stagedCandidate.setStatus("ACTIVE");
            stagedCandidate.setActive(true);
            stagedCandidate.setLastSha256Checksum(fetchResult.getSha256Checksum());
            stagedCandidate.setLastFileSizeBytes(fetchResult.getFileSizeBytes());
            stagedCandidate.setLastEtag(fetchResult.getEtag());
        } else {
            // In mocking/testing environment without network fetcher, mark ACTIVE if structurally valid
            stagedCandidate.setStatus("ACTIVE");
            stagedCandidate.setActive(true);
        }

        return ValidationOutcome.valid(stagedCandidate);
    }

    private static final int MAX_PAGE_REDIRECT_HOPS = 3;

    private String extractHost(String url) {
        if (url == null) return "unknown";
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String fetchHtmlPage(String urlStr, List<String> logs, Map<String, Integer> rejections) {
        DomainDiagnostics diag = DomainDiagnostics.builder().rootUrl(urlStr).domain(extractHost(urlStr)).build();
        return fetchHtmlPageWithDiagnostics(urlStr, logs, rejections, diag);
    }

    private String fetchHtmlPageWithDiagnostics(
            String urlStr, List<String> logs, Map<String, Integer> rejections, DomainDiagnostics diag) {
        return fetchHtmlPageWithRedirects(urlStr, 0, logs, rejections, diag);
    }

    private String fetchHtmlPageWithRedirects(
            String urlStr, int hopCount, List<String> logs, Map<String, Integer> rejections, DomainDiagnostics diag) {
        if (hopCount > MAX_PAGE_REDIRECT_HOPS) {
            recordRejection(rejections, "REDIRECT_LIMIT_EXCEEDED: Exceeded max " + MAX_PAGE_REDIRECT_HOPS + " hops");
            if (diag != null) {
                diag.setErrorCategory("REDIRECT_LIMIT_EXCEEDED");
                diag.setSanitizedExcerpt("Exceeded maximum redirect limit of " + MAX_PAGE_REDIRECT_HOPS + " hops");
            }
            return null;
        }

        URI uri;
        try {
            uri = URI.create(urlStr.trim());
        } catch (Exception e) {
            recordRejection(rejections, "MALFORMED_PAGE_URL");
            if (diag != null) {
                diag.setErrorCategory("MALFORMED_PAGE_URL");
                diag.setSanitizedExcerpt("Malformed URL: " + e.getMessage());
            }
            return null;
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            recordRejection(rejections, "UNSUPPORTED_SCHEME");
            if (diag != null) {
                diag.setErrorCategory("UNSUPPORTED_SCHEME");
                diag.setSanitizedExcerpt("Invalid scheme: " + scheme);
            }
            return null;
        }

        String host = uri.getHost();
        if (host == null || !PolicySourceMonitor.APPROVED_OFFICIAL_DOMAINS.stream()
                .anyMatch(d -> host.equalsIgnoreCase(d) || host.toLowerCase().endsWith("." + d))) {
            recordRejection(rejections, "UNAPPROVED_PAGE_DOMAIN: " + host);
            if (diag != null) {
                diag.setErrorCategory("UNAPPROVED_PAGE_DOMAIN");
                diag.setSanitizedExcerpt("Host not on approved insurer allowlist: " + host);
            }
            return null;
        }

        java.net.InetAddress destinationIp = null;
        if (documentFetcher != null) {
            try {
                destinationIp = documentFetcher.resolveAndValidateSafeDestinationIp(host);
                if (diag != null && diag.getResolvedIp() == null) {
                    diag.setResolvedIp(destinationIp.getHostAddress());
                }
            } catch (Exception se) {
                recordRejection(rejections, "SSRF_PAGE_HOST_BLOCKED: " + host + " (" + se.getMessage() + ")");
                if (diag != null) {
                    diag.setErrorCategory("SSRF_BLOCKED");
                    diag.setSanitizedExcerpt("SSRF blocked destination host: " + se.getMessage());
                }
                return null;
            }
        }

        HttpURLConnection conn = null;
        try {
            if (documentFetcher != null && destinationIp != null) {
                conn = documentFetcher.openPinnedConnection(uri, destinationIp);
            } else {
                URL url = uri.toURL();
                conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            }
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false); // Security: Manual redirect inspection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 (VidhuraAI-OfficialDiscovery/1.0; +https://vidhura.ai; regulatory-compliance@vidhura.ai)");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            int status = conn.getResponseCode();
            String msg = conn.getResponseMessage();
            String contentType = conn.getContentType();

            if (diag != null) {
                diag.setHttpStatusCode(status);
                diag.setResponseMessage(msg);
                diag.setContentType(contentType);
            }

            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String redirect = conn.getHeaderField("Location");
                if (redirect != null && !redirect.isBlank()) {
                    URI redirectUri = uri.resolve(redirect);
                    String redirHost = redirectUri.getHost();
                    if (diag != null) {
                        diag.setRedirectLocation(redirectUri.toString());
                        if (diag.getRedirectChain() == null) diag.setRedirectChain(new ArrayList<>());
                        diag.getRedirectChain().add(urlStr + " -> (" + status + ") " + redirectUri);
                    }
                    if (redirHost == null || !PolicySourceMonitor.APPROVED_OFFICIAL_DOMAINS.stream()
                            .anyMatch(d -> redirHost.equalsIgnoreCase(d) || redirHost.toLowerCase().endsWith("." + d))) {
                        recordRejection(rejections, "UNAPPROVED_REDIRECT_DESTINATION: " + redirHost);
                        if (diag != null) {
                            diag.setErrorCategory("UNAPPROVED_REDIRECT_DESTINATION");
                            diag.setSanitizedExcerpt("Redirect target '" + redirHost + "' is outside approved official insurer domain allowlist");
                        }
                        return null;
                    }
                    if (documentFetcher != null && documentFetcher.isBlockedHostOrIp(redirHost)) {
                        recordRejection(rejections, "SSRF_REDIRECT_DESTINATION_BLOCKED: " + redirHost);
                        if (diag != null) {
                            diag.setErrorCategory("SSRF_BLOCKED");
                            diag.setSanitizedExcerpt("SSRF Protection blocked redirect destination host: " + redirHost);
                        }
                        return null;
                    }
                    logs.add("Following safe redirect (" + status + ") to: " + redirectUri);
                    return fetchHtmlPageWithRedirects(redirectUri.toString(), hopCount + 1, logs, rejections, diag);
                } else {
                    recordRejection(rejections, "EMPTY_REDIRECT_LOCATION");
                    if (diag != null) {
                        diag.setErrorCategory("EMPTY_REDIRECT_LOCATION");
                        diag.setSanitizedExcerpt("HTTP " + status + " redirect with missing Location header");
                    }
                    return null;
                }
            }

            if (status == 403) {
                recordRejection(rejections, "PAGE_HTTP_403");
                if (diag != null) {
                    diag.setErrorCategory("BOT_PROTECTION_403");
                    diag.setSanitizedExcerpt("HTTP 403 Forbidden: Insurer portal edge protected by bot management (e.g. Akamai/Cloudflare)");
                }
                return null;
            }

            if (status == 404) {
                recordRejection(rejections, "PAGE_HTTP_404");
                if (diag != null) {
                    diag.setErrorCategory("OBSOLETE_ENDPOINT_404");
                    diag.setSanitizedExcerpt("HTTP 404 Not Found: Discovery endpoint path is obsolete or moved on insurer portal");
                }
                return null;
            }

            if (status == 400 || status == 421) {
                recordRejection(rejections, "PAGE_HTTP_" + status);
                if (diag != null) {
                    diag.setErrorCategory("MISDIRECTED_OR_BAD_421_400");
                    diag.setSanitizedExcerpt("HTTP " + status + ": Edge routing or host header mismatch rejected request");
                }
                return null;
            }

            if (status != 200) {
                recordRejection(rejections, "PAGE_HTTP_" + status);
                if (diag != null) {
                    diag.setErrorCategory("HTTP_" + status);
                    diag.setSanitizedExcerpt("HTTP " + status + " (" + msg + ")");
                }
                return null;
            }

            // HTTP 200 Success
            if (diag != null) {
                diag.setErrorCategory("SUCCESS_200");
            }

            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                int total = 0;
                while ((n = reader.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                    total += n;
                    if (total > MAX_PAGE_DOWNLOAD_BYTES) break;
                }
                String content = sb.toString();
                if (diag != null) {
                    String cleanExcerpt = content.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                    if (cleanExcerpt.length() > 200) cleanExcerpt = cleanExcerpt.substring(0, 200) + "...";
                    diag.setSanitizedExcerpt("HTTP 200 OK: " + cleanExcerpt);
                }
                return content;
            }
        } catch (java.net.SocketTimeoutException te) {
            log.warn("[DiscoveryAdapter] Timeout fetching {}: {}", urlStr, te.getMessage());
            recordRejection(rejections, "PAGE_TIMEOUT");
            if (diag != null) {
                diag.setErrorCategory("TIMEOUT_ERROR");
                diag.setSanitizedExcerpt("SocketTimeoutException: Connection or read timed out after " + READ_TIMEOUT_MS + "ms");
            }
            return null;
        } catch (Exception e) {
            log.warn("[DiscoveryAdapter] Network error fetching {}: {}", urlStr, e.getMessage());
            recordRejection(rejections, "PAGE_NETWORK_ERROR: " + e.getMessage());
            if (diag != null) {
                diag.setErrorCategory("CONNECTION_ERROR");
                diag.setSanitizedExcerpt(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String deriveCandidateName(String url, String host) {
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (filename.toLowerCase().endsWith(".pdf")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        filename = filename.replace('-', ' ').replace('_', ' ');
        return capitalizeWords(filename) + " (" + host + ")";
    }

    public static String deriveProductNameFromUrl(String url) {
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (filename.toLowerCase().endsWith(".pdf")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        return capitalizeWords(filename.replace('-', ' ').replace('_', ' '));
    }

    public static String deriveProviderFromHost(String host) {
        if (host == null) return "Official Insurer";
        String lower = host.toLowerCase();
        if (lower.contains("hdfcergo")) return "HDFC ERGO General Insurance Co. Ltd.";
        if (lower.contains("starhealth")) return "Star Health and Allied Insurance Co. Ltd.";
        if (lower.contains("careinsurance")) return "Care Health Insurance Ltd.";
        if (lower.contains("nivabupa")) return "Niva Bupa Health Insurance Co. Ltd.";
        if (lower.contains("icicilombard")) return "ICICI Lombard General Insurance Co. Ltd.";
        if (lower.contains("bajajallianz") || lower.contains("bajajgeneralinsurance")) return "Bajaj Allianz General Insurance Co. Ltd.";
        if (lower.contains("tataaig")) return "Tata AIG General Insurance Co. Ltd.";
        if (lower.contains("uiic")) return "United India Insurance Co. Ltd.";
        if (lower.contains("nationalinsurance")) return "National Insurance Co. Ltd.";
        if (lower.contains("newindia")) return "The New India Assurance Co. Ltd.";
        if (lower.contains("orientalinsurance")) return "The Oriental Insurance Co. Ltd.";
        if (lower.contains("irdai")) return "Insurance Regulatory and Development Authority of India";
        return "Official Insurer (" + host + ")";
    }

    public static String capitalizeWords(String text) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void recordRejection(Map<String, Integer> map, String reason) {
        if (map != null && reason != null) {
            map.put(reason, map.getOrDefault(reason, 0) + 1);
        }
    }

    @Data
    @AllArgsConstructor
    public static class ValidationOutcome {
        private final boolean valid;
        private final PolicySource registeredSource;
        private final String rejectionReason;

        public static ValidationOutcome valid(PolicySource source) {
            return new ValidationOutcome(true, source, null);
        }

        public static ValidationOutcome rejected(String reason) {
            return new ValidationOutcome(false, null, reason);
        }
    }
}
