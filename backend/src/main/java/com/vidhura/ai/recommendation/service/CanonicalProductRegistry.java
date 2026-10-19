package com.vidhura.ai.recommendation.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Canonical Product Registry & Official IRDAI Filing Identifier Resolver.
 *
 * Prevents counterfeit or misattributed filings by validating extracted policy
 * documents against the authoritative registry of health insurance UINs,
 * product names, and revision schedules.
 */
@Slf4j
@Component
public class CanonicalProductRegistry {

    @Data
    @Builder
    public static class CanonicalProductEntry {
        private String irdaUin;
        private String canonicalProductName;
        private String canonicalProvider;
        private String expectedRevision;
        private List<String> seriesKeywords;
        private String category;
        private String filingStatus; // "APPROVED", "ACTIVE", "WITHDRAWN"
    }

    // Authoritative registry of known approved IRDAI retail health insurance products
    private static final Map<String, CanonicalProductEntry> CANONICAL_REGISTRY = new HashMap<>();

    static {
        register(CanonicalProductEntry.builder()
                .irdaUin("HDFHLIP21147V012021")
                .canonicalProductName("Optima Secure")
                .canonicalProvider("HDFC ERGO General Insurance Co. Ltd.")
                .expectedRevision("V012021")
                .seriesKeywords(List.of("optima secure", "optima"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("BAGHLIP21159V022021")
                .canonicalProductName("Health Guard")
                .canonicalProvider("Bajaj Allianz General Insurance Co. Ltd.")
                .expectedRevision("V022021")
                .seriesKeywords(List.of("health guard"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("SHAHLIP21163V012021")
                .canonicalProductName("Star Comprehensive Insurance Policy")
                .canonicalProvider("Star Health and Allied Insurance Co. Ltd.")
                .expectedRevision("V012021")
                .seriesKeywords(List.of("star comprehensive", "comprehensive insurance"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("CHIHLIP21161V012021")
                .canonicalProductName("Care Supreme")
                .canonicalProvider("Care Health Insurance Ltd.")
                .expectedRevision("V012021")
                .seriesKeywords(List.of("care supreme"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("MAXHLIP21165V012021")
                .canonicalProductName("ReAssure 2.0")
                .canonicalProvider("Niva Bupa Health Insurance Co. Ltd.")
                .expectedRevision("V012021")
                .seriesKeywords(List.of("reassure"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("MAXHLIP23036V022223")
                .canonicalProductName("ReAssure 2.0")
                .canonicalProvider("Niva Bupa Health Insurance Co. Ltd.")
                .expectedRevision("V022223")
                .seriesKeywords(List.of("reassure"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("NBHHLIP27054V032627")
                .canonicalProductName("ReAssure 2.0")
                .canonicalProvider("Niva Bupa Health Insurance Co. Ltd.")
                .expectedRevision("V032627")
                .seriesKeywords(List.of("reassure"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("NBHHLIP26051V092526")
                .canonicalProductName("Health Companion")
                .canonicalProvider("Niva Bupa Health Insurance Co. Ltd.")
                .expectedRevision("V092526")
                .seriesKeywords(List.of("health companion", "companion"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("NBHHLIP27053V022627")
                .canonicalProductName("Senior First")
                .canonicalProvider("Niva Bupa Health Insurance Co. Ltd.")
                .expectedRevision("V022627")
                .seriesKeywords(List.of("senior first"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("NBHHLIP27056V042627")
                .canonicalProductName("Arogya Sanjeevani")
                .canonicalProvider("Niva Bupa Health Insurance Co. Ltd.")
                .expectedRevision("V042627")
                .seriesKeywords(List.of("arogya sanjeevani", "sanjeevani"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("ICIHLIP21167V012021")
                .canonicalProductName("Elevate")
                .canonicalProvider("ICICI Lombard General Insurance Co. Ltd.")
                .expectedRevision("V012021")
                .seriesKeywords(List.of("elevate"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("TATHLIP21169V012021")
                .canonicalProductName("Medicare")
                .canonicalProvider("Tata AIG General Insurance Co. Ltd.")
                .expectedRevision("V012021")
                .seriesKeywords(List.of("medicare"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        // Official IRDAI Slash Regulatory Format Products
        register(CanonicalProductEntry.builder()
                .irdaUin("IRDAI/HLT/UII/P-H/V.1/20/2021-22")
                .canonicalProductName("Family Medicare 2021")
                .canonicalProvider("United India Insurance Co. Ltd.")
                .expectedRevision("V.1/20/2021-22")
                .seriesKeywords(List.of("family medicare", "medicare", "united india"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("IRDA/NL-HLT/NIC/P-H/V.I/237/13-14")
                .canonicalProductName("National Mediclaim Policy")
                .canonicalProvider("National Insurance Co. Ltd.")
                .expectedRevision("V.I/237/13-14")
                .seriesKeywords(List.of("national mediclaim", "mediclaim", "national insurance"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        // Live Acquired Official Products from Official Portals
        register(CanonicalProductEntry.builder()
                .irdaUin("UIIHLIP22070V042122")
                .canonicalProductName("Family Medicare Policy")
                .canonicalProvider("United India Insurance Company Limited")
                .expectedRevision("V042122")
                .seriesKeywords(List.of("family medicare", "medicare", "united india"))
                .category("Comprehensive Family Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("NIAHLIP25040V102425")
                .canonicalProductName("New India Mediclaim Policy")
                .canonicalProvider("The New India Assurance Company Limited")
                .expectedRevision("V102425")
                .seriesKeywords(List.of("new india mediclaim", "mediclaim", "new india assurance"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());

        register(CanonicalProductEntry.builder()
                .irdaUin("NIAHLIP21279V032021")
                .canonicalProductName("New India Premier Mediclaim")
                .canonicalProvider("The New India Assurance Company Limited")
                .expectedRevision("V032021")
                .seriesKeywords(List.of("new india premier", "premier mediclaim", "new india assurance"))
                .category("Comprehensive Health Insurance")
                .filingStatus("ACTIVE")
                .build());
    }

    private static void register(CanonicalProductEntry entry) {
        CANONICAL_REGISTRY.put(entry.getIrdaUin().toUpperCase(), entry);
    }

    public boolean isRegistered(String uin) {
        if (uin == null) return false;
        return CANONICAL_REGISTRY.containsKey(uin.trim().toUpperCase());
    }

    public Optional<CanonicalProductEntry> getEntry(String uin) {
        if (uin == null) return Optional.empty();
        return Optional.ofNullable(CANONICAL_REGISTRY.get(uin.trim().toUpperCase()));
    }

    /**
     * Validates an extracted document's identity against the canonical IRDAI registry.
     * Returns a list of identity findings or discrepancies.
     */
    public List<String> validateIdentity(
            String uin,
            String provider,
            String policyName,
            String uinRev,
            String rawText) {

        List<String> discrepancies = new ArrayList<>();
        if (uin == null || uin.isBlank() || uin.startsWith("UNKNOWN_UIN")) {
            discrepancies.add("UNCERTAIN_IRDAI_UIN: No conclusive IRDAI product UIN detected.");
            return discrepancies;
        }

        String cleanUin = uin.trim().toUpperCase();
        CanonicalProductEntry canonical = CANONICAL_REGISTRY.get(cleanUin);

        if (canonical == null) {
            // Unregistered in canonical seed registry
            discrepancies.add("UNREGISTERED_CANONICAL_UIN: Product UIN " + cleanUin
                    + " is not in the canonical registry. Manual verification against IRDAI product registry required.");
            return discrepancies;
        }

        // 1. Validate Product Name against Canonical Product Series
        if (policyName != null && !policyName.isBlank()) {
            String lowerName = policyName.toLowerCase();
            boolean seriesMatch = canonical.getSeriesKeywords().stream()
                    .anyMatch(k -> lowerName.contains(k.toLowerCase()) || canonical.getCanonicalProductName().toLowerCase().contains(lowerName));

            if (!seriesMatch) {
                discrepancies.add(String.format(
                        "CANONICAL_PRODUCT_MISMATCH: IRDAI UIN %s officially belongs to '%s' (%s), but document was filed as '%s'.",
                        cleanUin, canonical.getCanonicalProductName(), canonical.getCanonicalProvider(), policyName));
            }
        }

        // 2. Validate Provider
        if (provider != null && !provider.isBlank()) {
            String lowerProv = provider.toLowerCase();
            String canonicalProvLower = canonical.getCanonicalProvider().toLowerCase();
            boolean providerMatch = canonicalProvLower.contains(lowerProv) || lowerProv.contains(canonicalProvLower.split(" ")[0]);
            if (!providerMatch) {
                discrepancies.add(String.format(
                        "CANONICAL_PROVIDER_MISMATCH: IRDAI UIN %s belongs to '%s', but designated provider is '%s'.",
                        cleanUin, canonical.getCanonicalProvider(), provider));
            }
        }

        // 3. Validate Document Wording Text contains canonical keywords
        if (rawText != null && !rawText.isBlank()) {
            String lowerText = rawText.toLowerCase();
            boolean textHasSeries = canonical.getSeriesKeywords().stream()
                    .anyMatch(k -> lowerText.contains(k.toLowerCase()));
            if (!textHasSeries) {
                discrepancies.add(String.format(
                        "EVIDENCE_PRODUCT_MISMATCH: Official document wording does not cite product series '%s' matching UIN %s.",
                        canonical.getCanonicalProductName(), cleanUin));
            }
        }

        // 4. Validate Revision Code
        if (uinRev != null && !uinRev.isBlank() && canonical.getExpectedRevision() != null) {
            if (!uinRev.equalsIgnoreCase(canonical.getExpectedRevision())) {
                boolean isAddendumOrEndorsement = uinRev.startsWith("A") || uinRev.contains("ADDENDUM") || uinRev.contains("ENDORSEMENT");
                if (isAddendumOrEndorsement) {
                    log.info("[CanonicalProductRegistry] Legitimate contractual addendum/endorsement detected for UIN {}: {}", cleanUin, uinRev);
                } else {
                    discrepancies.add(String.format(
                            "CANONICAL_REVISION_ADVANCED: Extracted revision '%s' is an updated filing relative to registered baseline version '%s'. Requires reviewer confirmation of change impact.",
                            uinRev, canonical.getExpectedRevision()));
                }
            }
        }

        return discrepancies;
    }
}
