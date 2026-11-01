# Vidhura AI — Docker Development & Containerization Guide

## 1. Overview & Architecture

Vidhura AI utilizes a multi-container architecture orchestrated via Docker Compose:

| Component | Technology | Runtime Base | Container Port | Health Endpoint |
|---|---|---|---|---|
| **Frontend** | Next.js 15, React 19, TypeScript | `node:20-alpine` | `3000` | `http://localhost:3000/` (`wget`) |
| **Backend** | Spring Boot 3, Java 17, Maven | `eclipse-temurin:17-jre-jammy` | `5000` | `http://localhost:5000/api/health` (`curl`) |
| **Database** | MongoDB 7 (or MongoDB Atlas) | `mongo:7` | `27017` | `mongosh --eval "db.adminCommand('ping')"` |
| **Reverse Proxy** | Nginx (optional/prod) | `nginx:alpine` | `80`, `443` | TCP port binding |

---

## 2. Security & Container Principles

1. **Multi-Stage Builds:**
   - Backend: Dependencies and application source are compiled inside `maven:3.9-eclipse-temurin-17`. Only the resulting standalone `*.jar` is copied into `eclipse-temurin:17-jre-jammy`. Source code, build tools, and Maven local repositories are excluded from the runtime image.
   - Frontend: Dependencies installed via `npm ci` in `deps` stage, compiled via `npm run build` using Next.js `output: "standalone"`, and only compiled artifacts (`standalone/`, `static/`, `public/`) are copied to the runtime image.
2. **Non-Root Execution:**
   - Backend executes under system user `appuser:appgroup` (`UID 1001, GID 1001`).
   - Frontend executes under system user `nextjs:nodejs` (`UID 1001, GID 1001`).
3. **No Embedded Secrets:**
   - Image builds never bake in database credentials, JWT secrets, or API keys. All credentials are injected via runtime environment variables.
4. **JVM Container Tuning:**
   - Containers enforce `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom` to ensure the JVM respects cgroup container memory limits.
5. **Process Signal Propagation:**
   - Both Dockerfiles execute container entrypoints via `exec` form (`exec java $JAVA_OPTS -jar app.jar` and `node server.js`) so OS signals (`SIGTERM`, `SIGINT`) are handled directly by the main process without being trapped by a shell wrapper.

---

## 3. Local Development Workflows

### Prerequisites
- Docker Engine 24+ and Docker Compose v2.20+
- (Optional) Docker Desktop on Windows/macOS

### Quickstart (Full Local Stack with Local MongoDB)
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Build and start containers in detached mode
docker compose up -d --build

# 3. View service logs
docker compose logs -f backend
docker compose logs -f frontend

# 4. Check container health status
docker compose ps
```

### Developing with Hosted MongoDB Atlas
If your team uses MongoDB Atlas rather than a local MongoDB container:
1. Update `.env`:
   ```bash
   MONGODB_URI=mongodb+srv://<username>:<password>@cluster.mongodb.net/vidhura?retryWrites=true&w=majority
   ```
2. Start only the backend and frontend:
   ```bash
   docker compose up -d --build backend frontend
   ```

---

## 4. Policy Document Storage Isolation

- In Windows local environments, raw policy PDFs and manifests can reside on external storage (`E:\VidhuraData\policies`).
- In Docker environments, policy storage is decoupled and mounted to a standard container path:
  ```yaml
  volumes:
    - policy_data:/app/data/policies
  environment:
    VIDHURA_POLICY_STORAGE_ROOT: /app/data/policies
  ```
- Neither `.gitignore` nor `.dockerignore` permit raw policy PDFs or external storage directories to enter version control or container image build contexts.

---

## 5. Verification & Health Monitoring

To verify container health:
```bash
# Check backend health
curl -f http://localhost:5000/api/health

# Check frontend availability
curl -I http://localhost:3000/

# Inspect container health checks
docker inspect --format='{{json .State.Health}}' vidhura-backend | jq .
docker inspect --format='{{json .State.Health}}' vidhura-frontend | jq .
```
