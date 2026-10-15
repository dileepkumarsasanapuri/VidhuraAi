package com.vidhura.ai.service;

import com.vidhura.ai.dto.ScenarioDto;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.pattern.strategy.ScenarioStrategyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * High-level service coordinating scenario simulations by delegating
 * to the Strategy Context (AI Actuarial Strategy with graceful Deterministic Fallback).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioEngineService {

    private final ScenarioStrategyContext strategyContext;

    public ScenarioDto.ScenarioResponse simulateScenario(String question, Policy policy) {
        return simulateScenario(question, policy, "English");
    }

    public ScenarioDto.ScenarioResponse simulateScenario(String question, Policy policy, String language) {
        return strategyContext.executeSimulation(question, policy, language);
    }
}
