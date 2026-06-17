const fs = require('fs');
const FormData = require('form-data');
const axios = require('axios');

async function test() {
  const form = new FormData();
  form.append('policyFile', fs.createReadStream('e:/VidhuraAI/backend/package.json'), {
    filename: 'test.pdf',
    contentType: 'application/pdf',
  });

  try {
    const res = await axios.post('http://localhost:5000/api/analyze', form, {
      headers: form.getHeaders()
    });
    console.log(res.data);
  } catch (err) {
    console.log(err.response ? err.response.data : err.message);
  }
}
test();
