package com.vidhura.ai.pattern.strategy;

import com.vidhura.ai.dto.ClaimDto;
import com.vidhura.ai.pattern.chain.ClaimContext;

/**
 * Strategy interface for evaluating insurance claims.
 */
public interface ClaimEvaluationStrategy {

    /**
     * Executes the claim evaluation strategy.
     *
     * @param context The shared claim context containing input data and policy contract terms.
     * @return The calculated ClaimOutcomeResponse.
     * @throws Exception if strategy execution fails.
     */
    ClaimDto.ClaimOutcomeResponse evaluate(ClaimContext context) throws Exception;

    /**
     * Returns a human-readable identifier for logging.
     */
    String getStrategyName();
}
