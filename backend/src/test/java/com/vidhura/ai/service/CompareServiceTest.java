package com.vidhura.ai.service;

import com.vidhura.ai.dto.CompareDto;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.pattern.factory.PolicyComparisonItemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompareServiceTest {

    private CompareService compareService;

    @BeforeEach
    void setUp() {
        PolicyComparisonItemFactory factory = new PolicyComparisonItemFactory();
        compareService = new CompareService(factory);
    }

    @Test
    void testCompareEmptyPolicies() {
        CompareDto.CompareResponse response = compareService.comparePolicies(Collections.emptyList(), null);
        assertNotNull(response);
        assertTrue(response.getPolicies().isEmpty());
        assertNull(response.getRecommendation());
    }

    @Test
    void testCompareSelectsBestPolicyRecommendation() {
        Policy p1 = new Policy();
        p1.setId("p1");
        p1.setName("Basic Health Care");
        p1.setProvider("ABC General");
        p1.setPremium("INR 12,000/yr");
        p1.setCoverage("INR 5,00,000");

        Policy.PolicyAnalysis a1 = new Policy.PolicyAnalysis();
        a1.setScores(Policy.PolicyScores.builder()
                .transparency(60)
                .fairness(60)
                .claimReliability(60)
                .risk(40)
                .build());
        p1.setAnalysis(a1);

        Policy p2 = new Policy();
        p2.setId("p2");
        p2.setName("Optima Secure Plus");
        p2.setProvider("XYZ Insurance");
        p2.setPremium("INR 16,000/yr");
        p2.setCoverage("INR 15,00,000");

        Policy.PolicyAnalysis a2 = new Policy.PolicyAnalysis();
        a2.setScores(Policy.PolicyScores.builder()
                .transparency(95)
                .fairness(90)
                .claimReliability(92)
                .risk(15)
                .build());
        p2.setAnalysis(a2);

        CompareDto.CompareResponse response = compareService.comparePolicies(List.of(p1, p2), List.of("p1", "p2"));

        assertNotNull(response);
        assertEquals(2, response.getPolicies().size());
        assertNotNull(response.getRecommendation());
        assertEquals("Optima Secure Plus", response.getRecommendation().getBestPolicy());
    }
}
