package com.vidhura.ai.pattern.strategy;

import com.vidhura.ai.dto.ClaimDto;
import com.vidhura.ai.pattern.chain.ClaimContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy Context in the Strategy Pattern.
 * Coordinates execution of ClaimEvaluationStrategy implementations, handling graceful fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimStrategyContext {

    private final AiClaimEvaluationStrategy aiStrategy;
    private final DeterministicRuleClaimStrategy ruleStrategy;

    /**
     * Executes the claim evaluation strategy workflow.
     * Attempts AI evaluation first for maximum scenario flexibility; if unavailable,
     * seamlessly executes the deterministic actuarial Chain of Responsibility.
     *
     * @param context The shared claim context.
     * @return The final claim outcome response.
     */
    public ClaimDto.ClaimOutcomeResponse executeEvaluation(ClaimContext context) {
        try {
            log.info("[Strategy Context] Attempting primary strategy: {}", aiStrategy.getStrategyName());
            return aiStrategy.evaluate(context);
        } catch (Exception ex) {
            log.warn("[Strategy Context] Primary strategy ({}) failed: {}. Executing fallback strategy: {}",
                    aiStrategy.getStrategyName(), ex.getMessage(), ruleStrategy.getStrategyName());
            return ruleStrategy.evaluate(context);
        }
    }
}
