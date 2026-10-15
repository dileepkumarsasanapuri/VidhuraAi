package com.vidhura.ai.pattern.strategy;

import com.vidhura.ai.dto.ScenarioDto;
import com.vidhura.ai.model.Policy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy Context managing scenario simulation strategy selection and resilient fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioStrategyContext {

    private final AiScenarioSimulationStrategy aiStrategy;
    private final DeterministicScenarioSimulationStrategy deterministicStrategy;

    public ScenarioDto.ScenarioResponse executeSimulation(String question, Policy policy, String language) {
        String targetLang = (language != null && !language.isBlank()) ? language.trim() : "English";

        // Try AI LLM Strategy first if policy is available
        if (policy != null && aiStrategy.supports(policy)) {
            try {
                return aiStrategy.simulate(question, policy, targetLang);
            } catch (Exception ex) {
                log.warn("[StrategyContext] AI strategy invocation failed ({}), falling back to Deterministic Strategy.", ex.getMessage());
            }
        }

        // Fallback or policy-null scenario
        return deterministicStrategy.simulate(question, policy, targetLang);
    }
}
