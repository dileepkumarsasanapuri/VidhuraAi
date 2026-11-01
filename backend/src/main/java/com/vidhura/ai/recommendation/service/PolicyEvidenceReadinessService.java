package com.vidhura.ai.recommendation.service;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import com.vidhura.ai.recommendation.model.PolicyDocumentExtraction;
import com.vidhura.ai.recommendation.model.PolicyDocumentRevision;
import com.vidhura.ai.recommendation.repository.IrdaiRegistryRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentExtractionRepository;
import com.vidhura.ai.recommendation.repository.PolicyDocumentRevisionRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 6.4: Policy Evidence Readiness Assessment Service.
 *
 * Implements deterministic policy-level evidence readiness gates based on product category
 * and verified contractual terms. Keeps evidence readiness strictly separated from recommendation ranking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEvidenceReadinessService {

    private final PolicyDocumentExtractionRepository extractionRepository;
    private final PolicyDocumentRevisionRepository revisionRepository;
    private final IrdaiRegistryRepository registryRepository;

    /**
     * Evaluates deterministic policy-level readiness for recommendation.
     *
     * @param documentRevisionId or document SHA-256
     * @return PolicyEvidenceReadinessResult
     */
    public PolicyEvidenceReadinessResult evaluatePolicyReadiness(String documentIdentifier) {
        if (documentIdentifier == null || documentIdentifier.isBlank()) {
            throw new IllegalArgumentException("Document identifier must be provided.");
        }

        String cleanId = documentIdentifier.trim();
        Optional<PolicyDocumentRevision> revOpt = revisionRepository.findById(cleanId);
        if (revOpt.isEmpty() && cleanId.length() == 64) {
            revOpt = revisionRepository.findByVerifiedSha256(cleanId.toLowerCase());
        }

        String revisionId = revOpt.map(PolicyDocumentRevision::getId).orElse(cleanId);
        String docSha = revOpt.map(PolicyDocumentRevision::getVerifiedSha256)
                .orElse(cleanId.length() == 64 ? cleanId.toLowerCase() : null);
        String registryRecordId = revOpt.map(PolicyDocumentRevision::getRegistryRecordId).orElse(null);
        String canonicalUin = revOpt.map(PolicyDocumentRevision::getIrdaUin).orElse(null);

        String productCategory = "INDIVIDUAL";
        String productName = "Unknown Product";
        String insurerName = "Unknown Insurer";

        if (registryRecordId != null) {
            Optional<IrdaiRegistryRecord> regOpt = registryRepository.findById(registryRecordId);
            if (regOpt.isPresent()) {
                IrdaiRegistryRecord reg = regOpt.get();
                if (reg.getClassificationStatus() != null && !reg.getClassificationStatus().isBlank()) {
                    productCategory = reg.getClassificationStatus();
                } else if (reg.getNormalizedCategory() != null) {
                    productCategory = reg.getNormalizedCategory();
                }
                if (canonicalUin == null) canonicalUin = reg.getNormalizedUin();
                if (reg.getProductName() != null) productName = reg.getProductName();
                if (reg.getInsurerName() != null) insurerName = reg.getInsurerName();
            }
        }

        // Fetch all extractions for this document
        List<PolicyDocumentExtraction> extractions = extractionRepository.findAllByDocumentRevisionId(revisionId);
        if (extractions.isEmpty() && docSha != null) {
            extractions = extractionRepository.findAllByDocumentSha256(docSha);
        }

        // Determine required fields for this product category
        List<String> requiredFields = getRequiredFieldsForCategory(productCategory);

        Map<String, List<PolicyDocumentExtraction>> byField = extractions.stream()
                .collect(Collectors.groupingBy(PolicyDocumentExtraction::getExtractedFieldName));

        List<FieldReadinessEvaluation> fieldEvals = new ArrayList<>();
        List<String> missingOrUnverified = new ArrayList<>();
        List<String> conflictingFields = new ArrayList<>();
        int verifiedCount = 0;
        int notApplicableCount = 0;

        for (String field : requiredFields) {
            List<PolicyDocumentExtraction> fieldExts = byField.getOrDefault(field, Collections.emptyList());

            boolean hasUnresolvedConflict = fieldExts.stream().anyMatch(e -> Boolean.TRUE.equals(e.getHasConflict()));
            boolean isVerified = fieldExts.stream().anyMatch(e -> "VERIFIED_REVIEWER".equalsIgnoreCase(e.getVerificationStatus()));
            boolean isNotApplicable = fieldExts.stream().anyMatch(e -> "NOT_APPLICABLE".equalsIgnoreCase(e.getExtractionStatus()));

            String verifiedValue = null;
            if (isVerified) {
                for (PolicyDocumentExtraction e : fieldExts) {
                    if ("VERIFIED_REVIEWER".equalsIgnoreCase(e.getVerificationStatus())) {
                        verifiedValue = e.getCandidateValue();
                        break;
                    }
                }
            }

            FieldReadinessEvaluation eval = FieldReadinessEvaluation.builder()
                    .fieldName(field)
                    .required(true)
                    .verified(isVerified)
                    .notApplicable(isNotApplicable)
                    .hasConflict(hasUnresolvedConflict)
                    .candidateCount(fieldExts.size())
                    .verifiedValue(verifiedValue)
                    .build();

            fieldEvals.add(eval);

            if (hasUnresolvedConflict) {
                conflictingFields.add(field);
            }

            if (isVerified) {
                verifiedCount++;
            } else if (isNotApplicable) {
                notApplicableCount++;
            } else {
                missingOrUnverified.add(field);
            }
        }

        // Deterministic readiness decision
        boolean ready;
        String readinessStatus;

        if (!conflictingFields.isEmpty()) {
            ready = false;
            readinessStatus = "BLOCKED_BY_CONFLICT";
        } else if (!missingOrUnverified.isEmpty()) {
            ready = false;
            readinessStatus = "INSUFFICIENT_VERIFIED_EVIDENCE";
        } else {
            ready = true;
            readinessStatus = "READY_FOR_RECOMMENDATION";
        }

        int satisfiedCount = verifiedCount + notApplicableCount;
        double completeness = requiredFields.isEmpty() ? 0.0 : ((double) satisfiedCount / requiredFields.size()) * 100.0;

        return PolicyEvidenceReadinessResult.builder()
                .documentRevisionId(revisionId)
                .documentSha256(docSha)
                .canonicalUin(canonicalUin)
                .productName(productName)
                .insurerName(insurerName)
                .productCategory(productCategory)
                .readyForRecommendation(ready)
                .readinessStatus(readinessStatus)
                .totalRequiredFields(requiredFields.size())
                .verifiedFieldsCount(verifiedCount)
                .notApplicableCount(notApplicableCount)
                .completenessScore(Math.round(completeness * 10.0) / 10.0)
                .fieldEvaluations(fieldEvals)
                .missingOrUnverifiedFields(missingOrUnverified)
                .conflictingFields(conflictingFields)
                .evaluatedAt(Instant.now())
                .build();
    }

    /**
     * Determines the mandatory contractual fields required to approve recommendation readiness.
     */
    public List<String> getRequiredFieldsForCategory(String category) {
        String clean = category != null ? category.toUpperCase().trim() : "INDIVIDUAL";

        switch (clean) {
            case "INDIVIDUAL":
            case "FAMILY_FLOATER":
            case "SENIOR_CITIZEN":
                // Retail Comprehensive Health
                return List.of(
                        "SUM_INSURED_TIERS",
                        "ROOM_RENT_LIMIT",
                        "ICU_CHARGES_LIMIT",
                        "COPAY_PERCENTAGE",
                        "WAITING_PERIOD_INITIAL_DAYS",
                        "WAITING_PERIOD_PED_MONTHS",
                        "PERMANENT_EXCLUSIONS",
                        "RESTORATION_BENEFIT"
                );

            case "GROUP_HEALTH":
                return List.of(
                        "SUM_INSURED_TIERS",
                        "ROOM_RENT_LIMIT",
                        "COPAY_PERCENTAGE",
                        "PERMANENT_EXCLUSIONS"
                );

            case "PERSONAL_ACCIDENT":
                return List.of(
                        "SUM_INSURED_TIERS",
                        "POLICY_DURATION_YEARS",
                        "PERMANENT_EXCLUSIONS"
                );

            case "TRAVEL":
                return List.of(
                        "SUM_INSURED_TIERS",
                        "POLICY_DURATION_YEARS",
                        "PERMANENT_EXCLUSIONS"
                );

            case "BENEFIT_BASED":
            case "CRITICAL_ILLNESS":
                return List.of(
                        "SUM_INSURED_TIERS",
                        "WAITING_PERIOD_INITIAL_DAYS",
                        "PERMANENT_EXCLUSIONS"
                );

            case "ADD_ON":
            case "RIDER":
                return List.of(
                        "SUM_INSURED_TIERS",
                        "PERMANENT_EXCLUSIONS"
                );

            default:
                return List.of(
                        "SUM_INSURED_TIERS",
                        "ROOM_RENT_LIMIT",
                        "COPAY_PERCENTAGE",
                        "PERMANENT_EXCLUSIONS"
                );
        }
    }

    @Data
    @Builder
    public static class PolicyEvidenceReadinessResult {
        private String documentRevisionId;
        private String documentSha256;
        private String canonicalUin;
        private String productName;
        private String insurerName;
        private String productCategory;
        private boolean readyForRecommendation;
        private String readinessStatus; // READY_FOR_RECOMMENDATION, INSUFFICIENT_VERIFIED_EVIDENCE, BLOCKED_BY_CONFLICT
        private int totalRequiredFields;
        private int verifiedFieldsCount;
        private int notApplicableCount;
        private double completenessScore;
        private List<FieldReadinessEvaluation> fieldEvaluations;
        private List<String> missingOrUnverifiedFields;
        private List<String> conflictingFields;
        private Instant evaluatedAt;
    }

    @Data
    @Builder
    public static class FieldReadinessEvaluation {
        private String fieldName;
        private boolean required;
        private boolean verified;
        private boolean notApplicable;
        private boolean hasConflict;
        private int candidateCount;
        private String verifiedValue;
    }
}
