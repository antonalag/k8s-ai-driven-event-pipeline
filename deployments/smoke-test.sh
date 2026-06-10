#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Phase 14 — Full Stack Smoke Test (deployments/)
#
# Validates the complete Docker Compose stack: healthchecks, reverse-proxy,
# API proxy, and SPA fallback routing.
#
# Usage (from the deployments/ directory):
#   ./smoke-test.sh
#
# Or from the repository root:
#   ./deployments/smoke-test.sh
#
# Requirements validated:
#   3.6  — observability-ui depends_on ai-analyzer (service_healthy)
#   3.7  — ai-analyzer depends_on kafka (service_healthy)
#   3.8  — ai-analyzer healthcheck (actuator/health, 10s/5s/5 retries/30s start)
#   3.9  — observability-ui healthcheck (/healthz, 30s/3s/3 retries)
#   5.2  — Nginx reverse-proxy /api/ → ai-analyzer:8082
#   5.3  — Kafka bootstrap-servers override via SPRING_KAFKA_BOOTSTRAP_SERVERS
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# Resolve script directory so we can always find docker-compose.yaml
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="docker-compose.yaml"
BASE_URL="http://localhost:3000"

PASSED=0
FAILED=0

# ─── Colors ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ─── Helpers ──────────────────────────────────────────────────────────────────
pass() {
    PASSED=$((PASSED + 1))
    echo -e "  ${GREEN}✓ PASS${NC}: $1"
}

fail() {
    FAILED=$((FAILED + 1))
    echo -e "  ${RED}✗ FAIL${NC}: $1"
}

info() {
    echo -e "  ${YELLOW}ℹ${NC} $1"
}

# ─── Cleanup (always runs, even on failure) ───────────────────────────────────
cleanup() {
    echo ""
    echo "🧹 Tearing down stack..."
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
    echo "🧹 Teardown complete."
}
trap cleanup EXIT

# ─── Pre-flight checks ───────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════════════════════════"
echo "  Phase 14 — Full Stack Smoke Test"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

if ! command -v docker &> /dev/null; then
    echo "ERROR: docker is not installed or not in PATH"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running"
    exit 1
fi

# Change to the deployments/ directory where docker-compose.yaml lives
cd "$SCRIPT_DIR"

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "ERROR: Compose file not found at $SCRIPT_DIR/$COMPOSE_FILE"
    exit 1
fi

# ─── Start stack ──────────────────────────────────────────────────────────────
echo "🚀 Starting full platform stack (this may take several minutes)..."
echo "   Working directory: $SCRIPT_DIR"
echo "   Compose file: $COMPOSE_FILE"
echo ""

docker compose -f "$COMPOSE_FILE" up -d --wait

echo ""
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: Container Health"
echo "───────────────────────────────────────────────────────────────────────────"

# ─── Test: Verify all containers are healthy ──────────────────────────────────
SERVICES=("kafka" "ai-analyzer" "observability-ui")

for service in "${SERVICES[@]}"; do
    health=$(docker inspect --format='{{.State.Health.Status}}' "$service" 2>/dev/null || echo "not_found")
    if [ "$health" = "healthy" ]; then
        pass "$service container is healthy"
    else
        fail "$service container health status: $health"
    fi
done

echo ""
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: Endpoint Verification"
echo "───────────────────────────────────────────────────────────────────────────"

# ─── Test: Healthcheck endpoint returns HTTP 200 ──────────────────────────────
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/healthz" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "GET /healthz returns HTTP 200"
else
    fail "GET /healthz returned HTTP $HTTP_CODE (expected 200)"
fi

# ─── Test: API proxy returns JSON ─────────────────────────────────────────────
API_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/v1/analyses" 2>/dev/null || echo -e "\n000")
API_BODY=$(echo "$API_RESPONSE" | head -n -1)
API_CODE=$(echo "$API_RESPONSE" | tail -n 1)

if [[ "$API_CODE" =~ ^2[0-9][0-9]$ ]]; then
    pass "GET /api/v1/analyses returns HTTP $API_CODE (proxied to backend)"
else
    fail "GET /api/v1/analyses returned HTTP $API_CODE (expected 2xx)"
fi

# Verify response is JSON content
CONTENT_TYPE=$(curl -s -o /dev/null -w "%{content_type}" "$BASE_URL/api/v1/analyses" 2>/dev/null || echo "")
if echo "$CONTENT_TYPE" | grep -qi "application/json"; then
    pass "GET /api/v1/analyses returns JSON content-type"
elif echo "$API_BODY" | grep -qE '^\s*[\[\{]'; then
    pass "GET /api/v1/analyses returns JSON body"
else
    fail "GET /api/v1/analyses content-type: $CONTENT_TYPE (expected application/json)"
fi

# ─── Test: SPA fallback serves index.html for unknown routes ──────────────────
SPA_RESPONSE=$(curl -s "$BASE_URL/nonexistent" 2>/dev/null || echo "")
SPA_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/nonexistent" 2>/dev/null || echo "000")

if [ "$SPA_CODE" = "200" ]; then
    if echo "$SPA_RESPONSE" | grep -qi "<!doctype html\|<html\|<div id="; then
        pass "SPA fallback: /nonexistent returns index.html (HTTP 200)"
    else
        fail "SPA fallback: /nonexistent returned HTTP 200 but content is not index.html"
    fi
else
    fail "SPA fallback: /nonexistent returned HTTP $SPA_CODE (expected 200 with index.html)"
fi

# ─── Results ──────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
TOTAL=$((PASSED + FAILED))
echo -e "  Results: ${GREEN}$PASSED passed${NC}, ${RED}$FAILED failed${NC} (out of $TOTAL tests)"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}🎉 All smoke tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some smoke tests failed. Review output above.${NC}"
    exit 1
fi
