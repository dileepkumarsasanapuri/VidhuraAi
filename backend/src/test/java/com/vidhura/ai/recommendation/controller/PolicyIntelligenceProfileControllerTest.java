package com.vidhura.ai.recommendation.controller;

import com.vidhura.ai.recommendation.dto.ExternalEvidenceDto;
import com.vidhura.ai.recommendation.dto.PolicyIntelligenceProfileDto;
import com.vidhura.ai.recommendation.service.PolicyIntelligenceProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyIntelligenceProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PolicyIntelligenceProfileService profileService;

    @Test
    @DisplayName("GET /api/v1/policies/{policyCode}/intelligence-profile returns unified profile")
    void testGetPolicyIntelligenceProfileEndpoint() throws Exception {
        PolicyIntelligenceProfileDto mockDto = PolicyIntelligenceProfileDto.builder()
                .contractOverview(PolicyIntelligenceProfileDto.PolicyContractOverviewDto.builder()
                        .policyCode("HDFC_OPTIMA_SECURE")
                        .policyName("HDFC ERGO Optima Secure")
                        .irdaUin("HDFHLIP21503V012021")
                        .roomRentCapped(false)
                        .copayPercentage(BigDecimal.ZERO)
                        .build())
                .insurerPerformance(ExternalEvidenceDto.InsurerPerformanceSummaryDto.builder()
                        .insurerCode("HDFC_ERGO")
                        .incurredClaimRatio("68.10%")
                        .verificationStatus("VERIFIED_STATUTORY")
                        .verifiedStatutory(true)
                        .build())
                .hospitalAccessibility(ExternalEvidenceDto.HospitalNetworkSummaryDto.builder()
                        .totalNetworkHospitalsInCity(28)
                        .preferredTier1Count(10)
                        .cashlessAccessibilityScore(100.0)
                        .build())
                .premiumQuotation(ExternalEvidenceDto.QuoteDetailDto.builder()
                        .quotedAnnualPremium(new BigDecimal("14500.00"))
                        .isAuthentic(true)
                        .build())
                .benchmarkClaimSimulation(PolicyIntelligenceProfileDto.BenchmarkClaimSimulationSummaryDto.builder()
                        .totalSubmittedBill(new BigDecimal("123000.00"))
                        .estimatedInsurerPayment(new BigDecimal("111000.00"))
                        .estimatedPatientLiability(new BigDecimal("12000.00"))
                        .isReconciled(true)
                        .build())
                .evidenceQualityScorecard(PolicyIntelligenceProfileDto.EvidenceQualityScorecardDto.builder()
                        .contractualGroundTruthStatus("VERIFIED_CONTRACTUAL_SPECIFICATION")
                        .externalStatutoryEvidenceStatus("VERIFIED_STATUTORY")
                        .overallQualityScore(95.0)
                        .verifiedDisclosures(List.of("Contractual ground truth verified against IRDAI registry"))
                        .build())
                .build();

        when(profileService.getPolicyIntelligenceProfile(
                eq("HDFC_OPTIMA_SECURE"), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/policies/HDFC_OPTIMA_SECURE/intelligence-profile")
                        .param("age", "35")
                        .param("sumInsured", "1000000")
                        .param("city", "Mumbai")
                        .param("suitabilityScore", "88.5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contractOverview.policyCode").value("HDFC_OPTIMA_SECURE"))
                .andExpect(jsonPath("$.data.contractOverview.roomRentCapped").value(false))
                .andExpect(jsonPath("$.data.insurerPerformance.incurredClaimRatio").value("68.10%"))
                .andExpect(jsonPath("$.data.hospitalAccessibility.totalNetworkHospitalsInCity").value(28))
                .andExpect(jsonPath("$.data.premiumQuotation.quotedAnnualPremium").value(14500.00))
                .andExpect(jsonPath("$.data.benchmarkClaimSimulation.estimatedInsurerPayment").value(111000.00))
                .andExpect(jsonPath("$.data.evidenceQualityScorecard.overallQualityScore").value(95.0));
    }
}
