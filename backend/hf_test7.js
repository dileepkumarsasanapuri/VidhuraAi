const { HfInference } = require("@huggingface/inference");
require("dotenv").config({ path: "e:/VidhuraAI/backend/.env" });

const hf = new HfInference(process.env.HF_TOKEN);

async function test() {
  try {
    const response = await hf.chatCompletion({
      model: "HuggingFaceH4/zephyr-7b-beta",
      messages: [
        { role: "user", content: "Hi, give me valid JSON: { \"hello\": \"world\" }" }
      ],
      max_tokens: 500
    });
    console.log(response.choices[0].message.content);
  } catch (err) {
    console.error("ERROR BODY:", JSON.stringify(err, null, 2));
    if (err.httpResponse && err.httpResponse.body) {
      console.error("HTTP ERROR:", err.httpResponse.body);
    }
  }
}
test();
