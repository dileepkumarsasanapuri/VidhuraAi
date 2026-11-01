package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.PolicyDiscoveryCandidate;
import com.vidhura.ai.recommendation.model.PolicyDiscoveryRun;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.repository.PolicyDiscoveryCandidateRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentRevisionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 5.6: Official Source Discovery Engine.
 *
 * Inspects registered insurer and regulatory source pages, extracts candidate product links,
 * normalizes UINs, detects revisions vs duplicates, and persists candidates into MongoDB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialSourceDiscoveryEngine {

    private final OfficialSourceAcquisitionService acquisitionService;
    private final PolicyDiscoveryCandidateRepository candidateRepository;
    private final PolicyDocumentRevisionRepository revisionRepository;

    // Pattern for HTML anchor tags
    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "(?i)<a\\s+[^>]*href\\s*=\\s*[\"']([^\"'#\\s>]+)[\"'][^>]*>(.*?)</a>",
            Pattern.DOTALL
    );

    // Alphanumeric IRDAI UIN: e.g. HDFHLIP21503V012021 or NICHLIP21262V022021
    private static final Pattern ALPHANUMERIC_UIN_PATTERN = Pattern.compile(
            "(?i)\\b([A-Z0-9]{5,8}[A-Z0-9]{3,5}V[0-9]{6,8})\\b"
    );

    // Statutory Slash UIN: e.g. IRDAI/HLT/UII/P-H/V.1/20/2021-22
    private static final Pattern SLASH_UIN_PATTERN = Pattern.compile(
            "(?i)\\b(IRDA[I]?/[A-Z0-9._/-]+)\\b"
    );

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveredItem {
        private String rawTitle;
        private String documentUrl;
        private String irdaUin;
        private String revision;
        private String insurerCode;
        private String productName;
        private boolean isRevisedRevision;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryScanSummary {
        private String sourceUrl;
        private int linksFound;
        private int newCandidates;
        private int updatedRevisions;
        private int duplicatesSkipped;
        private String status;
        private String errorMessage;
    }

    /**
     * Discovers candidates from an official source URL and its HTML content.
     */
    public List<DiscoveredItem> extractCandidatesFromHtml(String baseUrl, String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return Collections.emptyList();
        }

        List<DiscoveredItem> items = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        URI baseUri;
        try {
            baseUri = URI.create(baseUrl.trim());
        } catch (Exception e) {
            log.warn("[DiscoveryEngine] Invalid base URL: {}", baseUrl);
            return Collections.emptyList();
        }

        Matcher matcher = ANCHOR_PATTERN.matcher(htmlContent);
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            String linkText = matcher.group(2).replaceAll("<[^>]*>", "").trim();

            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:") || href.startsWith("mailto:")) {
                continue;
            }

            String resolvedUrl;
            try {
                resolvedUrl = baseUri.resolve(href).toString();
            } catch (Exception e) {
                continue;
            }

            String lower = resolvedUrl.toLowerCase();
            boolean isPolicyDoc = lower.endsWith(".pdf")
                    || lower.contains(".pdf?")
                    || lower.contains("policy-wording")
                    || lower.contains("policy_wording")
                    || lower.contains("prospectus")
                    || lower.contains("customer-information-sheet")
                    || lower.contains("product-wording");

            if (!isPolicyDoc || !seenUrls.add(resolvedUrl)) {
                continue;
            }

            // Extract UIN from linkText or URL
            String fullText = linkText + " " + href;
            String uin = null;
            String revision = "V01";
            Matcher uinMatcher = ALPHANUMERIC_UIN_PATTERN.matcher(fullText);
            if (uinMatcher.find()) {
                uin = uinMatcher.group(1).toUpperCase();
                int vIdx = uin.indexOf('V');
                if (vIdx != -1) {
                    revision = uin.substring(vIdx);
                }
            } else {
                Matcher slashMatcher = SLASH_UIN_PATTERN.matcher(fullText);
                if (slashMatcher.find()) {
                    uin = slashMatcher.group(1).toUpperCase();
                    revision = "V.1";
                }
            }

            String productName = cleanProductName(linkText, resolvedUrl);
            String insurerCode = deriveInsurerCode(productName, resolvedUrl, uin);

            items.add(DiscoveredItem.builder()
                    .rawTitle(linkText)
                    .documentUrl(resolvedUrl)
                    .irdaUin(uin)
                    .revision(revision)
                    .insurerCode(insurerCode)
                    .productName(productName)
                    .build());
        }

        return items;
    }

    /**
     * Scans an official source, dedupes candidates, and persists new/revised candidates in MongoDB.
     */
    public DiscoveryScanSummary processSource(String sourceUrl, String htmlContent, String runId) {
        List<DiscoveredItem> discovered = extractCandidatesFromHtml(sourceUrl, htmlContent);

        int newCount = 0;
        int revisedCount = 0;
        int dupCount = 0;

        for (DiscoveredItem item : discovered) {
            String uin = item.getIrdaUin();
            String rev = item.getRevision();
            String normalizedCode = buildNormalizedCode(item.getInsurerCode(), item.getProductName());

            // 1. Check if exact document revision or identical URL already exists
            boolean isExistingRev = (uin != null && revisionRepository.existsByIrdaUinAndUinRevision(uin, rev));
            boolean isExistingCandidate = (uin != null && candidateRepository.existsByIrdaUinAndUinRevision(uin, rev));

            if (isExistingRev || isExistingCandidate) {
                dupCount++;
                continue;
            }

            // 2. Check if product exists with older revision -> Updated Revision Candidate!
            boolean isProductKnown = (uin != null && candidateRepository.findByIrdaUin(uin).isPresent());

            PolicyDiscoveryCandidate candidate = PolicyDiscoveryCandidate.builder()
                    .insurerCode(item.getInsurerCode())
                    .insurerName(deriveInsurerName(item.getInsurerCode()))
                    .productName(item.getProductName())
                    .normalizedProductCode(normalizedCode)
                    .irdaUin(uin)
                    .uinRevision(rev)
                    .sourceUrl(sourceUrl)
                    .finalAcquisitionUrl(item.getDocumentUrl())
                    .discoveryRunId(runId)
                    .discoveredAt(Instant.now())
                    .lifecycleStage("ACQUISITION_PENDING")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            if (isProductKnown) {
                candidate.getAuditFindings().add("Updated revision detected for known product: " + uin + " (" + rev + ")");
                revisedCount++;
            } else {
                candidate.getAuditFindings().add("New policy product candidate discovered: " + item.getProductName());
                newCount++;
            }

            candidateRepository.save(candidate);
        }

        return DiscoveryScanSummary.builder()
                .sourceUrl(sourceUrl)
                .linksFound(discovered.size())
                .newCandidates(newCount)
                .updatedRevisions(revisedCount)
                .duplicatesSkipped(dupCount)
                .status("COMPLETED")
                .build();
    }

    public String buildNormalizedCode(String insurerCode, String productName) {
        String base = (insurerCode != null ? insurerCode.toUpperCase() : "GEN") + "_"
                + (productName != null ? productName.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase() : "POLICY");
        // Trim trailing underscores
        while (base.endsWith("_")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String cleanProductName(String linkText, String url) {
        if (linkText != null && !linkText.isBlank() && linkText.length() > 4) {
            String cleaned = linkText.replaceAll("(?i)\\b(policy wording|policy wordings|prospectus|cis|download|pdf)\\b", "").trim();
            if (!cleaned.isBlank()) return cleaned;
        }
        // Extract filename without .pdf
        String filename = url.substring(url.lastIndexOf('/') + 1);
        int qIdx = filename.indexOf('?');
        if (qIdx != -1) filename = filename.substring(0, qIdx);
        if (filename.toLowerCase().endsWith(".pdf")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        return filename.replace('-', ' ').replace('_', ' ').trim();
    }

    public String deriveInsurerCode(String productName, String url, String uin) {
        if (uin != null && uin.length() >= 3) {
            String prefix = uin.substring(0, 3).toUpperCase();
            if (prefix.startsWith("HDF")) return "HDFC_ERGO";
            if (prefix.startsWith("SHA")) return "STAR_HEALTH";
            if (prefix.startsWith("CHI") || prefix.startsWith("REL")) return "CARE_HEALTH";
            if (prefix.startsWith("MAX") || prefix.startsWith("NBH")) return "NIVA_BUPA";
            if (prefix.startsWith("ABH")) return "ADITYA_BIRLA";
            if (prefix.startsWith("ICI")) return "ICICI_LOMBARD";
            if (prefix.startsWith("BAJ") || prefix.startsWith("BAG")) return "BAG";
            if (prefix.startsWith("TAT")) return "TATA_AIG";
            if (prefix.startsWith("MCI")) return "MANIPAL_CIGNA";
            if (prefix.startsWith("NIA")) return "NIA";
            if (prefix.startsWith("UII")) return "UIIC";
            if (prefix.startsWith("NIC")) return "NIC";
            if (prefix.startsWith("OIC")) return "OIC";
        }
        String combined = (productName + " " + url).toLowerCase();
        if (combined.contains("hdfc")) return "HDFC_ERGO";
        if (combined.contains("star health")) return "STAR_HEALTH";
        if (combined.contains("care health")) return "CARE_HEALTH";
        if (combined.contains("niva") || combined.contains("max bupa")) return "NIVA_BUPA";
        if (combined.contains("aditya birla")) return "ADITYA_BIRLA";
        if (combined.contains("icici")) return "ICICI_LOMBARD";
        if (combined.contains("bajaj")) return "BAG";
        if (combined.contains("tata")) return "TATA_AIG";
        if (combined.contains("manipal") || combined.contains("cigna")) return "MANIPAL_CIGNA";
        if (combined.contains("new india")) return "NIA";
        if (combined.contains("united india") || combined.contains("uiic")) return "UIIC";
        if (combined.contains("national insurance")) return "NIC";
        if (combined.contains("oriental insurance")) return "OIC";
        return "UNKNOWN_INSURER";
    }

    private String deriveInsurerName(String insurerCode) {
        if (insurerCode == null) return "Official Insurer";
        return switch (insurerCode) {
            case "HDFC_ERGO" -> "HDFC ERGO General Insurance Co. Ltd.";
            case "STAR_HEALTH" -> "Star Health and Allied Insurance Co. Ltd.";
            case "CARE_HEALTH" -> "Care Health Insurance Ltd.";
            case "NIVA_BUPA" -> "Niva Bupa Health Insurance Co. Ltd.";
            case "ADITYA_BIRLA" -> "Aditya Birla Health Insurance Co. Ltd.";
            case "ICICI_LOMBARD" -> "ICICI Lombard General Insurance Co. Ltd.";
            case "BAG" -> "Bajaj Allianz General Insurance Co. Ltd.";
            case "TATA_AIG" -> "Tata AIG General Insurance Co. Ltd.";
            case "MANIPAL_CIGNA" -> "ManipalCigna Health Insurance Co. Ltd.";
            case "NIA" -> "The New India Assurance Co. Ltd.";
            case "UIIC" -> "United India Insurance Co. Ltd.";
            case "NIC" -> "National Insurance Co. Ltd.";
            case "OIC" -> "The Oriental Insurance Co. Ltd.";
            default -> "Official Insurer (" + insurerCode + ")";
        };
    }
}
