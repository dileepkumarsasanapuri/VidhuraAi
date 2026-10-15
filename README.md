# Vidhura AI

> **"Understand Your Insurance Before It Understands You."**

An AI-powered insurance intelligence platform that transforms complex insurance policies into personalized, scenario-based, interactive explanations — powered by HuggingFace AI.

## 🏗️ Architecture

```
├── frontend/          # Next.js 16 + React 19 + Tailwind CSS 4 + Framer Motion
├── backend/           # Spring Boot 3.2 + MongoDB + JWT Auth + HuggingFace AI
├── nginx/             # Reverse proxy configuration
├── docker-compose.yml # Full-stack orchestration
└── .github/workflows/ # CI/CD pipeline
```

## ✨ Features

| Product | Purpose |
|---------|---------|
| **PolicyLens AI** | Simplifies policy documents via PDF parsing + AI analysis |
| **ClaimShield AI** | Predicts claim approval probability & cost breakdowns |
| **CompareWise** | Intelligent side-by-side policy comparison with scoring |
| **ScenarioGPT** | Real-life coverage Q&A with AI-powered analysis |
| **TrustScore Engine** | Transparency, fairness & reliability scoring system |
| **Analytics Dashboard** | KPIs, trends, coverage gaps & risk distribution |

---

## 🚀 Quick Start

### Option 1: Docker (Recommended)

```bash
# 1. Clone the repository
git clone https://github.com/yourusername/VidhuraAI.git
cd VidhuraAI

# 2. Configure environment
cp .env.example .env
# Edit .env with your secrets (JWT_SECRET, HF_TOKEN, GOOGLE_CLIENT_ID)

# 3. Launch everything
docker compose up -d

# 4. Open the app
# Frontend:  http://localhost:3000
# Backend:   http://localhost:5000/api/health
# MongoDB:   localhost:27017
```

### Option 2: With Nginx Reverse Proxy

```bash
docker compose --profile with-nginx up -d
# App available at http://localhost (port 80)
```

### Option 3: Manual Development

**Backend (Spring Boot)**
```bash
cd backend
mvn spring-boot:run
# → http://localhost:5000
```

**Frontend (Next.js)**
```bash
cd frontend
npm install
npm run dev
# → http://localhost:3000
```

---

## 🔐 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `MONGODB_URI` | ✅ | MongoDB connection string |
| `JWT_SECRET` | ✅ | JWT signing key (min 32 chars) |
| `GOOGLE_CLIENT_ID` | ⚡ | Google OAuth client ID |
| `HF_TOKEN` | ⚡ | HuggingFace API token for AI analysis |
| `HF_MODEL` | ❌ | AI model (default: `Qwen/Qwen2.5-72B-Instruct`) |
| `CORS_ALLOWED_ORIGINS` | ❌ | Allowed CORS origins (default: `*`) |
| `RATE_LIMIT_RPM` | ❌ | API rate limit per minute (default: `120`) |
| `SPRING_PROFILES_ACTIVE` | ❌ | Spring profile: `default` or `prod` |

> ✅ = Required | ⚡ = Required for full functionality | ❌ = Optional

---

## 📡 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health` | Health check |
| `POST` | `/api/auth/register` | User registration |
| `POST` | `/api/auth/login` | User login |
| `POST` | `/api/auth/google` | Google OAuth login |
| `GET` | `/api/auth/me` | Current user info |
| `GET` | `/api/policies` | List user policies |
| `POST` | `/api/policies` | Create a policy |
| `GET` | `/api/policies/:id` | Get policy by ID |
| `DELETE` | `/api/policies/:id` | Delete a policy |
| `POST` | `/api/analyze` | Upload & analyze PDF policy |
| `POST` | `/api/scenario` | Simulate insurance scenario |
| `POST` | `/api/claims/predict` | Predict claim outcome |
| `POST` | `/api/compare` | Compare policies |
| `GET` | `/api/analytics` | Dashboard analytics |
| `GET` | `/api/settings` | Get user settings |
| `PUT` | `/api/settings` | Update user settings |

---

## 🏭 Production Deployment

### Prerequisites
- Docker & Docker Compose v2+
- Domain name (for SSL)
- HuggingFace API token

### Steps

1. **Configure environment:**
   ```bash
   cp .env.example .env
   # Set strong JWT_SECRET, HF_TOKEN, GOOGLE_CLIENT_ID
   # Set SPRING_PROFILES_ACTIVE=prod
   # Set CORS_ALLOWED_ORIGINS=https://yourdomain.com
   ```

2. **SSL Setup (optional):**
   ```bash
   mkdir -p nginx/ssl
   # Place cert.pem and key.pem in nginx/ssl/
   # Uncomment SSL lines in nginx/nginx.conf
   ```

3. **Deploy:**
   ```bash
   docker compose --profile with-nginx up -d --build
   ```

4. **Monitor:**
   ```bash
   # Health checks
   curl http://localhost:5000/api/health
   curl http://localhost:5000/actuator/health
   
   # Logs
   docker compose logs -f backend
   docker compose logs -f frontend
   ```

---

## 🧪 CI/CD Pipeline

Vidhura AI is equipped with an automated, production-grade CI/CD pipeline using **GitHub Actions**:

- **Continuous Integration (`.github/workflows/ci.yml`)**:
  - Compiles Java 17 backend sources and executes automated JUnit 5 tests.
  - Generates Surefire test reports and packages release JAR artifacts.
  - Enforces ESLint standards and validates Next.js production builds.
  - Performs automated security vulnerability auditing with Trivy.
- **Continuous Delivery & Deployment (`.github/workflows/cd.yml`)**:
  - Builds optimized Docker images with Buildx and GitHub Actions layer caching.
  - Publishes multi-tagged images to GitHub Container Registry (`ghcr.io`).
  - Supports zero-downtime rolling deployment via SSH and synthetic post-deployment health probes.

### Local CI Validation
Before committing or opening a PR, run the local CI check:
- **Windows (PowerShell)**: `.\scripts\ci-check.ps1`
- **Linux / macOS**: `./scripts/ci-check.sh`

Detailed architectural documentation, secrets configuration, and deployment guides are available in [CI_CD_PIPELINE.md](CI_CD_PIPELINE.md).

---

## 📄 License

MIT
