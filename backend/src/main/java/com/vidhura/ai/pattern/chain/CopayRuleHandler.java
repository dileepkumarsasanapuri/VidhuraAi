package com.vidhura.ai.pattern.chain;

/**
 * Concrete handler for calculating co-payment based strictly on the extracted policy contract terms.
 */
public class CopayRuleHandler extends AbstractClaimRuleHandler {

    @Override
    protected void processRule(ClaimContext context) {
        double copayPct = context.getPolicyCopayPct();
        double bill = context.getHospitalBill();
        double coPay = bill * (copayPct / 100.0);

        context.setCoPay(coPay);

        if (copayPct > 0) {
            context.setApprovalProbability(context.getApprovalProbability() - 5);
            context.getTips().add("Mandatory " + (int) copayPct + "% co-payment applies under policy terms.");
        } else {
            context.getTips().add("✅ Zero Co-Pay: "
                    + (context.getPolicy() != null ? context.getPolicy().getName() : "Policy")
                    + " has 0% co-payment across all member ages.");
        }
    }
}
