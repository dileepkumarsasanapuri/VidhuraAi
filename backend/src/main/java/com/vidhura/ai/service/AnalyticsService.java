package com.vidhura.ai.service;

import com.vidhura.ai.model.Policy;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PolicyService policyService;

    /**
     * Compute analytics dynamically from the user's actual policies.
     * Returns zeroed-out metrics when the user has no policies.
     */
    @Cacheable(value = "analytics", key = "#userId")
    public Map<String, Object> getAnalyticsData(String userId) {
        List<Policy> policies = policyService.getUserPolicies(userId);

        // KPIs derived from real policies
        int avgTransparency = 0, avgFairness = 0, avgClaimReliability = 0, avgRisk = 0, avgOverall = 0;
        int totalHiddenRisks = 0;
        int analyzedCount = 0;

        for (Policy p : policies) {
            if (p.getAnalysis() != null && p.getAnalysis().getScores() != null) {
                Policy.PolicyScores s = p.getAnalysis().getScores();
                avgTransparency += (s.getTransparency() != null ? s.getTransparency() : 0);
                avgFairness += (s.getFairness() != null ? s.getFairness() : 0);
                avgClaimReliability += (s.getClaimReliability() != null ? s.getClaimReliability() : 0);
                avgRisk += (s.getRisk() != null ? s.getRisk() : 0);
                avgOverall += (s.getOverall() != null ? s.getOverall() : 0);
                analyzedCount++;
            }
            if (p.getAnalysis() != null && p.getAnalysis().getHiddenConditions() != null) {
                totalHiddenRisks += p.getAnalysis().getHiddenConditions().size();
            }
        }

        if (analyzedCount > 0) {
            avgTransparency /= analyzedCount;
            avgFairness /= analyzedCount;
            avgClaimReliability /= analyzedCount;
            avgRisk /= analyzedCount;
            avgOverall /= analyzedCount;
        }

        int trustScore = analyzedCount > 0 ? (avgTransparency + avgFairness + avgClaimReliability) / 3 : 0;
        int claimPreparedness = avgClaimReliability;

        Map<String, Object> kpis = Map.of(
                "trustScore", Map.of("value", trustScore, "trend", 0),
                "claimPreparedness", Map.of("value", claimPreparedness, "trend", 0),
                "hiddenRisks", Map.of("value", totalHiddenRisks, "trend", 0),
                "coverageGap", Map.of("value", "—", "trend", 0)
        );

        Map<String, Object> policyHealth = Map.of(
                "transparency", avgTransparency,
                "riskProtection", analyzedCount > 0 ? (100 - avgRisk) : 0,
                "claimReadiness", avgClaimReliability,
                "valueForMoney", avgFairness,
                "overall", avgOverall
        );

        // Coverage breakdown from real policies
        List<Map<String, Object>> coverageBreakdown = new ArrayList<>();
        Map<String, List<Policy>> policyByType = policies.stream()
                .collect(Collectors.groupingBy(p -> p.getType() != null ? p.getType() : "Other"));

        for (Map.Entry<String, List<Policy>> entry : policyByType.entrySet()) {
            int typeAvgScore = 0;
            int typeCount = 0;
            for (Policy p : entry.getValue()) {
                if (p.getAnalysis() != null && p.getAnalysis().getScores() != null && p.getAnalysis().getScores().getOverall() != null) {
                    typeAvgScore += p.getAnalysis().getScores().getOverall();
                    typeCount++;
                }
            }
            int percentage = typeCount > 0 ? typeAvgScore / typeCount : 0;
            String coverage = entry.getValue().stream()
                    .map(Policy::getCoverage)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("—");
            coverageBreakdown.add(Map.of(
                    "type", entry.getKey(),
                    "amount", coverage,
                    "percentage", percentage
            ));
        }

        // Risk distribution from actual hidden conditions, exclusions, and waiting periods
        List<Map<String, Object>> riskDistribution = new ArrayList<>();
        int exclusionCount = 0, waitingPeriodCount = 0;
        for (Policy p : policies) {
            if (p.getAnalysis() != null) {
                if (p.getAnalysis().getExclusions() != null) {
                    exclusionCount += p.getAnalysis().getExclusions().size();
                }
                if (p.getAnalysis().getWaitingPeriods() != null) {
                    waitingPeriodCount += p.getAnalysis().getWaitingPeriods().size();
                }
            }
        }
        if (totalHiddenRisks > 0) {
            riskDistribution.add(Map.of("category", "Hidden Conditions", "count", totalHiddenRisks, "severity", totalHiddenRisks > 3 ? "High" : "Medium"));
        }
        if (exclusionCount > 0) {
            riskDistribution.add(Map.of("category", "Exclusions", "count", exclusionCount, "severity", exclusionCount > 8 ? "High" : "Medium"));
        }
        if (waitingPeriodCount > 0) {
            riskDistribution.add(Map.of("category", "Waiting Periods", "count", waitingPeriodCount, "severity", "Low"));
        }

        // Trust score trend — not available without historical data, return empty
        List<Map<String, Object>> trustScoreTrend = List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("kpis", kpis);
        result.put("trustScoreTrend", trustScoreTrend);
        result.put("policyHealth", policyHealth);
        result.put("coverageBreakdown", coverageBreakdown);
        result.put("riskDistribution", riskDistribution);
        result.put("totalPolicies", policies.size());
        result.put("analyzedPolicies", analyzedCount);

        return result;
    }
}
