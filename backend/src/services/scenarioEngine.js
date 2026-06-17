/**
 * ScenarioEngine — AI scenario simulation for insurance coverage analysis.
 * Uses Hugging Face to dynamically determine coverage based on policy details.
 */
const { HfInference } = require("@huggingface/inference");

const hf = new HfInference(process.env.HF_TOKEN);

async function simulateScenario(question, policy) {
  if (!policy) {
    console.log("[ScenarioEngine] No policy provided, falling back.");
    return getFallbackResponse();
  }

  const prompt = `You are an expert insurance analyst. The user has a question about a medical scenario. 
Based on their policy details, determine the coverage.

Policy Details:
Name: ${policy.name}
Type: ${policy.type}
Clauses: ${JSON.stringify(policy.analysis?.clauses || [])}
Exclusions: ${JSON.stringify(policy.analysis?.exclusions || [])}
Waiting Periods: ${JSON.stringify(policy.analysis?.waitingPeriods || [])}

User Scenario: ${question}

Return ONLY valid JSON exactly matching this schema:
{
  "covered": true,
  "coverageStatus": "string (e.g. ✅ Covered — No Waiting Period)",
  "summary": "string explaining the coverage",
  "estimatedCost": { "total": 100000, "covered": 80000, "outOfPocket": 20000 },
  "conditions": ["string array of conditions"],
  "recommendation": "string",
  "relevantClauses": ["string array of matching clauses"]
}
`;

  try {
    console.log("[ScenarioEngine] Querying Hugging Face AI...");
    const response = await hf.chatCompletion({
      model: "Qwen/Qwen2.5-72B-Instruct",
      messages: [
        { role: "system", content: "You are an expert insurance analyst." },
        { role: "user", content: prompt }
      ],
      max_tokens: 1000,
      temperature: 0.1,
    });

    const generatedText = response.choices[0].message.content.trim();
    const jsonMatch = generatedText.match(/\{[\s\S]*\}/);
    const parsedData = JSON.parse(jsonMatch ? jsonMatch[0] : generatedText);
    
    if (parsedData.covered === "true") parsedData.covered = true;
    if (parsedData.covered === "false") parsedData.covered = false;

    return parsedData;
  } catch (error) {
    console.error("[ScenarioEngine] HF API Error:", error.message);
    return getFallbackResponse();
  }
}

function getFallbackResponse() {
  return {
    covered: "unknown",
    coverageStatus: "📋 Detailed Analysis Required",
    summary: "I've analyzed this scenario, but couldn't verify it with AI (API failed or policy missing). Please check manually.",
    estimatedCost: null,
    conditions: ["AI analysis unavailable"],
    recommendation: "Please review the policy document manually.",
    relevantClauses: [],
  };
}

module.exports = { simulateScenario };
