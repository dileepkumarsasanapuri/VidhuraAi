const { HfInference } = require("@huggingface/inference");
require("dotenv").config({ path: "e:/VidhuraAI/backend/.env" });

const hf = new HfInference(process.env.HF_TOKEN);

const text = `United India Insurance Company Limited
Corporate Identity Number: U93090TN1938GOI000108
Registered Office: 24 Whites Road, Chennai – 600014
IRDAI REG NO.545
1 | P a g e
Individual Health Insurance Policy – Policy Wordings
UIN: UIIHLIP26047V072526
Individual Health Insurance Policy – Platinum & Gold
Terms & Conditions`;

const prompt = `You are an expert insurance analyst. Read the following policy excerpt and extract the details in valid JSON format ONLY. Do not write anything outside the JSON.
      
Schema:
{
  "policyName": "string",
  "provider": "string",
  "type": "string",
  "premium": "string",
  "coverage": "string",
  "expiryDate": "string",
  "clauses": ["string"],
  "exclusions": ["string"],
  "waitingPeriods": [{"condition": "string", "duration": "string", "note": "string"}],
  "riders": [{"name": "string", "details": "string"}],
  "hiddenConditions": ["string"],
  "simplifiedSummary": "string",
  "scores": {"transparency": 70, "risk": 40, "fairness": 75, "claimReliability": 80, "overall": 72}
}

Excerpt:
${text}

JSON Output:
`;

async function test() {
  try {
    const response = await hf.textGeneration({
      model: "mistralai/Mistral-7B-Instruct-v0.2",
      inputs: prompt,
      parameters: {
        max_new_tokens: 1000,
        temperature: 0.1,
        return_full_text: false,
      }
    });
    console.log(response.generated_text);
  } catch (err) {
    console.error(err);
  }
}
test();
