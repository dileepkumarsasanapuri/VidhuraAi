const http = require('http');

const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
const payload = `--${boundary}\r\nContent-Disposition: form-data; name="policyFile"; filename="test.pdf"\r\nContent-Type: application/pdf\r\n\r\nFakePDFContent\r\n--${boundary}--\r\n`;

const options = {
  hostname: 'localhost',
  port: 5001,
  path: '/api/analyze',
  method: 'POST',
  headers: {
    'Content-Type': `multipart/form-data; boundary=${boundary}`,
    'Content-Length': Buffer.byteLength(payload)
  }
};

const req = http.request(options, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => console.log('Response 5001:', data));
});

req.on('error', e => console.error('Error:', e));
req.write(payload);
req.end();
