#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Phase 15 — MCP Server Docker Compose Smoke Test
#
# Validates the docker-compose.yaml configuration contains the expected
# MCP Server service definitions, dependency chains, security hardening,
# and startup order WITHOUT requiring Docker runtime.
#
# Usage:
#   ./scripts/mcp-smoke-test.sh
#
# Requirements validated:
#   9.1 — MCP Server service defined in platform-net network
#   9.2 — AI Analyzer depends_on mcp-server (service_healthy)
#   9.3 — MCP Server healthcheck configuration
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

COMPOSE_FILE="deployments/docker-compose.yaml"
PASSED=0
FAILED=0

# ─── Colors ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

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

# ─── Pre-flight checks ───────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════════════════════════"
echo "  Phase 15 — MCP Server Docker Compose Smoke Test"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "ERROR: Compose file not found at $COMPOSE_FILE"
    echo "       Run this script from the repository root."
    exit 1
fi

# ─── Validate Compose YAML syntax ────────────────────────────────────────────
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: Compose File Validation"
echo "───────────────────────────────────────────────────────────────────────────"

if command -v docker &> /dev/null && docker compose version &> /dev/null 2>&1; then
    if docker compose -f "$COMPOSE_FILE" config --quiet 2>/dev/null; then
        pass "docker compose config --quiet validates successfully"
    else
        fail "docker compose config --quiet failed"
    fi
else
    info "Docker not available — skipping 'docker compose config' validation"
fi

# ─── Read compose file content for grep-based checks ──────────────────────────
COMPOSE_CONTENT=$(cat "$COMPOSE_FILE")

echo ""
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: MCP Server Service Definition"
echo "───────────────────────────────────────────────────────────────────────────"

# Test 1: MCP Server service exists
if echo "$COMPOSE_CONTENT" | grep -q "mcp-server:"; then
    pass "mcp-server service is defined in compose file"
else
    fail "mcp-server service NOT found in compose file"
fi

# Test 2: MCP Server has healthcheck configured
if echo "$COMPOSE_CONTENT" | grep -A 30 "mcp-server:" | grep -q "healthcheck:"; then
    pass "mcp-server has healthcheck configured"
else
    fail "mcp-server healthcheck NOT configured"
fi

# Test 3: MCP Server healthcheck probes /health endpoint
if echo "$COMPOSE_CONTENT" | grep -A 40 "mcp-server:" | grep -q "/health"; then
    pass "mcp-server healthcheck probes /health endpoint"
else
    fail "mcp-server healthcheck does NOT probe /health endpoint"
fi

# Test 4: MCP Server healthcheck interval is 10s
if echo "$COMPOSE_CONTENT" | grep -A 40 "mcp-server:" | grep "interval:" | grep -q "10s"; then
    pass "mcp-server healthcheck interval is 10s"
else
    fail "mcp-server healthcheck interval is NOT 10s"
fi

# Test 5: MCP Server healthcheck timeout is 5s
if echo "$COMPOSE_CONTENT" | grep -A 40 "mcp-server:" | grep "timeout:" | grep -q "5s"; then
    pass "mcp-server healthcheck timeout is 5s"
else
    fail "mcp-server healthcheck timeout is NOT 5s"
fi

# Test 6: MCP Server healthcheck retries is 5
if echo "$COMPOSE_CONTENT" | grep -A 40 "mcp-server:" | grep "retries:" | grep -q "5"; then
    pass "mcp-server healthcheck retries is 5"
else
    fail "mcp-server healthcheck retries is NOT 5"
fi

# Test 7: MCP Server healthcheck start_period is 20s
if echo "$COMPOSE_CONTENT" | grep -A 40 "mcp-server:" | grep "start_period:" | grep -q "20s"; then
    pass "mcp-server healthcheck start_period is 20s"
else
    fail "mcp-server healthcheck start_period is NOT 20s"
fi

echo ""
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: Security Hardening"
echo "───────────────────────────────────────────────────────────────────────────"

# Extract the mcp-server service block (up to the next top-level service)
MCP_BLOCK=$(sed -n '/^  mcp-server:/,/^  [a-z]/p' "$COMPOSE_FILE" | head -n -1)

# Test 8: read_only filesystem
if echo "$MCP_BLOCK" | grep -q "read_only: true"; then
    pass "mcp-server has read_only: true"
else
    fail "mcp-server does NOT have read_only: true"
fi

# Test 9: no-new-privileges security option
if echo "$MCP_BLOCK" | grep -q "no-new-privileges"; then
    pass "mcp-server has no-new-privileges security option"
else
    fail "mcp-server does NOT have no-new-privileges security option"
fi

# Test 10: non-root user
if echo "$MCP_BLOCK" | grep -q 'user:.*"1000:1000"'; then
    pass "mcp-server runs as non-root user (1000:1000)"
else
    fail "mcp-server does NOT run as non-root user (1000:1000)"
fi

# Test 11: No host port mappings
if echo "$MCP_BLOCK" | grep -q "ports:"; then
    fail "mcp-server has host port mappings (should be internal only)"
else
    pass "mcp-server has no host port mappings (internal only)"
fi

# Test 12: Memory limits configured
if echo "$MCP_BLOCK" | grep -q "memory: 512M"; then
    pass "mcp-server memory limit is 512M"
else
    fail "mcp-server memory limit is NOT 512M"
fi

# Test 13: Memory reservations configured
if echo "$MCP_BLOCK" | grep -q "memory: 128M"; then
    pass "mcp-server memory reservation is 128M"
else
    fail "mcp-server memory reservation is NOT 128M"
fi

echo ""
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: Network Configuration"
echo "───────────────────────────────────────────────────────────────────────────"

# Test 14: MCP Server is on platform-net network
if echo "$MCP_BLOCK" | grep -q "platform-net"; then
    pass "mcp-server is on platform-net network"
else
    fail "mcp-server is NOT on platform-net network"
fi

echo ""
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: Dependency Chain & Startup Order"
echo "───────────────────────────────────────────────────────────────────────────"

# Test 15: MCP Server depends on Kafka with service_healthy
if echo "$MCP_BLOCK" | grep -A 3 "depends_on:" | grep -q "kafka:"; then
    if echo "$MCP_BLOCK" | grep -A 5 "depends_on:" | grep -q "service_healthy"; then
        pass "mcp-server depends_on kafka with condition: service_healthy"
    else
        fail "mcp-server depends_on kafka but WITHOUT service_healthy condition"
    fi
else
    fail "mcp-server does NOT depend_on kafka"
fi

# Extract the ai-analyzer service block
AI_BLOCK=$(sed -n '/^  ai-analyzer:/,/^  [a-z]/p' "$COMPOSE_FILE" | head -n -1)

# Test 16: AI Analyzer depends on mcp-server with service_healthy
if echo "$AI_BLOCK" | grep -A 10 "depends_on:" | grep -q "mcp-server:"; then
    if echo "$AI_BLOCK" | grep -A 12 "depends_on:" | grep -A 2 "mcp-server:" | grep -q "service_healthy"; then
        pass "ai-analyzer depends_on mcp-server with condition: service_healthy"
    else
        fail "ai-analyzer depends_on mcp-server but WITHOUT service_healthy condition"
    fi
else
    fail "ai-analyzer does NOT depend_on mcp-server"
fi

# Test 17: Observability UI depends on ai-analyzer
OBS_BLOCK=$(sed -n '/^  observability-ui:/,/^  [a-z]/p' "$COMPOSE_FILE")
if echo "$OBS_BLOCK" | grep -A 5 "depends_on:" | grep -q "ai-analyzer:"; then
    pass "observability-ui depends_on ai-analyzer"
else
    fail "observability-ui does NOT depend_on ai-analyzer"
fi

# Test 18: Startup order verification (Kafka → MCP Server → AI Analyzer → Observability UI)
info "Verifying startup order: Kafka → MCP Server → AI Analyzer → Observability UI"

STARTUP_ORDER_OK=true

# MCP Server depends on Kafka
if ! echo "$MCP_BLOCK" | grep -A 5 "depends_on:" | grep -q "kafka:"; then
    STARTUP_ORDER_OK=false
fi

# AI Analyzer depends on MCP Server (and Kafka)
if ! echo "$AI_BLOCK" | grep -A 12 "depends_on:" | grep -q "mcp-server:"; then
    STARTUP_ORDER_OK=false
fi

# Observability UI depends on AI Analyzer
if ! echo "$OBS_BLOCK" | grep -A 5 "depends_on:" | grep -q "ai-analyzer:"; then
    STARTUP_ORDER_OK=false
fi

if [ "$STARTUP_ORDER_OK" = true ]; then
    pass "Startup order is correct: Kafka → MCP Server → AI Analyzer → Observability UI"
else
    fail "Startup order is INCORRECT"
fi

echo ""
echo "───────────────────────────────────────────────────────────────────────────"
echo "  Test Suite: Environment Configuration"
echo "───────────────────────────────────────────────────────────────────────────"

# Test 19: MCP Server has MCP_MODE=mock
if echo "$MCP_BLOCK" | grep -q 'MCP_MODE.*mock'; then
    pass "mcp-server has MCP_MODE=mock for local development"
else
    fail "mcp-server does NOT have MCP_MODE=mock"
fi

# Test 20: MCP Server has MCP_PORT=3001
if echo "$MCP_BLOCK" | grep -q 'MCP_PORT.*3001'; then
    pass "mcp-server has MCP_PORT=3001"
else
    fail "mcp-server does NOT have MCP_PORT=3001"
fi

# Test 21: AI Analyzer has MCP Server URL configured
if echo "$AI_BLOCK" | grep -q 'mcp-server:3001'; then
    pass "ai-analyzer has MCP Server URL configured (mcp-server:3001)"
else
    fail "ai-analyzer does NOT have MCP Server URL configured"
fi

# ─── Results ──────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
TOTAL=$((PASSED + FAILED))
echo -e "  Results: ${GREEN}$PASSED passed${NC}, ${RED}$FAILED failed${NC} (out of $TOTAL tests)"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}🎉 All MCP smoke tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some MCP smoke tests failed. Review output above.${NC}"
    exit 1
fi
