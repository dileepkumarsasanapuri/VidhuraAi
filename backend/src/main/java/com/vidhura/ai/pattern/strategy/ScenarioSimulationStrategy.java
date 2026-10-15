package com.vidhura.ai.pattern.strategy;

import com.vidhura.ai.dto.ScenarioDto;
import com.vidhura.ai.model.Policy;

/**
 * Strategy interface for simulating insurance claim and coverage scenarios.
 * Implements the GoF Strategy pattern to decouple AI prompt-based reasoning
 * from deterministic actuarial rule calculation.
 */
public interface ScenarioSimulationStrategy {

    /**
     * Executes scenario simulation for a given question and policy contract.
     *
     * @param question The user's query or medical scenario
     * @param policy The policy contract under evaluation
     * @param language Target language for explanation (e.g. English, Hindi, Telugu, etc.)
     * @return Simulated scenario outcome
     */
    ScenarioDto.ScenarioResponse simulate(String question, Policy policy, String language);

    /**
     * Identifies whether this strategy can execute the request.
     */
    boolean supports(Policy policy);
}
