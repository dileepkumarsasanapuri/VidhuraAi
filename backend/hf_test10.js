const { HfInference } = require("@huggingface/inference");
require("dotenv").config({ path: "e:/VidhuraAI/backend/.env" });

const hf = new HfInference(process.env.HF_TOKEN);

async function test() {
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
United India Insurance Company Limited
Corporate Identity Number: U93090TN1938GOI000108
Registered Office: 24 Whites Road, Chennai – 600014
IRDAI REG NO.545
1 | P a g e
Individual Health Insurance Policy – Policy Wordings
UIN: UIIHLIP26047V072526
Individual Health Insurance Policy – Platinum & Gold
Terms & Conditions
I. PREAMBLE
This Policy is a contract of insurance issued by UNITED INDIA INSURANCE COMPANY (hereinafter
called the COMPANY) to the Proposer mentioned in the Schedule (hereinafter called the ‘Policyholder’)
to cover the person(s) named in the schedule (hereinafter called the ‘Insured Persons’). The Policy is
based on the statements and declaration provided in the Proposal Form by the Proposer and is subject
to
i The receipt of full premium,
ii Disclosure to information norm including the information provided in the Proposal Form by the
proposer on behalf of him/her/them-selves and all persons to be Insured which is incorporated in
the policy and is the basis of

JSON Output:
`;

  try {
    const response = await hf.chatCompletion({
      model: "Qwen/Qwen2.5-72B-Instruct",
      messages: [
        { role: "system", content: "You are an expert insurance analyst." },
        { role: "user", content: prompt }
      ],
      max_tokens: 1000
    });
    console.log(response.choices[0].message.content);
  } catch (err) {
    if (err.httpResponse && err.httpResponse.body) {
      console.error("HTTP ERROR:", err.httpResponse.body);
    } else {
      console.error("ERROR:", err);
    }
  }
}
test();
