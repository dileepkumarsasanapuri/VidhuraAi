package com.vidhura.ai.service;

import com.vidhura.ai.dto.CompareDto;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.pattern.factory.PolicyComparisonItemFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing policy portfolio comparative analytics.
 * Employs PolicyComparisonItemFactory to assemble consistent comparison models.
 */
@Service
@RequiredArgsConstructor
public class CompareService {

    private final PolicyComparisonItemFactory comparisonItemFactory;

    public CompareDto.CompareResponse comparePolicies(List<Policy> rawPolicies, List<String> requestedIds) {
        if (rawPolicies == null || rawPolicies.isEmpty()) {
            return CompareDto.CompareResponse.builder()
                    .policies(List.of())
                    .recommendation(null)
                    .build();
        }

        List<CompareDto.PolicyComparisonItem> allPolicies = new ArrayList<>();
        for (Policy p : rawPolicies) {
            allPolicies.add(comparisonItemFactory.createComparisonItem(p));
        }

        List<CompareDto.PolicyComparisonItem> selected = (requestedIds != null && !requestedIds.isEmpty())
                ? allPolicies.stream().filter(p -> requestedIds.contains(p.getId())).collect(Collectors.toList())
                : allPolicies;

        if (selected.isEmpty()) {
            return CompareDto.CompareResponse.builder()
                    .policies(List.of())
                    .recommendation(null)
                    .build();
        }

        CompareDto.PolicyComparisonItem bestValue = selected.get(0);
        int bestScore = -1;

        for (CompareDto.PolicyComparisonItem item : selected) {
            int score = (item.getScores() != null)
                    ? item.getScores().getTransparency() + item.getScores().getFairness() + item.getScores().getClaimReliability()
                    : 0;
            if (score > bestScore) {
                bestScore = score;
                bestValue = item;
            }
        }

        CompareDto.Recommendation rec = null;
        if (selected.size() > 1 && bestValue.getScores() != null) {
            rec = CompareDto.Recommendation.builder()
                    .bestPolicy(bestValue.getName())
                    .reason(bestValue.getName() + " leads among your selected policies with a combined transparency ("
                            + bestValue.getScores().getTransparency() + "/100), fairness ("
                            + bestValue.getScores().getFairness() + "/100), and claim reliability ("
                            + bestValue.getScores().getClaimReliability() + "/100) profile.")
                    .savings("Carefully compare deductible and co-pay terms before selecting your final policy.")
                    .build();
        } else if (selected.size() == 1) {
            rec = CompareDto.Recommendation.builder()
                    .bestPolicy(bestValue.getName())
                    .reason("Select at least 2 policies from your portfolio to see comparative advantages and trade-offs.")
                    .savings("")
                    .build();
        }

        return CompareDto.CompareResponse.builder()
                .policies(selected)
                .recommendation(rec)
                .build();
    }
}
