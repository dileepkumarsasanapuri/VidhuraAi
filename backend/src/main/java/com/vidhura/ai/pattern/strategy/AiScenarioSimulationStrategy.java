package com.vidhura.ai.pattern.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidhura.ai.dto.ScenarioDto;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.service.PolicyAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Concrete strategy utilizing LLM actuarial reasoning to simulate complex medical scenarios.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AiScenarioSimulationStrategy implements ScenarioSimulationStrategy {

    private final PolicyAnalyzerService policyAnalyzerService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public boolean supports(Policy policy) {
        return policy != null;
    }

    @Override
    public ScenarioDto.ScenarioResponse simulate(String question, Policy policy, String language) {
        String targetLang = (language != null && !language.isBlank()) ? language.trim() : "English";

        try {
            String clausesJson = objectMapper.writeValueAsString(policy.getAnalysis() != null ? policy.getAnalysis().getClauses() : List.of());
            String exclusionsJson = objectMapper.writeValueAsString(policy.getAnalysis() != null ? policy.getAnalysis().getExclusions() : List.of());
            String waitingPeriodsJson = objectMapper.writeValueAsString(policy.getAnalysis() != null ? policy.getAnalysis().getWaitingPeriods() : List.of());

            String roomRent = (policy.getAnalysis() != null && policy.getAnalysis().getRoomRentLimit() != null)
                    ? policy.getAnalysis().getRoomRentLimit() : "Single Standard Private AC Room";
            String copay = (policy.getAnalysis() != null && policy.getAnalysis().getCopay() != null)
                    ? policy.getAnalysis().getCopay() : "0% (Nil)";
            String deductible = (policy.getAnalysis() != null && policy.getAnalysis().getDeductible() != null)
                    ? policy.getAnalysis().getDeductible() : "₹0 (Nil)";
            String subLimits = (policy.getAnalysis() != null && policy.getAnalysis().getDiseaseSubLimits() != null)
                    ? policy.getAnalysis().getDiseaseSubLimits() : "None";

            String languageInstruction = !"English".equalsIgnoreCase(targetLang)
                    ? "\nCRITICAL LANGUAGE INSTRUCTION: The user speaks and listens in " + targetLang + ". Write the 'summary', 'coverageStatus', 'conditions', and 'recommendation' values in fluent " + targetLang + " (in native script) so the policyholder can understand their coverage.\n"
                    : "";

            String prompt = "You are an expert insurance analyst and actuary. The user has a question about a medical scenario.\n"
                    + "Based on their exact policy contract details, determine coverage with actuarial precision." + languageInstruction + "\n\n"
                    + "Policy Contract Extracted Parameters:\n"
                    + "Name: " + policy.getName() + " (" + policy.getProvider() + ")\n"
                    + "Type: " + policy.getType() + "\n"
                    + "Room Rent Entitlement: " + roomRent + "\n"
                    + "Co-payment: " + copay + "\n"
                    + "Deductible: " + deductible + "\n"
                    + "Disease Sub-limits: " + subLimits + "\n"
                    + "Clauses: " + clausesJson + "\n"
                    + "Exclusions: " + exclusionsJson + "\n"
                    + "Waiting Periods: " + waitingPeriodsJson + "\n\n"
                    + "User Scenario: " + question + "\n\n"
                    + "CRITICAL FIDELITY DIRECTIVE:\n"
                    + "- Base your cost estimation and coverage decision strictly on this policy's actual terms.\n"
                    + "- If this policy has 0% co-payment, ₹0 deductible, and uncapped room rent, out-of-pocket must be ₹0 and covered amount must equal 100% of the cost. Do NOT invent standard deductibles, penalties, or co-pays!\n\n"
                    + "Return ONLY valid JSON exactly matching this schema:\n"
                    + "{\n"
                    + "  \"covered\": true,\n"
                    + "  \"coverageStatus\": \"string (e.g. ✅ Covered — No Waiting Period)\",\n"
                    + "  \"summary\": \"string explaining the coverage citing the policy\",\n"
                    + "  \"estimatedCost\": { \"total\": 100000, \"covered\": 100000, \"outOfPocket\": 0 },\n"
                    + "  \"conditions\": [\"string array of actual policy conditions\"],\n"
                    + "  \"recommendation\": \"string\",\n"
                    + "  \"relevantClauses\": [\"string array of matching clauses\"]\n"
                    + "}\n";

            log.info("[Strategy: AiScenario] Invoking LLM actuarial reasoning for scenario (Language: {})...", targetLang);
            String rawResponse = policyAnalyzerService.callAiChat(prompt, true);

            String cleanText = rawResponse.trim().replaceAll("(?s)<think>.*?</think>", "").trim();
            if (cleanText.contains("```")) {
                cleanText = cleanText.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            }
            int firstBrace = cleanText.indexOf('{');
            int lastBrace = cleanText.lastIndexOf('}');
            String jsonStr = (firstBrace >= 0 && lastBrace > firstBrace) ? cleanText.substring(firstBrace, lastBrace + 1) : cleanText;

            return objectMapper.readValue(jsonStr, ScenarioDto.ScenarioResponse.class);
        } catch (Exception e) {
            log.warn("[Strategy: AiScenario] AI execution failed ({}), throwing to trigger fallback strategy.", e.getMessage());
            throw new RuntimeException("AI Scenario Simulation failed: " + e.getMessage(), e);
        }
    }
}
