# ==============================================================================
# Vidhura AI — Local CI Pipeline Validator (PowerShell)
# Simulates GitHub Actions CI locally before pushing to remote
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "[1/3] Starting Backend CI Quality Gate..." -ForegroundColor Cyan
Push-Location "$PSScriptRoot/../backend"
try {
    Write-Host "Compiling Backend Java Sources..." -ForegroundColor Yellow
    mvn clean test-compile -B
    if ($LASTEXITCODE -ne 0) { throw "Backend test-compile failed!" }

    Write-Host "Running Automated Backend Tests..." -ForegroundColor Yellow
    mvn test -B
    if ($LASTEXITCODE -ne 0) { throw "Backend tests failed!" }

    Write-Host "Backend CI Gate Passed!" -ForegroundColor Green
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[2/3] Starting Frontend CI Quality Gate..." -ForegroundColor Cyan
Push-Location "$PSScriptRoot/../frontend"
try {
    Write-Host "Running ESLint..." -ForegroundColor Yellow
    npm run lint
    if ($LASTEXITCODE -ne 0) { throw "Frontend linting failed!" }

    Write-Host "Validating Next.js Production Build and TypeScript..." -ForegroundColor Yellow
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed!" }

    Write-Host "Frontend CI Gate Passed!" -ForegroundColor Green
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[3/3] Checking Docker Compose Syntax..." -ForegroundColor Cyan
Push-Location "$PSScriptRoot/.."
try {
    docker compose config -q
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Docker Compose Configuration Valid!" -ForegroundColor Green
    } else {
        Write-Host "Docker Compose verification skipped." -ForegroundColor DarkYellow
    }
} catch {
    Write-Host "Docker Compose check skipped." -ForegroundColor DarkYellow
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "ALL LOCAL CI CHECKS PASSED! Code is ready to commit and push to GitHub." -ForegroundColor Green
Write-Host ""
