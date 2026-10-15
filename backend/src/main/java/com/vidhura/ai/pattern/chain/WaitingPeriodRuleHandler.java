package com.vidhura.ai.pattern.chain;

import com.vidhura.ai.model.Policy;

/**
 * Concrete handler for pre-existing diseases (PED) and waiting period scrutiny.
 */
public class WaitingPeriodRuleHandler extends AbstractClaimRuleHandler {

    @Override
    protected void processRule(ClaimContext context) {
        context.getTips().add("File claim intimation within 24 hours of admission for seamless cashless settlement.");
        context.getTips().add("Ensure hospital discharge summary matches final pharmacy and diagnostic bills.");
        context.getTips().add("Keep all original payment receipts, investigation films, and doctor consultation notes.");

        if (context.isPreExisting()) {
            context.setApprovalProbability(context.getApprovalProbability() - 30);
            context.getRejectionReasons().add("Pre-existing disease (PED) clause: Subject to policy waiting period verification.");
            context.getTips().add("Submit consecutive renewal policy certificates proving continuous uninterrupted coverage.");
        }

        // Check if policy has specific waiting periods for this disease
        if (context.getPolicy() != null && context.getPolicy().getAnalysis() != null
                && context.getPolicy().getAnalysis().getWaitingPeriods() != null) {
            String diseaseLower = context.getDisease().toLowerCase();
            for (Policy.WaitingPeriod wp : context.getPolicy().getAnalysis().getWaitingPeriods()) {
                String wpText = (wp.getCondition() + " " + wp.getNote()).toLowerCase();
                if (wpText.contains(diseaseLower) || (diseaseLower.contains("cataract") && wpText.contains("cataract"))
                        || (diseaseLower.contains("knee") && wpText.contains("joint"))
                        || (diseaseLower.contains("hernia") && wpText.contains("hernia"))) {
                    context.getTips().add("⏳ Specified Condition Notice: " + wp.getCondition() + " requires "
                            + wp.getDuration() + " of continuous tenure (" + wp.getNote() + ").");
                }
            }
        }
    }
}
