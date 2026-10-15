package com.vidhura.ai.pattern.chain;

/**
 * Concrete handler for applying policy-level deductibles.
 * Uses ONLY the authentic extracted deductible from the policy contract.
 */
public class DeductibleRuleHandler extends AbstractClaimRuleHandler {

    @Override
    protected void processRule(ClaimContext context) {
        double deductible = context.getPolicyDeductible();
        context.setDeductible(deductible);

        if (deductible > 0) {
            context.getTips().add("Base deductible of ₹" + (long) deductible + " applies before insurance payment.");
        } else {
            context.getTips().add("✅ Zero Deductible: "
                    + (context.getPolicy() != null ? context.getPolicy().getName() : "Policy")
                    + " has ₹0 compulsory base deductible for hospitalization in India.");
        }
    }
}
