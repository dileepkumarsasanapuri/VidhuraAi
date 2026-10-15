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
                // No eligibility rules defined — assume eligible with warning
                warnings.add("No eligibility rules defined for this product; verify manually.");
                results.add(EligibilityResult.builder()
                        .policy(policy)
                        .eligible(true)
                        .warnings(warnings)
                        .build());
                continue;
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
                if (!rules.getAllowedFamilyTypes().contains(profile.getInsuranceType())) {
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

            boolean eligible = failReasons.isEmpty();
            results.add(EligibilityResult.builder()
                    .policy(policy)
                    .eligible(eligible)
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
        private List<String> failReasons = new ArrayList<>();
        @lombok.Builder.Default
        private List<String> warnings = new ArrayList<>();
    }
}
