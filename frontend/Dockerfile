# ============================================
# Vidhura AI Frontend — Multi-stage Production Dockerfile
# Platform: Next.js 15 / React 19 / Node 20
# ============================================

# ── Stage 1: Install Dependencies ──────────
FROM node:20-alpine AS deps
WORKDIR /app

# Install dependencies deterministically via package-lock.json
COPY package.json package-lock.json ./
RUN npm ci

# ── Stage 2: Production Build ──────────────
FROM node:20-alpine AS builder
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build-time public arguments (browser-safe only; NO private secrets)
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID

ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL:-/api} \
    NEXT_PUBLIC_GOOGLE_CLIENT_ID=${NEXT_PUBLIC_GOOGLE_CLIENT_ID:-} \
    NEXT_TELEMETRY_DISABLED=1 \
    NODE_ENV=production

# Compile Next.js application with standalone output mode
RUN npm run build

# ── Stage 3: Minimal Production Runtime ────
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME="0.0.0.0"

# Security: create unprivileged system group and user
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

# Copy static assets and compiled standalone bundle
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

# Health check verifies the HTTP server returns a valid response
HEALTHCHECK --interval=30s --timeout=10s --start-period=25s --retries=3 \
  CMD wget -qO- http://localhost:3000/ || exit 1

CMD ["node", "server.js"]
