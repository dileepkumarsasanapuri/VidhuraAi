package com.vidhura.ai.pattern.chain;

/**
 * Concrete handler for evaluating room rent limits and calculating proportional deductions
 * strictly according to the policy contract.
 */
public class RoomRentRuleHandler extends AbstractClaimRuleHandler {

    @Override
    protected void processRule(ClaimContext context) {
        double roomRentExcess = 0.0;
        String roomType = context.getRoomType();
        double bill = context.getHospitalBill();

        if (context.isRoomRentCapped()) {
            if ("suite".equalsIgnoreCase(roomType)) {
                roomRentExcess = bill * 0.35;
                context.setApprovalProbability(context.getApprovalProbability() - 10);
                context.getRejectionReasons().add("Room rent category (Suite) exceeded policy limit: Triggers proportional deduction across surgeon, OT & doctor charges.");
            } else if ("deluxe".equalsIgnoreCase(roomType)) {
                roomRentExcess = bill * 0.20;
                context.setApprovalProbability(context.getApprovalProbability() - 10);
                context.getRejectionReasons().add("Room rent category (Deluxe) exceeded policy limit: Triggers proportional deduction across surgeon, OT & doctor charges.");
            }
        } else {
            context.getTips().add("✅ Uncapped Room Rent: "
                    + (context.getPolicy() != null ? context.getPolicy().getName() : "Policy")
                    + " covers single standard private AC room without proportional deduction penalties.");
        }

        context.setRoomRentExcess(roomRentExcess);
    }
}
