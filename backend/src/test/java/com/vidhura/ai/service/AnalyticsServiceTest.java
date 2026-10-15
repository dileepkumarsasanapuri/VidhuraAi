package com.vidhura.ai.service;

import com.vidhura.ai.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private PolicyService policyService;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(policyService);
    }

    @Test
    void testGetAnalyticsDataWithNoPolicies() {
        when(policyService.getUserPolicies("user-1")).thenReturn(Collections.emptyList());

        Map<String, Object> analytics = analyticsService.getAnalyticsData("user-1");

        assertNotNull(analytics);
        assertEquals(0, analytics.get("totalPolicies"));
        assertEquals(0, analytics.get("analyzedPolicies"));

        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) analytics.get("kpis");
        assertNotNull(kpis);

        @SuppressWarnings("unchecked")
        Map<String, Object> trustScore = (Map<String, Object>) kpis.get("trustScore");
        assertEquals(0, trustScore.get("value"));
    }

    @Test
    void testGetAnalyticsDataWithPopulatedPolicies() {
        Policy policy = new Policy();
        policy.setId("p1");
        policy.setName("Optima Secure");
        policy.setType("Health");
        policy.setCoverage("INR 10,00,000");

        Policy.PolicyAnalysis analysis = new Policy.PolicyAnalysis();
        Policy.PolicyScores scores = Policy.PolicyScores.builder()
                .transparency(90)
                .fairness(85)
                .claimReliability(80)
                .risk(20)
                .overall(85)
                .build();
        analysis.setScores(scores);
        analysis.setHiddenConditions(List.of("Condition 1", "Condition 2"));
        analysis.setExclusions(List.of("Cosmetic", "Adventure sports"));
        policy.setAnalysis(analysis);

        when(policyService.getUserPolicies("user-1")).thenReturn(List.of(policy));

        Map<String, Object> analytics = analyticsService.getAnalyticsData("user-1");

        assertNotNull(analytics);
        assertEquals(1, analytics.get("totalPolicies"));
        assertEquals(1, analytics.get("analyzedPolicies"));

        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) analytics.get("kpis");
        assertNotNull(kpis);

        @SuppressWarnings("unchecked")
        Map<String, Object> trustScore = (Map<String, Object>) kpis.get("trustScore");
        // (90 + 85 + 80) / 3 = 85
        assertEquals(85, trustScore.get("value"));

        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) analytics.get("policyHealth");
        assertEquals(90, health.get("transparency"));
        assertEquals(80, health.get("riskProtection")); // 100 - 20
        assertEquals(85, health.get("overall"));
    }
}
