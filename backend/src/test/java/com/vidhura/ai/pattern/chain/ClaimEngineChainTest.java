package com.vidhura.ai.pattern.chain;

import com.vidhura.ai.dto.ClaimDto;
import com.vidhura.ai.pattern.strategy.DeterministicRuleClaimStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaimEngineChainTest {

    private DeterministicRuleClaimStrategy strategy;

    @BeforeEach
    void setUp() {
        ClaimRuleChainBuilder builder = new ClaimRuleChainBuilder();
        strategy = new DeterministicRuleClaimStrategy(builder);
    }

    @Test
    void testStandardNetworkClaimSettlement() {
        ClaimDto.ClaimRequest request = new ClaimDto.ClaimRequest();
        request.setType("hospitalization");
        request.setDisease("Appendicitis Surgery");
        request.setHospitalBill(100000.0);
        request.setMemberAge(32);
        request.setIsPreExisting(false);
        request.setHospitalType("network");
        request.setRoomType("single");

        ClaimContext context = ClaimContext.builder()
                .request(request)
                .hospitalBill(100000.0)
                .memberAge(32)
                .isPreExisting(false)
                .hospitalType("network")
                .roomType("single")
                .disease("Appendicitis Surgery")
                .policyDeductible(5000.0)
                .policyCopayPct(10.0)
                .roomRentCapped(false)
                .hasNoSubLimits(true)
                .build();

        ClaimDto.ClaimOutcomeResponse response = strategy.evaluate(context);

        assertNotNull(response);
        assertEquals(100000.0, response.getHospitalBill());

        // Deductible = 5000, Copay = 10% of bill (100000 * 0.10 = 10000)
        assertEquals(5000.0, response.getBreakdown().getDeductible());
        assertEquals(10000.0, response.getBreakdown().getCoPay());

        // Covered amount + out of pocket should equal hospital bill
        assertTrue(response.getCoveredAmount() > 0);
        assertTrue(response.getOutOfPocket() > 0);
        assertEquals(100000.0, response.getCoveredAmount() + response.getOutOfPocket(), 0.01);
        assertTrue(response.getApprovalProbability() >= 70);
    }

    @Test
    void testPreExistingDiseasePenalty() {
        ClaimDto.ClaimRequest request = new ClaimDto.ClaimRequest();
        request.setType("hospitalization");
        request.setDisease("Type 2 Diabetes Complication");
        request.setHospitalBill(200000.0);
        request.setMemberAge(55);
        request.setIsPreExisting(true);
        request.setHospitalType("non-network");
        request.setRoomType("suite");

        ClaimContext context = ClaimContext.builder()
                .request(request)
                .hospitalBill(200000.0)
                .memberAge(55)
                .isPreExisting(true)
                .hospitalType("non-network")
                .roomType("suite")
                .disease("Type 2 Diabetes Complication")
                .policyDeductible(10000.0)
                .policyCopayPct(20.0)
                .roomRentCapped(true)
                .roomRentLimit("1% of sum insured")
                .hasNoSubLimits(false)
                .subLimitsClause("Diabetes capped at 50,000")
                .build();

        ClaimDto.ClaimOutcomeResponse response = strategy.evaluate(context);

        assertNotNull(response);
        assertNotNull(response.getRejectionReasons());
        assertFalse(response.getRejectionReasons().isEmpty(), "Should list pre-existing condition waiting period / risk");
        assertTrue(response.getBreakdown().getRoomRentExcess() > 0, "Suite room should incur room rent excess deduction");
        assertTrue(response.getApprovalProbability() < 80, "Pre-existing condition + non-network hospital should lower probability");
    }

    @Test
    void testRequiredDocumentsGenerated() {
        ClaimDto.ClaimRequest request = new ClaimDto.ClaimRequest();
        request.setType("surgery");
        request.setDisease("Cataract");
        request.setHospitalBill(45000.0);

        ClaimContext context = ClaimContext.builder()
                .request(request)
                .hospitalBill(45000.0)
                .memberAge(65)
                .isPreExisting(false)
                .hospitalType("network")
                .roomType("single")
                .disease("Cataract")
                .hasNoSubLimits(true)
                .build();

        ClaimDto.ClaimOutcomeResponse response = strategy.evaluate(context);

        assertNotNull(response.getRequiredDocuments());
        assertFalse(response.getRequiredDocuments().isEmpty());
        assertTrue(response.getRequiredDocuments().stream().anyMatch(d -> d.getName().toLowerCase().contains("discharge")));
    }
}
