package com.vidhura.ai.pattern.chain;

import com.vidhura.ai.dto.ClaimDto;

import java.util.List;

/**
 * Terminal handler in the Chain of Responsibility.
 * Computes net covered payout, out-of-pocket expenses, document requirements, and sanity bounds.
 */
public class FinalSettlementCalculatorHandler extends AbstractClaimRuleHandler {

    @Override
    protected void processRule(ClaimContext context) {
        double bill = context.getHospitalBill();
        double totalDeductions = context.getDeductible() + context.getCoPay()
                + context.getRoomRentExcess() + context.getSubLimitDeduction();

        double coveredAmount = Math.max(0.0, bill - totalDeductions);
        double outOfPocket = bill - coveredAmount;

        context.setCoveredAmount(coveredAmount);
        context.setOutOfPocket(outOfPocket);

        // Clamp probability
        int prob = Math.max(15, Math.min(98, context.getApprovalProbability()));
        context.setApprovalProbability(prob);

        if (context.getRejectionReasons().isEmpty()) {
            if (outOfPocket == 0.0) {
                context.getTips().add("✅ Full Coverage: 100% of the hospital bill qualifies under policy terms with zero deductible and zero co-pay.");
            } else {
                context.getRejectionReasons().add("Standard non-medical administrative consumables if billed outside medical tariffs.");
            }
        }

        // Standard statutory and hospital claim documentation checklist
        context.setRequiredDocuments(List.of(
                ClaimDto.RequiredDocument.builder().name("Duly filled and signed claim pre-auth form").critical(true).build(),
                ClaimDto.RequiredDocument.builder().name("Hospital discharge summary with clinical history").critical(true).build(),
                ClaimDto.RequiredDocument.builder().name("Original itemized hospital bill with receipt numbers").critical(true).build(),
                ClaimDto.RequiredDocument.builder().name("Attending doctor's prescription and treatment chart").critical(true).build(),
                ClaimDto.RequiredDocument.builder().name("Diagnostic lab reports and radiology films (X-Ray/MRI/CT)").critical(true).build(),
                ClaimDto.RequiredDocument.builder().name("Pharmacy purchase vouchers supported by prescriptions").critical(true).build(),
                ClaimDto.RequiredDocument.builder().name("Patient government photo ID proof and cancelled bank cheque").critical(false).build(),
                ClaimDto.RequiredDocument.builder().name("Indoor case papers (ICPs) and OT notes").critical(false).build()
        ));
    }
}
