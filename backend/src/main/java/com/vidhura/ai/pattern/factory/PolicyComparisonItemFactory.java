package com.vidhura.ai.pattern.factory;

import com.vidhura.ai.dto.CompareDto;
import com.vidhura.ai.model.Policy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory pattern implementation to construct standardized PolicyComparisonItem
 * representations from domain Policy contracts.
 */
@Component
public class PolicyComparisonItemFactory {

    public CompareDto.PolicyComparisonItem createComparisonItem(Policy policy) {
        if (policy == null) {
            return null;
        }

        CompareDto.Scores scores = CompareDto.Scores.builder()
                .transparency(0).risk(0).fairness(0).claimReliability(0)
                .build();
        List<String> pros = new ArrayList<>();
        List<String> cons = new ArrayList<>();
        int exclusions = 0;
        int hiddenTraps = 0;

        String roomRentLimit = "Single Standard AC Room";
        Boolean roomRentCapped = false;
        String copay = "0% (Nil)";
        Double copayPercentage = 0.0;
        String deductible = "₹0 (Nil)";
        Double deductibleAmount = 0.0;
        String diseaseSubLimits = "None";
        Integer preHospitalizationDays = 60;
        Integer postHospitalizationDays = 180;
        String restorationBenefit = "100% Instant Restoration";
        String noClaimBonus = "Cumulative Bonus available";

        if (policy.getAnalysis() != null) {
            Policy.PolicyAnalysis a = policy.getAnalysis();
            if (a.getScores() != null) {
                scores = CompareDto.Scores.builder()
                        .transparency(a.getScores().getTransparency() != null ? a.getScores().getTransparency() : 0)
                        .risk(a.getScores().getRisk() != null ? a.getScores().getRisk() : 0)
                        .fairness(a.getScores().getFairness() != null ? a.getScores().getFairness() : 0)
                        .claimReliability(a.getScores().getClaimReliability() != null ? a.getScores().getClaimReliability() : 0)
                        .build();
            }
            if (a.getClauses() != null && !a.getClauses().isEmpty()) {
                pros = a.getClauses().stream().limit(3).map(c -> c.split(":")[0]).collect(Collectors.toList());
            }
            if (a.getHiddenConditions() != null && !a.getHiddenConditions().isEmpty()) {
                cons = a.getHiddenConditions().stream().limit(3).map(c -> c.replace("⚠️ ", "")).collect(Collectors.toList());
                hiddenTraps = a.getHiddenConditions().size();
            }
            if (a.getExclusions() != null) {
                exclusions = a.getExclusions().size();
            }
            if (a.getRoomRentLimit() != null) roomRentLimit = a.getRoomRentLimit();
            if (a.getRoomRentCapped() != null) roomRentCapped = a.getRoomRentCapped();
            if (a.getCopay() != null) copay = a.getCopay();
            if (a.getCopayPercentage() != null) copayPercentage = a.getCopayPercentage();
            if (a.getDeductible() != null) deductible = a.getDeductible();
            if (a.getDeductibleAmount() != null) deductibleAmount = a.getDeductibleAmount();
            if (a.getDiseaseSubLimits() != null) diseaseSubLimits = a.getDiseaseSubLimits();
            if (a.getPreHospitalizationDays() != null) preHospitalizationDays = a.getPreHospitalizationDays();
            if (a.getPostHospitalizationDays() != null) postHospitalizationDays = a.getPostHospitalizationDays();
            if (a.getRestorationBenefit() != null) restorationBenefit = a.getRestorationBenefit();
            if (a.getNoClaimBonus() != null) noClaimBonus = a.getNoClaimBonus();
        }

        return CompareDto.PolicyComparisonItem.builder()
                .id(policy.getId())
                .name(policy.getName() != null ? policy.getName() : "Policy")
                .provider(policy.getProvider() != null ? policy.getProvider() : "Provider")
                .premium(policy.getPremium() != null ? policy.getPremium() : "—")
                .coverage(policy.getCoverage() != null ? policy.getCoverage() : "—")
                .scores(scores)
                .pros(pros)
                .cons(cons)
                .exclusions(exclusions)
                .hiddenTraps(hiddenTraps)
                .roomRentLimit(roomRentLimit)
                .roomRentCapped(roomRentCapped)
                .copay(copay)
                .copayPercentage(copayPercentage)
                .deductible(deductible)
                .deductibleAmount(deductibleAmount)
                .diseaseSubLimits(diseaseSubLimits)
                .preHospitalizationDays(preHospitalizationDays)
                .postHospitalizationDays(postHospitalizationDays)
                .restorationBenefit(restorationBenefit)
                .noClaimBonus(noClaimBonus)
                .build();
    }
}
