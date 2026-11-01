# Vidhura AI — Environment Configuration & Security Guide

## 1. Environment Variable Reference

### Backend Configuration (Spring Boot 3)

| Variable | Type | Default | Description | Environment |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | String | `default` | Active Spring profile (`default`, `prod`). | All |
| `SERVER_PORT` | Integer | `5000` | Port for the embedded web server. | All |
| `MONGODB_URI` | URI | `mongodb://localhost:27017/vidhura` | Connection string for MongoDB (local container or Atlas). | All |
| `JWT_SECRET` | String | *Must be set in prod* | Secret key for HMAC-SHA256 token signing (min. 32 chars). | All |
| `VIDHURA_POLICY_STORAGE_ROOT` | Path | `/app/data/policies` (Linux) / `E:/VidhuraData/policies` (Win) | Root directory for policy documents and extraction manifests. | All |
| `GROQ_API_KEY` | Secret | `""` | API key for Groq LLM provider (Llama-3.3-70B). | All |
| `GROQ_MODEL` | String | `llama-3.3-70b-versatile` | Active Groq model name. | All |
| `HF_TOKEN` | Secret | `""` | HuggingFace user access token (optional fallback). | All |
| `GOOGLE_CLIENT_ID` | String | `""` | Client ID for Google OAuth2 authentication. | All |
| `CORS_ALLOWED_ORIGINS` | Comma list| `http://localhost:3000` | Allowed origins for CORS requests. | All |
| `RATE_LIMIT_RPM` | Integer | `120` | Max incoming requests per minute per IP address. | All |
| `SMTP_HOST` | Hostname| `smtp.gmail.com` | SMTP host for OTP and transactional notifications. | All |
| `SMTP_PORT` | Integer | `587` | SMTP port (STARTTLS). | All |
| `SMTP_USERNAME` | String | `""` | SMTP sender username. | All |
| `SMTP_PASSWORD` | Secret | `""` | SMTP application password. | All |

### Frontend Configuration (Next.js 15)

| Variable | Scope | Default | Description |
|---|---|---|---|
| `NEXT_PUBLIC_API_URL` | Public (Client + Server) | `/api` | Base URL for backend REST API endpoints. |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID`| Public (Client) | `""` | Google OAuth client ID rendered in web browser. |
| `PORT` | Server Runtime | `3000` | Local HTTP binding port for Next.js node server. |
| `HOSTNAME` | Server Runtime | `0.0.0.0` | Host interface binding for container networking. |
| `NODE_ENV` | Build & Runtime | `production` | Node environment flag. |

> [!CAUTION]
> **Boundary Rule:** Never prefix backend database connection strings, JWT secrets, AI provider API keys, or private SSH credentials with `NEXT_PUBLIC_`. Any variable starting with `NEXT_PUBLIC_` is baked into client-side JavaScript bundles and visible to all website users.

---

## 2. Policy Document Storage Abstraction

To ensure compatibility across local Windows machines, developer workstations, and cloud Linux containers, the storage root is fully decoupled:

1. **Local Windows Development:**
   - Default: `E:/VidhuraData/policies` (or set via `VIDHURA_POLICY_STORAGE_ROOT` in `backend/.env`).
2. **Docker Compose & Container Environments:**
   - Standard path: `/app/data/policies` mounted via named volume `policy_data`.
3. **CI Environments:**
   - Automated tests run against temporary directories (e.g. `/tmp/vidhura-policies`), preserving host storage isolation.

---

## 3. Configuration Security Checklist

- [ ] `.env` is never committed to Git (verified by root and sub-project `.gitignore`).
- [ ] `.env.example` contains only non-secret placeholders and descriptive documentation.
- [ ] Production JWT secret is at least 32 characters long with high entropy.
- [ ] Production CORS origins strictly whitelist the authorized domain name.
- [ ] Actuator endpoints `/actuator/health` and application endpoint `/api/health` are unauthenticated for health checks, while metrics/sensitive endpoints require authentication in production.
