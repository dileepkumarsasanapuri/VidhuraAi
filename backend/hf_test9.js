const { HfInference } = require("@huggingface/inference");
require("dotenv").config({ path: "e:/VidhuraAI/backend/.env" });

const hf = new HfInference(process.env.HF_TOKEN);

async function test() {
  try {
    const response = await hf.chatCompletion({
      model: "Qwen/Qwen2.5-72B-Instruct",
      messages: [
        { role: "system", content: "You are an expert insurance analyst." },
        { role: "user", content: "A".repeat(3000) }
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
