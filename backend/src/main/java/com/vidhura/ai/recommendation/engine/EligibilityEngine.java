package com.vidhura.ai.recommendation.engine;

import com.vidhura.ai.recommendation.dto.RecommendationRequestDto;
import com.vidhura.ai.recommendation.model.RecommendationProfile;
import com.vidhura.ai.recommendation.model.StructuredPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Eligibility Engine — deterministic mandatory-requirement filter.
 * Applies hard eligibility rules from the structured policy catalog
 * against the user's profile to disqualify ineligible products BEFORE scoring.
 *
 * Design principle: this engine uses only verified product facts,
 * never AI-generated guesses.
 */
@Slf4j
@Component
public class EligibilityEngine {

    /**
     * Filters the full policy catalog down to only those products the user is eligible for.
     * Returns a list of eligible policies paired with any warnings.
     */
    public List<EligibilityResult> filterEligible(
            RecommendationProfile profile,
            List<StructuredPolicy> catalog) {

        log.info("[EligibilityEngine] Evaluating {} catalog policies for user profile (age={}, type={}, city={})",
                catalog.size(), profile.getPrimaryAge(), profile.getInsuranceType(), profile.getCityOrPincode());

        List<EligibilityResult> results = new ArrayList<>();

        for (StructuredPolicy policy : catalog) {
            if (!policy.isActive()) {
                continue;
            }

            List<String> failReasons = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            StructuredPolicy.EligibilityRules rules = policy.getEligibility();
            if (rules == null) {
                failReasons.add("Eligibility cannot be assessed — missing contractual terms in policy record. (Not a confirmed rejection by insurer).");
                results.add(EligibilityResult.builder()
                        .policy(policy)
                        .eligible(false)
                        .eligibilityUnknown(true)
                        .eligibilityStatus("UNASSESSED_MISSING_DATA")
                        .failReasons(failReasons)
                        .warnings(warnings)
                        .build());
                continue;
            }


            // 0. Independent Category Routing (Section 3 & 8)
            String targetCat = profile.getTargetCategory() != null && !profile.getTargetCategory().isBlank()
                    ? profile.getTargetCategory().toUpperCase()
                    : "RETAIL_MEDICAL";

            String policyCat = policy.getCanonicalCategory();
            if (!"ALL".equals(targetCat) && !targetCat.equals(policyCat)) {
                failReasons.add(String.format("Product category '%s' does not match requested recommendation category '%s'. Incompatible insurance categories are isolated from each other.", policyCat, targetCat));
            }

            // 0a. Rider / Add-on Parent Product Dependency Check
            if ("RIDER".equals(policyCat) && (profile.getBasePolicyCode() == null || profile.getBasePolicyCode().isBlank())) {
                failReasons.add("Rider and add-on products cannot be recommended standalone without an eligible, verified base policy relationship.");
            }

            // 0b. Group Health Eligibility Check
            if ("GROUP_HEALTH".equals(policyCat)) {
                if (profile.getGroupSize() == null || profile.getGroupSize() < 7) {
                    failReasons.add(String.format("Group Health Insurance requires a formal group structure with at least 7 eligible members as per IRDAI group regulations (provided group size: %s).",
                            profile.getGroupSize() != null ? profile.getGroupSize() : "unspecified"));
                }
            }

            // 1. Primary Adult Age Check
            if (profile.getPrimaryAge() < rules.getMinAdultAge()) {
                failReasons.add(String.format("Primary insured age %d is below minimum %d",
                        profile.getPrimaryAge(), rules.getMinAdultAge()));
            }
            if (profile.getPrimaryAge() > rules.getMaxAdultAge()) {
                failReasons.add(String.format("Primary insured age %d exceeds maximum entry age %d",
                        profile.getPrimaryAge(), rules.getMaxAdultAge()));
            }

            // 2. Family Type Check
            if (rules.getAllowedFamilyTypes() != null && !rules.getAllowedFamilyTypes().isEmpty()) {
                String reqType = profile.getInsuranceType();
                boolean matches = rules.getAllowedFamilyTypes().contains(reqType);
                if (!matches && ("FAMILY_FLOATER".equalsIgnoreCase(reqType) || "FLOATER".equalsIgnoreCase(reqType))) {
                    matches = rules.getAllowedFamilyTypes().contains("SELF_AND_SPOUSE")
                            || rules.getAllowedFamilyTypes().contains("FAMILY_WITH_CHILDREN");
                }
                if (!matches) {
                    failReasons.add(String.format("Family type '%s' is not supported. Allowed: %s",
                            profile.getInsuranceType(), String.join(", ", rules.getAllowedFamilyTypes())));
                }
            }

            // 3. Member Age Checks (each member must meet age bounds)
            if (profile.getMembers() != null) {
                int adultCount = 1; // primary
                int childCount = 0;

                for (RecommendationProfile.InsuredMember member : profile.getMembers()) {
                    String rel = member.getRelation().toUpperCase();
                    if (rel.contains("CHILD")) {
                        childCount++;
                        if (member.getAge() > rules.getMaxChildAgeYears()) {
                            failReasons.add(String.format("Child member '%s' age %d exceeds child max age %d",
                                    member.getDisplayName() != null ? member.getDisplayName() : rel,
                                    member.getAge(), rules.getMaxChildAgeYears()));
                        }
                    } else {
                        adultCount++;
                        if (member.getAge() < rules.getMinAdultAge() || member.getAge() > rules.getMaxAdultAge()) {
                            failReasons.add(String.format("Adult member '%s' age %d is outside allowed range %d-%d",
                                    member.getDisplayName() != null ? member.getDisplayName() : rel,
                                    member.getAge(), rules.getMinAdultAge(), rules.getMaxAdultAge()));
                        }
                    }
                }

                if (rules.getMaxAdults() > 0 && adultCount > rules.getMaxAdults()) {
                    failReasons.add(String.format("Too many adults (%d). Maximum allowed: %d", adultCount, rules.getMaxAdults()));
                }
                if (rules.getMaxChildren() > 0 && childCount > rules.getMaxChildren()) {
                    failReasons.add(String.format("Too many children (%d). Maximum allowed: %d", childCount, rules.getMaxChildren()));
                }
            }

            // 4. Sum Insured Availability Check
            long desiredSI = profile.getDesiredSumInsured();
            if (desiredSI > 0) {
                if (rules.getMinSumInsured() > 0 && desiredSI < rules.getMinSumInsured()) {
                    failReasons.add(String.format("Desired sum insured ₹%,d is below minimum available ₹%,d",
                            desiredSI, rules.getMinSumInsured()));
                }
                if (rules.getMaxSumInsured() > 0 && desiredSI > rules.getMaxSumInsured()) {
                    warnings.add(String.format("Desired sum insured ₹%,d exceeds maximum ₹%,d. Will evaluate at maximum.",
                            desiredSI, rules.getMaxSumInsured()));
                }
            }

            // 5. Zone Check (if zone rules exist)
            if (rules.getAllowedZones() != null && !rules.getAllowedZones().isEmpty()
                    && profile.getDetectedZone() != null && !profile.getDetectedZone().isBlank()) {
                if (!rules.getAllowedZones().contains(profile.getDetectedZone())) {
                    warnings.add(String.format("Your zone '%s' may have different pricing. Allowed zones: %s",
                            profile.getDetectedZone(), String.join(", ", rules.getAllowedZones())));
                }
            }

            // 6. User Specific Requirement Checks (Maternity, OPD, Copay)
            StructuredPolicy.ContractTerms terms = policy.getContractTerms();
            if (terms != null) {
                if (profile.isMaternityBenefitNeeded() && !terms.isMaternityCovered()) {
                    warnings.add("Notice: You requested maternity coverage, but this plan does not offer maternity or newborn benefits.");
                }
                if (profile.isOpdBenefitNeeded() && !terms.isOpdCovered()) {
                    warnings.add("Notice: You requested OPD coverage, but this plan does not cover outpatient consultations.");
                }
                if ("NONE".equalsIgnoreCase(profile.getCopayTolerance()) && terms.getCopayPercentage() > 0) {
                    warnings.add(String.format("Notice: This plan carries a %.0f%% mandatory co-payment despite your preference for zero co-payment.",
                            terms.getCopayPercentage()));
                }
            }

            // 7. Strict Budget Hard Filter (Phase 3 Hard Filter)
            if (("STRICT".equalsIgnoreCase(profile.getBudgetType()) || "MAX_HARD_LIMIT".equalsIgnoreCase(profile.getBudgetType()))
                    && profile.getAnnualBudget() > 0) {
                double estPremium = RankingEngine.estimatePremium(policy, profile);
                if (estPremium > profile.getAnnualBudget()) {
                    failReasons.add(String.format("Estimated annual premium ₹%,.0f exceeds strict maximum budget of ₹%,.0f",
                            estPremium, profile.getAnnualBudget()));
                }
            }

            // 8. Senior Citizen Copay Hard Filter (Phase 3)
            boolean hasSeniorMember = profile.getPrimaryAge() >= 60 ||
                    (profile.getMembers() != null && profile.getMembers().stream().anyMatch(m -> m.getAge() >= 60));
            if (hasSeniorMember && "NONE".equalsIgnoreCase(profile.getCopayTolerance()) && terms != null && terms.getCopayPercentage() >= 20.0) {
                failReasons.add(String.format("Mandatory senior citizen co-payment of %.0f%% violates your zero co-payment requirement",
                        terms.getCopayPercentage()));
            }

            // 9. Critical Permanent Exclusion Filter (Phase 3)
            if (profile.getLikelyTreatmentNeeds() != null && !profile.getLikelyTreatmentNeeds().isBlank()
                    && !"COMPREHENSIVE".equalsIgnoreCase(profile.getLikelyTreatmentNeeds()) && policy.getExclusions() != null) {
                String need = profile.getLikelyTreatmentNeeds().trim();
                String needNorm = need.toLowerCase();
                for (String excl : policy.getExclusions()) {
                    String exclNorm = excl.toLowerCase();
                    if (exclNorm.contains(needNorm) || (needNorm.contains("maternity") && exclNorm.contains("maternity"))) {
                        failReasons.add(String.format("Requested treatment '%s' is permanently excluded under policy exclusions (%s)",
                                need, excl));
                        break;
                    }
                }
            }

            // 10. Synthetic Demonstration Fixture Isolation Gate (Phase 3 Provenance Gate)
            boolean catalogHasAuthentic = catalog.stream().anyMatch(StructuredPolicy::isAuthenticAcquiredDocument);
            if (catalogHasAuthentic && policy.isSyntheticFixture() && !profile.isIncludeDemoFixtures()) {
                failReasons.add("Disqualified demonstration fixture: Authentic verified policies exist in catalog and includeDemoFixtures is false.");
            }

            boolean eligible = failReasons.isEmpty();
            results.add(EligibilityResult.builder()
                    .policy(policy)
                    .eligible(eligible)
                    .eligibilityUnknown(false)
                    .eligibilityStatus(eligible ? "ELIGIBLE" : "INELIGIBLE")
                    .failReasons(failReasons)
                    .warnings(warnings)
                    .build());

            if (!eligible) {
                log.debug("[EligibilityEngine] INELIGIBLE: {} — {}", policy.getPolicyName(), failReasons);
            }
        }

        long eligibleCount = results.stream().filter(EligibilityResult::isEligible).count();
        log.info("[EligibilityEngine] Result: {} eligible / {} total", eligibleCount, catalog.size());

        return results;
    }

    /**
     * Convenience: returns only the eligible policies.
     */
    public List<EligibilityResult> getEligibleOnly(
            RecommendationProfile profile,
            List<StructuredPolicy> catalog) {
        return filterEligible(profile, catalog).stream()
                .filter(EligibilityResult::isEligible)
                .collect(Collectors.toList());
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EligibilityResult {
        private StructuredPolicy policy;
        private boolean eligible;
        @lombok.Builder.Default
        private boolean eligibilityUnknown = false;
        private String eligibilityStatus;
        @lombok.Builder.Default
        private List<String> failReasons = new ArrayList<>();
        @lombok.Builder.Default
        private List<String> warnings = new ArrayList<>();
    }
}
