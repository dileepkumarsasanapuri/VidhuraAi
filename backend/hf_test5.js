require("dotenv").config({ path: "e:/VidhuraAI/backend/.env" });

async function test() {
  try {
    const response = await fetch("https://api-inference.huggingface.co/models/mistralai/Mistral-7B-Instruct-v0.2", {
      headers: {
        Authorization: `Bearer ${process.env.HF_TOKEN}`,
        "Content-Type": "application/json",
      },
      method: "POST",
      body: JSON.stringify({
        inputs: "Hi, give me valid JSON: { \"hello\": \"world\" }",
        parameters: { max_new_tokens: 500 }
      }),
    });
    
    const result = await response.json();
    console.log(result);
  } catch (err) {
    console.error(err);
  }
}
test();
