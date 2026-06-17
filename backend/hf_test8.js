const https = require('https');
require("dotenv").config({ path: "e:/VidhuraAI/backend/.env" });

const data = JSON.stringify({
  inputs: "Hi, give me valid JSON: { \"hello\": \"world\" }"
});

const options = {
  hostname: 'api-inference.huggingface.co',
  path: '/models/mistralai/Mistral-7B-Instruct-v0.2',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${process.env.HF_TOKEN}`,
    'Content-Length': data.length
  }
};

const req = https.request(options, (res) => {
  let body = '';
  res.on('data', (d) => { body += d; });
  res.on('end', () => { console.log("Status:", res.statusCode); console.log("Body:", body); });
});

req.on('error', (error) => { console.error("Error:", error); });
req.write(data);
req.end();
