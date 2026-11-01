package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.ExternalEvidenceRecord;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult;
import com.vidhura.ai.recommendation.model.ExternalEvidenceValidationResult.ValidationFinding;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Phase 5.3: Policy-Governed Automated Evidence Acceptance Rules Engine.
 *
 * Implements a strict, versioned governance policy defining:
 * 1. Explicitly eligible evidence categories for automated acceptance (structured statutory returns).
 * 2. Mandatory machine-checkable verification gates (provenance, hash, bounds, citations, calculations).
 * 3. Immediate routing to human review for ambiguities, conflicts, or qualitative policy clauses.
 * 4. Immediate quarantine/rejection for cryptographic hash mismatches or errant filings.
 *
 * Strict Provenance Rule:
 * Automated acceptance records approvalMode = AUTO_ACCEPTED and verifiedBy = null (or SYSTEM).
 * An automated rule NEVER fabricates a human reviewer email or claims a human approved something.
 */
@Component
@Slf4j
public class EvidenceAcceptanceGovernancePolicy {

    public static final String GOVERNANCE_RULE_VERSION = "2026.1-phase5.4";
    private static final String STATUTORY_DIR = "src/main/resources/evidence/statutory/";

    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    // Allowlisted authoritative official statutory domains
    private static final Set<String> ALLOWED_OFFICIAL_DOMAINS = Set.of(
            "irdai.gov.in",
            "www.irdai.gov.in",
            "cioins.co.in",
            "www.cioins.co.in",
            "uiic.co.in",
            "www.uiic.co.in",
            "newindia.co.in",
            "www.newindia.co.in",
            "gicouncil.in",
            "www.gicouncil.in",
            "policyholder.gov.in",
            "www.policyholder.gov.in",
            "financialservices.gov.in",
            "www.financialservices.gov.in",
            "nationalinsurance.nic.co.in",
            "orientalinsurance.org.in",
            "hdfcergo.com",
            "www.hdfcergo.com",
            "starhealth.in",
            "www.starhealth.in",
            "careinsurance.com",
            "www.careinsurance.com",
            "icicilombard.com",
            "www.icicilombard.com",
            "nivabupa.com",
            "www.nivabupa.com",
            "adityabirlacapital.com",
            "localhost",
            "127.0.0.1"
    );

    // Eligible structured statutory metric categories permitted for automated acceptance
    private static final Set<String> AUTO_ACCEPTABLE_METRIC_CATEGORIES = Set.of(
            "INCURRED_CLAIM_RATIO",
            "SETTLEMENT_RATIO_LESS_3_MONTHS",
            "SETTLEMENT_RATIO_ANNUAL",
            "SOLVENCY_RATIO",
            "COMPLAINTS_PER_10K_CLAIMS",
            "OMBUDSMAN_DISPOSAL_RATE"
    );

    // Recognized statutory insurer codes
    private static final Set<String> STATUTORY_INSURERS = Set.of(
            "NEW_INDIA_ASSURANCE",
            "UNITED_INDIA_INSURANCE",
            "NATIONAL_INSURANCE",
            "ORIENTAL_INSURANCE",
            "HDFC_ERGO",
            "STAR_HEALTH",
            "CARE_HEALTH",
            "ICICI_LOMBARD",
            "NIVA_BUPA",
            "ADITYA_BIRLA"
    );

    // Errant hash identified in forensic audits that must be permanently disqualified
    public static final String ERRANT_CIO_REPORT_HASH = "b6727c6d66e7bb844512c262e31505c065f462a677ca3e7f41d33ea64724a4f8";

    // Known physical statutory documents mapped to SHA-256
    private static final Map<String, String> HASH_TO_FILENAME = Map.of(
            "00f261acca2ea564118303747c4c8b8e89aeb1eba1a8686dfd97b3648366dbfb", "IRDAI_Annual_Report_2022-23.pdf",
            "3b787a46e3039e07e1d7828804fdf0475b7163a530b0ffd0a841ec38ba3fb435", "NIA_Public_Disclosures_March_2023.pdf",
            "de3f04d382e7702aa2b5e57b7de997b9b6b0f0b9696c3b5b25da247d3f108d75", "CIO_Annual_Report_2023-24.pdf",
            "d88e53dbca0e2a3d409653d65a41d0432fac4f4eaa67a711b907524ef9905d94", "CIO_Annual_Report_2024-25.pdf",
            "1548d76c7d3dd56cc5d32f132bfda30e933bfccbbdc591933667870cde6d45dd", "CIO_Annual_Report_2022-23.pdf",
            "dc05b394b70b3065d39037c39a0830373dfe50e1f52d6298dd4b9a03836ef6a7", "UIIC_NL26_Solvency_Margin_March_2023.pdf",
            "1462afedf13c237f9fa21b59725182ca97fd934ce188b18bbff135de5121dd33", "UIIC_NL37_Claims_Data_March_2023.pdf",
            "962ad2ae611c3c3c5369df1d991a899fa85148fa031b24d027d518132c4f8bb4", "UIIC_NL39_Ageing_Of_Claims_March_2023.pdf",
            "fdc70264ce307e0c0ead2de442881c907a03a638477c03a7aaf473d3cc139134", "IRDAI_Master_Circular_Health_Insurance_2024.pdf"
    );

    @Data
    @Builder
    public static class GovernanceEvaluationResult {
        private boolean autoAccepted;
        private String decision; // ACCEPTED, PENDING, REJECTED, QUARANTINED
        private String approvalMode; // AUTO_ACCEPTED, PENDING_REVIEW, REJECTED
        private String publicationStatus; // PUBLISHED_ELIGIBLE, STAGED_INTERNAL, QUARANTINED
        private String verificationStatus; // VERIFIED_STATUTORY, UNVERIFIED_PENDING_REVIEW, REJECTED
        private String governanceRuleVersion;
        private String decisionReason;
        private List<String> passedMandatoryChecks;
        private List<String> failedMandatoryChecks;
        private boolean requiresHumanReview;
    }

    /**
     * Evaluates an evidence record against the versioned automated acceptance policy.
     */
    public GovernanceEvaluationResult evaluateRecord(
            ExternalEvidenceRecord record,
            ExternalEvidenceValidationResult validationResult,
            List<ExternalEvidenceRecord> existingSiblingRecords) {

        List<String> passedChecks = new ArrayList<>();
        List<String> failedChecks = new ArrayList<>();

        // 1. Category Eligibility Check
        boolean isEligibleCategory = record.getMetricCategory() != null
                && AUTO_ACCEPTABLE_METRIC_CATEGORIES.contains(record.getMetricCategory());
        if (isEligibleCategory) {
            passedChecks.add("CHECK_CATEGORY_ELIGIBILITY");
        } else {
            failedChecks.add("DISQUALIFIED_CATEGORY: Metric category '" + record.getMetricCategory() + "' requires qualitative human review");
        }

        // 2. Cryptographic Hash Format & File Integrity Check
        String cleanHash = record.getSourceDocumentHash() != null
                ? record.getSourceDocumentHash().replaceAll("(?i)^sha256:", "").trim()
                : "";

        boolean validHashFormat = SHA256_HEX_PATTERN.matcher(cleanHash).matches();
        boolean isErrantHash = ERRANT_CIO_REPORT_HASH.equalsIgnoreCase(cleanHash);

        if (!validHashFormat) {
            failedChecks.add("CRITICAL_HASH_FORMAT: Missing or malformed 64-character SHA-256 digest ('" + cleanHash + "')");
        } else if (isErrantHash) {
            failedChecks.add("CRITICAL_ERRANT_HASH: Document hash matches known ghost filing without physical backing");
        } else {
            // Check physical disk bytes from cached artifact or mapped statutory file
            File physFile = null;
            if (record.getArtifactStoragePath() != null && !record.getArtifactStoragePath().isBlank()) {
                File candidate = new File(record.getArtifactStoragePath());
                if (candidate.exists() && candidate.isFile()) {
                    physFile = candidate;
                }
            }
            if (physFile == null) {
                String mappedFile = HASH_TO_FILENAME.get(cleanHash.toLowerCase());
                if (mappedFile != null) {
                    File candidate = new File(STATUTORY_DIR + mappedFile);
                    if (candidate.exists() && candidate.isFile()) {
                        physFile = candidate;
                    }
                }
            }

            if (physFile != null) {
                String actualHash = computeFileSha256(physFile);
                if (actualHash.equalsIgnoreCase(cleanHash)) {
                    passedChecks.add("CHECK_CRYPTOGRAPHIC_HASH: Verified 64-char hex against physical bytes of " + physFile.getName());
                } else {
                    failedChecks.add("CRITICAL_HASH_MISMATCH: Physical file digest " + actualHash + " does not match record hash " + cleanHash);
                }
            } else {
                failedChecks.add("DISQUALIFIED_UNVERIFIED_ARTIFACT: Source disclosure requires verified local physical artifact; no cached bytes match digest");
            }
        }

        // Freshness Lifecycle Check
        String freshness = record.getFreshnessStatus();
        if ("STALE_REVALIDATION_REQUIRED".equalsIgnoreCase(freshness)) {
            failedChecks.add("DISQUALIFIED_STALE_EVIDENCE: Source artifact is past freshness validity window; revalidation required");
        } else if ("ACQUISITION_FAILED".equalsIgnoreCase(freshness)) {
            failedChecks.add("DISQUALIFIED_ACQUISITION_FAILED: Source artifact acquisition failed");
        } else {
            passedChecks.add("CHECK_EVIDENCE_FRESHNESS: Freshness status is " + (freshness != null ? freshness : "FRESH"));
        }

        // 3. Source Allowlist Check
        boolean domainAllowlisted = false;
        if (record.getSourceUrl() != null && !record.getSourceUrl().isBlank()) {
            try {
                URI uri = URI.create(record.getSourceUrl());
                String host = uri.getHost();
                if (host != null && ALLOWED_OFFICIAL_DOMAINS.contains(host.toLowerCase())) {
                    domainAllowlisted = true;
                    passedChecks.add("CHECK_PROVENANCE_DOMAIN: Source URL host '" + host + "' is allowlisted");
                }
            } catch (Exception ignored) { }
        }
        if (!domainAllowlisted) {
            failedChecks.add("DISQUALIFIED_DOMAIN: Source URL is not on official statutory allowlist ('" + record.getSourceUrl() + "')");
        }

        // 4. Insurer Mapping Check
        boolean insurerRecognized = record.getInsurerCode() != null
                && STATUTORY_INSURERS.contains(record.getInsurerCode().toUpperCase().trim());
        if (insurerRecognized) {
            passedChecks.add("CHECK_INSURER_CODE: Recognized statutory insurer code '" + record.getInsurerCode() + "'");
        } else {
            failedChecks.add("DISQUALIFIED_INSURER: Unmapped or unrecognized insurer code '" + record.getInsurerCode() + "'");
        }

        // 5. Reporting Period Validity Check
        boolean validPeriod = record.getReportingPeriod() != null
                && !record.getReportingPeriod().isBlank()
                && !record.getReportingPeriod().equalsIgnoreCase("UNVERIFIED");
        if (validPeriod) {
            passedChecks.add("CHECK_REPORTING_PERIOD: Audited reporting period '" + record.getReportingPeriod() + "' valid");
        } else {
            failedChecks.add("DISQUALIFIED_PERIOD: Reporting period missing or unverified");
        }

        // 6. Citation Presence Check
        boolean hasCitation = record.getCitationTableReference() != null && !record.getCitationTableReference().isBlank();
        if (hasCitation) {
            passedChecks.add("CHECK_CITATION_PRESENCE: Statutory table/form reference specified");
        } else {
            failedChecks.add("DISQUALIFIED_CITATION: Mandatory citation table/form reference missing");
        }

        // 7. Schema & Numerical Sanity Bounds Check
        boolean numericalWithinBounds = false;
        if (record.getNumericValue() != null) {
            double val = record.getNumericValue().doubleValue();
            switch (record.getMetricCategory() != null ? record.getMetricCategory() : "") {
                case "INCURRED_CLAIM_RATIO":
                    numericalWithinBounds = (val >= 20.0 && val <= 250.0);
                    break;
                case "SETTLEMENT_RATIO_LESS_3_MONTHS":
                case "SETTLEMENT_RATIO_ANNUAL":
                    numericalWithinBounds = (val >= 50.0 && val <= 100.0);
                    break;
                case "SOLVENCY_RATIO":
                    numericalWithinBounds = (val >= 0.80 && val <= 10.0);
                    break;
                case "COMPLAINTS_PER_10K_CLAIMS":
                    numericalWithinBounds = (val >= 0.0 && val <= 1000.0);
                    break;
                case "OMBUDSMAN_DISPOSAL_RATE":
                    numericalWithinBounds = (val >= 0.0 && val <= 100.0);
                    break;
                default:
                    numericalWithinBounds = true;
                    break;
            }
        }
        if (numericalWithinBounds) {
            passedChecks.add("CHECK_NUMERICAL_BOUNDS: Value " + record.getNumericValue() + " within statutory sanity bounds");
        } else {
            failedChecks.add("DISQUALIFIED_NUMERICAL_BOUNDS: Value " + record.getNumericValue() + " exceeds plausible statutory thresholds");
        }

        // 8. Deterministic Derived Calculation Check
        if (record.isDerived()) {
            boolean formulaValid = record.getCalculationFormula() != null && !record.getCalculationFormula().isBlank();
            boolean formulaReproduced = false;
            if (formulaValid && record.getNumerator() != null && record.getDenominator() != null && record.getDenominator().compareTo(BigDecimal.ZERO) > 0) {
                double computed = record.getNumerator().divide(record.getDenominator(), 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0;
                double stated = record.getNumericValue() != null ? record.getNumericValue().doubleValue() : -999.0;
                formulaReproduced = Math.abs(computed - stated) <= 0.1;
            }
            if (formulaValid && formulaReproduced) {
                passedChecks.add("CHECK_DETERMINISTIC_CALCULATION: Formula reproduced within tolerance");
            } else {
                failedChecks.add("DISQUALIFIED_CALCULATION: Derived formula missing or does not match stated metric value");
            }
        } else {
            passedChecks.add("CHECK_DETERMINISTIC_CALCULATION: Direct extraction from official table");
        }

        // 9. Validation Findings Gate
        boolean hasBlockerOrError = false;
        if (validationResult != null && validationResult.getFindings() != null) {
            hasBlockerOrError = validationResult.getFindings().stream()
                    .anyMatch(f -> "BLOCKER".equals(f.getSeverity()) || "ERROR".equals(f.getSeverity()));
        }
        if (!hasBlockerOrError) {
            passedChecks.add("CHECK_NO_BLOCKING_FINDINGS: Zero BLOCKER or ERROR validation findings");
        } else {
            failedChecks.add("DISQUALIFIED_VALIDATION_FINDINGS: One or more BLOCKER/ERROR severity findings present");
        }

        // 10. Conflict & Sibling Collision Check
        boolean hasConflict = false;
        if (existingSiblingRecords != null && !existingSiblingRecords.isEmpty()) {
            for (ExternalEvidenceRecord sib : existingSiblingRecords) {
                if (sib.getId() != null && sib.getId().equals(record.getId())) continue;
                if (sib.getRevision() == record.getRevision()
                        && sib.getNumericValue() != null && record.getNumericValue() != null
                        && sib.getNumericValue().compareTo(record.getNumericValue()) != 0) {
                    hasConflict = true;
                    break;
                }
            }
        }
        if (!hasConflict) {
            passedChecks.add("CHECK_CONFLICT_DETECTION: No conflicting verified sibling filings");
        } else {
            failedChecks.add("DISQUALIFIED_CONFLICT: Conflicting metric value detected for same revision and period");
        }

        // =========================================================================
        // FINAL DECISION DETERMINATION
        // =========================================================================

        boolean isHardFailure = failedChecks.stream().anyMatch(c -> c.startsWith("CRITICAL_"));

        if (isHardFailure) {
            String primaryFailure = failedChecks.stream().filter(c -> c.startsWith("CRITICAL_")).findFirst().orElse("Critical integrity check failed");
            return GovernanceEvaluationResult.builder()
                    .autoAccepted(false)
                    .decision("REJECTED")
                    .approvalMode("REJECTED")
                    .publicationStatus("QUARANTINED")
                    .verificationStatus("REJECTED")
                    .governanceRuleVersion(GOVERNANCE_RULE_VERSION)
                    .decisionReason(primaryFailure)
                    .passedMandatoryChecks(passedChecks)
                    .failedMandatoryChecks(failedChecks)
                    .requiresHumanReview(false)
                    .build();
        }

        boolean allMandatoryPassed = failedChecks.isEmpty();

        if (allMandatoryPassed) {
            return GovernanceEvaluationResult.builder()
                    .autoAccepted(true)
                    .decision("ACCEPTED")
                    .approvalMode("AUTO_ACCEPTED")
                    .publicationStatus("PUBLISHED_ELIGIBLE")
                    .verificationStatus("VERIFIED_STATUTORY")
                    .governanceRuleVersion(GOVERNANCE_RULE_VERSION)
                    .decisionReason("Automatically accepted under Governance Policy " + GOVERNANCE_RULE_VERSION + ": passed all " + passedChecks.size() + " mandatory checks.")
                    .passedMandatoryChecks(passedChecks)
                    .failedMandatoryChecks(Collections.emptyList())
                    .requiresHumanReview(false)
                    .build();
        } else {
            String reasons = String.join("; ", failedChecks);
            return GovernanceEvaluationResult.builder()
                    .autoAccepted(false)
                    .decision("PENDING")
                    .approvalMode("PENDING_REVIEW")
                    .publicationStatus("STAGED_INTERNAL")
                    .verificationStatus("UNVERIFIED_PENDING_REVIEW")
                    .governanceRuleVersion(GOVERNANCE_RULE_VERSION)
                    .decisionReason("Routed to human review queue: " + reasons)
                    .passedMandatoryChecks(passedChecks)
                    .failedMandatoryChecks(failedChecks)
                    .requiresHumanReview(true)
                    .build();
        }
    }

    private String computeFileSha256(File file) {
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (Exception e) {
            log.warn("[GovernancePolicy] Error hashing file {}: {}", file.getName(), e.getMessage());
            return "";
        }
    }
}
