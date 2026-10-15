package com.vidhura.ai.pattern.chain;

import com.vidhura.ai.dto.ClaimDto;
import com.vidhura.ai.model.Policy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Context object passed along the Claim Evaluation Chain of Responsibility.
 * Holds claim inputs, intermediate deduction calculations, and actuarial notes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimContext {

    // Inputs
    private ClaimDto.ClaimRequest request;
    private Policy policy;
    private double hospitalBill;
    private int memberAge;
    private boolean isPreExisting;
    private String hospitalType;
    private String roomType;
    private String disease;

    // Policy Extracted Contract Terms
    private double policyDeductible;
    private double policyCopayPct;
    private boolean roomRentCapped;
    private boolean hasNoSubLimits;
    private String subLimitsClause;
    private String roomRentLimit;

    // Computed Deductions (Accumulated by Handlers in Chain)
    @Builder.Default
    private double deductible = 0.0;
    @Builder.Default
    private double coPay = 0.0;
    @Builder.Default
    private double roomRentExcess = 0.0;
    @Builder.Default
    private double subLimitDeduction = 0.0;
    @Builder.Default
    private double networkDiscount = 0.0;

    // Final Math
    @Builder.Default
    private double coveredAmount = 0.0;
    @Builder.Default
    private double outOfPocket = 0.0;
    @Builder.Default
    private int approvalProbability = 90;

    // Actuarial Insights & Evidence
    @Builder.Default
    private List<String> rejectionReasons = new ArrayList<>();
    @Builder.Default
    private List<String> tips = new ArrayList<>();
    @Builder.Default
    private List<ClaimDto.RequiredDocument> requiredDocuments = new ArrayList<>();
    private String timeline;
}
