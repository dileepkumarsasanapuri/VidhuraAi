package com.vidhura.ai.service;

import com.vidhura.ai.dto.ClaimDto;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.pattern.chain.ClaimContext;
import com.vidhura.ai.pattern.strategy.ClaimStrategyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating claim predictions using the Strategy Pattern and Chain of Responsibility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimEngineService {

    private final PolicyService policyService;
    private final ClaimStrategyContext claimStrategyContext;

    public ClaimDto.ClaimOutcomeResponse predictClaimOutcome(ClaimDto.ClaimRequest data) {
        Double parsedBill = data.getParsedHospitalBill();
        if (parsedBill == null || parsedBill <= 0) {
            throw new IllegalArgumentException("Valid hospital bill amount is required");
        }

        double bill = parsedBill;
        int memberAge = data.getMemberAge() != null ? data.getMemberAge() : 35;
        boolean isPreExisting = Boolean.TRUE.equals(data.getIsPreExisting());
        String hospitalType = data.getHospitalType() != null ? data.getHospitalType() : "network";
        String roomType = data.getRoomType() != null ? data.getRoomType() : "single";
        String disease = data.getDisease() != null ? data.getDisease().trim() : "General Hospitalization";

        // Fetch policy if provided
        Policy policy = null;
        if (data.getPolicyId() != null && !data.getPolicyId().isBlank()) {
            policy = policyService.findById(data.getPolicyId()).orElse(null);
        }

        // Extract exact policy parameters
        String roomLimit = "Single Standard Private AC Room";
        boolean roomRentCapped = false;
        double copayPct = 0.0;
        double deductibleAmt = 0.0;
        String subLimitsClause = "None (Full Sum Insured available for all covered illnesses without disease caps)";
        boolean hasNoSubLimits = true;

        if (policy != null && policy.getAnalysis() != null) {
            Policy.PolicyAnalysis a = policy.getAnalysis();
            if (a.getRoomRentLimit() != null) roomLimit = a.getRoomRentLimit();
            if (a.getRoomRentCapped() != null) roomRentCapped = a.getRoomRentCapped();
            if (a.getCopayPercentage() != null) copayPct = a.getCopayPercentage();
            if (a.getDeductibleAmount() != null) deductibleAmt = a.getDeductibleAmount();
            if (a.getDiseaseSubLimits() != null) {
                subLimitsClause = a.getDiseaseSubLimits();
                hasNoSubLimits = subLimitsClause.toLowerCase().contains("none");
            }
        }

        // Build context for the Strategy & Chain of Responsibility
        ClaimContext context = ClaimContext.builder()
                .request(data)
                .policy(policy)
                .hospitalBill(bill)
                .memberAge(memberAge)
                .isPreExisting(isPreExisting)
                .hospitalType(hospitalType)
                .roomType(roomType)
                .disease(disease)
                .policyDeductible(deductibleAmt)
                .policyCopayPct(copayPct)
                .roomRentCapped(roomRentCapped)
                .hasNoSubLimits(hasNoSubLimits)
                .subLimitsClause(subLimitsClause)
                .roomRentLimit(roomLimit)
                .build();

        // Execute via Strategy Context (Primary: AI Strategy, Fallback: Deterministic Chain of Responsibility)
        return claimStrategyContext.executeEvaluation(context);
    }
}
