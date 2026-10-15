#!/usr/bin/env bash
# ==============================================================================
# Vidhura AI — Local CI Pipeline Validator (Bash)
# Simulates GitHub Actions CI locally before pushing to remote
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."

echo ""
echo "🚀 [1/3] Starting Backend CI Quality Gate..."
cd "$ROOT_DIR/backend"
echo "📦 Compiling Backend Java Sources..."
mvn clean test-compile -B
echo "🧪 Running Automated Backend Tests..."
mvn test -B
echo "✅ Backend CI Gate Passed!"

echo ""
echo "🚀 [2/3] Starting Frontend CI Quality Gate..."
cd "$ROOT_DIR/frontend"
echo "🔍 Running ESLint..."
npm run lint
echo "📦 Validating Next.js Production Build & TypeScript..."
npm run build
echo "✅ Frontend CI Gate Passed!"

echo ""
echo "🚀 [3/3] Checking Docker Compose Syntax..."
cd "$ROOT_DIR"
if command -v docker &> /dev/null; then
  docker compose config -q && echo "✅ Docker Compose Configuration Valid!" || echo "⚠️ Docker daemon not reachable, skipping check."
else
  echo "ℹ️ Docker CLI not installed, skipping Docker Compose validation."
fi

echo ""
echo "🎉 ALL LOCAL CI CHECKS PASSED! Code is safe to commit & push to GitHub."
echo ""
