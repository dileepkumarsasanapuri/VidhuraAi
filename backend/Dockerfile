# ============================================
# Vidhura AI Backend — Multi-stage Production Dockerfile
# Platform: Java 17 / Spring Boot 3
# ============================================

# ── Stage 1: Build & Package ───────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code and compile
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Minimal Production Runtime ────
FROM eclipse-temurin:17-jre-jammy AS runner

# Install curl for container health check probe
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Security: create dedicated non-root service account
RUN groupadd -r -g 1001 appgroup && \
    useradd -r -u 1001 -g appgroup -s /sbin/nologin -d /app appuser

WORKDIR /app

# Initialize container data directories with non-root ownership
RUN mkdir -p /app/data/policies && chown -R appuser:appgroup /app

# Copy only the compiled JAR from the builder stage
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

USER appuser

# JVM container tuning and runtime environment defaults
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    VIDHURA_POLICY_STORAGE_ROOT="/app/data/policies" \
    SPRING_PROFILES_ACTIVE="prod"

EXPOSE 5000

# Container healthcheck using the unauthenticated /api/health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:5000/api/health || exit 1

# Graceful signal handling: exec replaces shell process with Java PID 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
