package com.vidhura.ai.recommendation.model;

import com.vidhura.ai.recommendation.dto.ClaimSimulationDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Document representing a stored claim simulation scenario.
 * Holds patient context, hospitalization details, and itemized bill lines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "claim_scenarios")
public class ClaimScenario {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userEmail;

    @Builder.Default
    private boolean syntheticExample = false;

    @Indexed
    private String policyId;

    private String policyCode;
    private String irdaUin;
    private String uinRevision;

    private int patientAge;
    private String diagnosis;
    private boolean accidentalInjury;
    private boolean preExistingCondition;
    private int policyContinuityMonths;

    private LocalDate admissionDate;
    private LocalDate dischargeDate;

    private String hospitalType;
    private String hospitalRoomCategoryOpted;

    private BigDecimal selectedSumInsured;
    private BigDecimal priorClaimsInCurrentYear;
    private boolean subsequentClaimForSameIllness;

    @Builder.Default
    private List<ClaimSimulationDto.BillLineItemDto> billLines = new ArrayList<>();

    private BigDecimal totalSubmittedBill;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
