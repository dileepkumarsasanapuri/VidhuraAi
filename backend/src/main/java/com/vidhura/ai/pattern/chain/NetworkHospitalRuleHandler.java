package com.vidhura.ai.pattern.chain;

/**
 * Concrete handler for empanelled network vs non-network hospital verification.
 */
public class NetworkHospitalRuleHandler extends AbstractClaimRuleHandler {

    @Override
    protected void processRule(ClaimContext context) {
        if ("non-network".equalsIgnoreCase(context.getHospitalType())) {
            context.setApprovalProbability(context.getApprovalProbability() - 10);
            context.getRejectionReasons().add("Non-network hospital: Cashless authorization not available; undergoes retrospective tariff verification.");
            context.getTips().add("Non-network hospital reimbursement requires upfront payment and takes 15-30 working days.");
            context.setTimeline("Reimbursement takes 15-30 days");
        } else {
            context.getTips().add(0, "Cashless authorization available. Submit pre-auth request at the hospital insurance TPA desk.");
            context.setTimeline("Cashless approval within 2-4 hours");
        }
    }
}
