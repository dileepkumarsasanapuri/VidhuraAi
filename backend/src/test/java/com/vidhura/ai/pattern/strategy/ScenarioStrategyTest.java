package com.vidhura.ai.pattern.strategy;

import com.vidhura.ai.dto.ScenarioDto;
import com.vidhura.ai.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioStrategyTest {

    private DeterministicScenarioSimulationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DeterministicScenarioSimulationStrategy();
    }

    @Test
    void testCoveredHospitalizationScenario() {
        Policy policy = new Policy();
        policy.setName("HDFC ERGO Optima Secure");
        policy.setCoverage("INR 10,00,000");

        Policy.PolicyAnalysis analysis = new Policy.PolicyAnalysis();
        analysis.setClauses(List.of(
                "In-Patient Treatment: Covers medical expenses for hospital stay exceeding 24 hours."
        ));
        analysis.setExclusions(List.of("Cosmetic treatments", "Self-inflicted injuries"));
        policy.setAnalysis(analysis);

        ScenarioDto.ScenarioResponse response = strategy.simulate(
                "I need emergency hospitalization for acute appendicitis. Will it be covered?",
                policy,
                "English"
        );

        assertNotNull(response);
        assertNotNull(response.getSummary());
        assertNotNull(response.getEstimatedCost());
        assertTrue(response.getEstimatedCost().getTotal() > 0);
        assertTrue(response.getEstimatedCost().getCovered() > 0);
        assertNotNull(response.getConditions());
    }

    @Test
    void testCosmeticExclusionScenario() {
        Policy policy = new Policy();
        policy.setName("Standard Health Policy");

        Policy.PolicyAnalysis analysis = new Policy.PolicyAnalysis();
        analysis.setExclusions(List.of("Cosmetic plastic surgery", "Dental surgery"));
        policy.setAnalysis(analysis);

        ScenarioDto.ScenarioResponse response = strategy.simulate(
                "Is cosmetic plastic surgery or Botox covered?",
                policy,
                "English"
        );

        assertNotNull(response);
        assertNotNull(response.getSummary());
        assertTrue(response.getSummary().toLowerCase().contains("cosmetic") ||
                   response.getSummary().toLowerCase().contains("exclu") ||
                   "Partial Coverage / Excluded".equals(response.getCoverageStatus()) ||
                   Boolean.FALSE.equals(response.getCovered()) ||
                   "false".equals(String.valueOf(response.getCovered())));
    }
}
