# SSL Certificates

Place your SSL certificate files here:

- `cert.pem` — Your SSL certificate
- `key.pem` — Your SSL private key

Then uncomment the SSL configuration in `nginx/nginx.conf`.

## Quick self-signed cert for testing:

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout key.pem -out cert.pem \
  -subj "/CN=localhost"
```
