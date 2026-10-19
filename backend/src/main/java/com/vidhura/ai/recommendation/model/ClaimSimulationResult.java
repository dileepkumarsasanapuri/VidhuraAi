package com.vidhura.ai.recommendation.model;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Document preserving a reproducible, immutable snapshot of a deterministic claim calculation.
 * Ensures that future policy updates or pipeline changes never alter the historical explanation of a simulation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "claim_simulation_results")
public class ClaimSimulationResult {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Builder.Default
    private boolean syntheticExample = false;

    @Indexed
    private String scenarioId;

    @Indexed
    private String policyId;

    private String policyCode;
    private String policyName;
    private String provider;
    private String irdaUin;
    private String uinRevision;
    private String sourceDocumentHash;
    private String ruleSetVersion;
    private String calculationEngineVersion;

    private ClaimSimulationDto.CalculationStatus calculationStatus;
    private String statusReason;

    // Monetary totals computed using exact BigDecimal arithmetic
    private BigDecimal totalSubmittedBill;
    private BigDecimal totalEligibleExpenses;
    private BigDecimal totalNonCoveredExpenses;
    private BigDecimal totalDeductions;
    private BigDecimal estimatedInsurerPayment;
    private BigDecimal estimatedPatientLiability;

    private String reconciliationIdentity;
    private boolean reconciled;

    private boolean restorationTriggered;
    private BigDecimal restorationAmountGranted;
    private String restorationExplanation;

    @Builder.Default
    private List<ClaimSimulationDto.DeductionItemDto> deductionBreakdown = new ArrayList<>();

    @Builder.Default
    private List<String> appliedClauses = new ArrayList<>();

    @Builder.Default
    private List<String> unresolvedRules = new ArrayList<>();

    private String legalDisclaimer;

    @CreatedDate
    private Instant evaluatedAt;

    private Instant createdAt;
}
