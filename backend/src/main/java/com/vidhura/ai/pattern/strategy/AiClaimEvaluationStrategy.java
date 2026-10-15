package com.vidhura.ai.pattern.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidhura.ai.dto.ClaimDto;
import com.vidhura.ai.pattern.chain.ClaimContext;
import com.vidhura.ai.service.PolicyAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Concrete Strategy: Uses LLM Actuarial Engine to evaluate arbitrary medical scenarios
 * with strict adherence to extracted policy contract terms.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClaimEvaluationStrategy implements ClaimEvaluationStrategy {

    private final PolicyAnalyzerService policyAnalyzerService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public String getStrategyName() {
        return "AI_ACTUARIAL_STRATEGY";
    }

    @Override
    public ClaimDto.ClaimOutcomeResponse evaluate(ClaimContext context) throws Exception {
        String policyName = context.getPolicy() != null ? context.getPolicy().getName() : "Standard Benchmark Policy";
        double bill = context.getHospitalBill();

        String policyContext = (context.getPolicy() != null && context.getPolicy().getAnalysis() != null)
                ? "Policy Name: " + policyName + "\n"
                + "Room Rent Entitlement: " + context.getRoomRentLimit() + " (Room Capped: " + context.isRoomRentCapped() + ")\n"
                + "Co-Payment Clause: " + context.getPolicy().getAnalysis().getCopay() + " (Mandatory Co-Pay: " + context.getPolicyCopayPct() + "%)\n"
                + "Deductible Clause: " + context.getPolicy().getAnalysis().getDeductible() + " (Base Deductible: ₹" + (long) context.getPolicyDeductible() + ")\n"
                + "Disease Sub-Limits: " + context.getSubLimitsClause() + "\n"
                + "Clauses: " + context.getPolicy().getAnalysis().getClauses() + "\n"
                + "Exclusions: " + context.getPolicy().getAnalysis().getExclusions() + "\n"
                + "Waiting Periods: " + context.getPolicy().getAnalysis().getWaitingPeriods() + "\n"
                + "Fine-Print Traps: " + context.getPolicy().getAnalysis().getHiddenConditions()
                : "Benchmark Health Policy (Room Rent: Single standard private AC room, Co-pay: 0%, Deductible: ₹0, No disease sub-limits).";

        String prompt = "You are a chief insurance claims auditor and actuary with 20+ years of experience auditing claims.\n"
                + "Evaluate this medical insurance claim against the policy contract with mathematical precision.\n\n"
                + "Policy Contract Details:\n"
                + policyContext + "\n\n"
                + "Submitted Claim Details:\n"
                + "- Ailment / Procedure: " + context.getDisease() + "\n"
                + "- Total Hospital Bill: ₹" + (long) bill + "\n"
                + "- Hospital Network: " + context.getHospitalType() + " (" + ("network".equalsIgnoreCase(context.getHospitalType()) ? "Empanelled Network Cashless" : "Non-Network Reimbursement Filing") + ")\n"
                + "- Room Type Chosen: " + context.getRoomType() + "\n"
                + "- Patient Age: " + context.getMemberAge() + " Years\n"
                + "- Pre-Existing Condition (PED): " + (context.isPreExisting() ? "Yes (Declared pre-existing condition, subject to waiting period verification)" : "No (Standard ailment)") + "\n\n"
                + "CRITICAL FIDELITY DIRECTIVE (STRICTLY ADHERE TO POLICY CONTRACT):\n"
                + "1. 'deductible': MUST EQUAL " + context.getPolicyDeductible() + ". If policy states ₹0 / Nil, deductible MUST BE 0. Do NOT invent a deductible.\n"
                + "2. 'coPay': Calculate based ONLY on policy's extracted co-payment (" + context.getPolicyCopayPct() + "%). If policy has 0% / Nil co-pay, coPay MUST BE 0. Do NOT apply senior citizen co-payment unless explicitly mandated by this policy!\n"
                + "3. 'roomRentExcess': If policy allows single private room and room chosen is single, or if room rent is uncapped, roomRentExcess MUST BE 0. Only apply proportional deduction if room exceeds entitlement AND policy is room-capped.\n"
                + "4. 'subLimitDeduction': If policy has no sub-limits (diseaseSubLimits is None), subLimitDeduction MUST BE 0.\n"
                + "5. 'coveredAmount' MUST exactly equal: hospitalBill - (deductible + coPay + roomRentExcess + subLimitDeduction). Cannot be negative. If all deductions are 0, coveredAmount MUST be 100% of hospitalBill.\n"
                + "6. 'outOfPocket' MUST exactly equal: hospitalBill - coveredAmount.\n\n"
                + "Return strictly valid JSON ONLY conforming to this schema with NO markdown outside the JSON:\n"
                + "{\n"
                + "  \"approvalProbability\": 90,\n"
                + "  \"hospitalBill\": " + bill + ",\n"
                + "  \"coveredAmount\": " + bill + ",\n"
                + "  \"outOfPocket\": 0,\n"
                + "  \"breakdown\": {\n"
                + "    \"deductible\": " + context.getPolicyDeductible() + ",\n"
                + "    \"coPay\": 0,\n"
                + "    \"roomRentExcess\": 0,\n"
                + "    \"subLimitDeduction\": 0,\n"
                + "    \"networkDiscount\": 0\n"
                + "  },\n"
                + "  \"rejectionReasons\": [\"Specific clause reason if any\"],\n"
                + "  \"requiredDocuments\": [\n"
                + "    {\"name\": \"Duly filled and signed claim form\", \"critical\": true},\n"
                + "    {\"name\": \"Discharge summary\", \"critical\": true},\n"
                + "    {\"name\": \"Original itemized hospital bill\", \"critical\": true}\n"
                + "  ],\n"
                + "  \"tips\": [\"Actionable advice citing policy\"],\n"
                + "  \"timeline\": \"" + ("network".equalsIgnoreCase(context.getHospitalType()) ? "Cashless approval within 2-4 hours" : "Reimbursement takes 15-30 days") + "\",\n"
                + "  \"similarClaims\": {\"approved\": 88, \"rejected\": 5, \"avgPayout\": " + Math.round(bill) + "}\n"
                + "}";

        log.info("[Strategy Pattern] Running AiClaimEvaluationStrategy for '{}' (Bill: ₹{})", context.getDisease(), (long) bill);
        String rawJson = policyAnalyzerService.callAiChat(prompt, true);

        String cleanText = rawJson.trim().replaceAll("(?s)<think>.*?</think>", "").trim();
        if (cleanText.contains("```")) {
            cleanText = cleanText.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
        }
        int firstBrace = cleanText.indexOf('{');
        int lastBrace = cleanText.lastIndexOf('}');
        String jsonStr = (firstBrace >= 0 && lastBrace > firstBrace) ? cleanText.substring(firstBrace, lastBrace + 1) : cleanText;

        ClaimDto.ClaimOutcomeResponse aiOutcome = objectMapper.readValue(jsonStr, ClaimDto.ClaimOutcomeResponse.class);

        // Sanity check math balance
        if (aiOutcome.getHospitalBill() == null || aiOutcome.getHospitalBill() <= 0) {
            aiOutcome.setHospitalBill(bill);
        }
        if (aiOutcome.getBreakdown() != null) {
            double ded = aiOutcome.getBreakdown().getDeductible();
            double cop = aiOutcome.getBreakdown().getCoPay();
            double rre = aiOutcome.getBreakdown().getRoomRentExcess();
            double sld = aiOutcome.getBreakdown().getSubLimitDeduction();
            double totalDeductions = ded + cop + rre + sld;
            aiOutcome.setCoveredAmount(Math.max(0.0, bill - totalDeductions));
            aiOutcome.setOutOfPocket(bill - aiOutcome.getCoveredAmount());
        }

        return aiOutcome;
    }
}
