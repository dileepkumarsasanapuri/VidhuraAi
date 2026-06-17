const { HfInference } = require("@huggingface/inference");
require("dotenv").config({ path: "e:/VidhuraAI/backend/.env" });

const hf = new HfInference(process.env.HF_TOKEN);

const text = `United India Insurance Company Limited...`;

async function test() {
  try {
    const response = await hf.chatCompletion({
      model: "mistralai/Mistral-7B-Instruct-v0.3", // v0.3 is better at function calling/JSON
      messages: [
        { role: "system", content: "You are an expert insurance analyst." },
        { role: "user", content: "Extract details in JSON:\n{ \"policyName\": \"string\" }\n\n" + text }
      ],
      max_tokens: 500,
      temperature: 0.1,
    });
    console.log(response.choices[0].message.content);
  } catch (err) {
    console.error(err);
  }
}
test();
