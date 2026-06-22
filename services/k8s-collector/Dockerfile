# ──────────────────────────────────────────────────────────────────────────────
# K8s Collector — Multi-Stage Dockerfile
#
# Stage 1: Build with Gradle (eclipse-temurin:21-jdk-alpine)
# Stage 2: Runtime with minimal JRE (eclipse-temurin:21-jre-alpine)
#
# Security: Non-root user (appuser UID 1000), SHA-256 pinned base images
#
# NOTE: Build context is the repository ROOT (not services/k8s-collector/).
#       docker-compose sets: context: ../ dockerfile: services/k8s-collector/Dockerfile
# ──────────────────────────────────────────────────────────────────────────────

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine@sha256:4fb80de7aeb277ad949cfbe89b4f504e50bb34c57fd908c5825236473d71e986 AS build
WORKDIR /app

# Copy Gradle wrapper and build configuration (root-level)
COPY gradlew ./
COPY gradle/wrapper/ gradle/wrapper/
COPY build.gradle settings.gradle ./

# Copy subproject build file
COPY services/k8s-collector/build.gradle services/k8s-collector/build.gradle

# Copy subproject source
COPY services/k8s-collector/src/ services/k8s-collector/src/

# Build the Spring Boot fat JAR
RUN chmod +x gradlew && ./gradlew :services:k8s-collector:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine@sha256:704db3c40204a44f471191446ddd9cda5d60dab40f0e15c6507b815ed897238b AS runtime
WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser

# Copy JAR from build stage
COPY --from=build /app/services/k8s-collector/build/libs/*.jar app.jar

# Copy entrypoint script (rewrites kubeconfig for Docker networking)
COPY services/k8s-collector/docker-entrypoint.sh /app/docker-entrypoint.sh

# Create .kube directory for kubeconfig mount
RUN mkdir -p /home/appuser/.kube && chown appuser:appgroup /home/appuser/.kube && \
    chmod +x /app/docker-entrypoint.sh

# Switch to non-root user
USER appuser

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=20s \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
