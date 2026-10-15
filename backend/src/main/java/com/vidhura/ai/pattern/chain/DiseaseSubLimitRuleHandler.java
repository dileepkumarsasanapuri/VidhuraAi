package com.vidhura.ai.pattern.chain;

/**
 * Concrete handler for evaluating exclusions and disease-specific sub-limits.
 * Strictly checks whether the policy has sub-limits (e.g. cataract capping) or full coverage.
 */
public class DiseaseSubLimitRuleHandler extends AbstractClaimRuleHandler {

    @Override
    protected void processRule(ClaimContext context) {
        String lowerDisease = context.getDisease().toLowerCase();
        double bill = context.getHospitalBill();
        double subLimitDeduction = 0.0;

        if (lowerDisease.contains("dental") || lowerDisease.contains("teeth")) {
            if (!lowerDisease.contains("accident") && !lowerDisease.contains("trauma")) {
                subLimitDeduction = bill;
                context.getRejectionReasons().add("Dental treatment is excluded under policy contract terms unless necessitated by accidental trauma requiring hospitalization.");
            }
        } else if (lowerDisease.contains("cosmetic") || lowerDisease.contains("plastic") || lowerDisease.contains("aesthetic")) {
            if (!lowerDisease.contains("burn") && !lowerDisease.contains("accident")) {
                subLimitDeduction = bill;
                context.getRejectionReasons().add("Cosmetic procedures are excluded under standard contract terms unless reconstructive following accidental injury.");
            }
        } else if (!context.isHasNoSubLimits() && context.getSubLimitsClause() != null
                && context.getSubLimitsClause().toLowerCase().contains("cataract")
                && (lowerDisease.contains("cataract") || lowerDisease.contains("eye lens"))) {
            // Apply sub-limit only if the policy contract explicitly caps cataract
            subLimitDeduction = Math.max(0.0, bill - 40000.0);
            if (subLimitDeduction > 0) {
                context.getRejectionReasons().add("Cataract surgery sub-limit: Capped at ₹40,000 per eye as per policy schedule. Excess ₹"
                        + Math.round(subLimitDeduction) + " not payable.");
            }
        }

        context.setSubLimitDeduction(subLimitDeduction);
    }
}
