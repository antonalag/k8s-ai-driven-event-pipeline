#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Phase 17 — Remediation E2E Smoke Test
# Validates the full POST /api/v1/remediations request/response contract.
# Runs against ai-analyzer with MCP_MODE=mock (no real cluster needed).
#
# Usage: bash scripts/remediation-smoke-test.sh [BASE_URL]
#   BASE_URL defaults to http://localhost:8082
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

BASE_URL="${1:-http://localhost:8082}"
ENDPOINT="${BASE_URL}/api/v1/remediations"
CORRELATION_ID="550e8400-e29b-41d4-a716-446655440000"
PASS=0
FAIL=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

assert_status() {
  local test_name="$1"
  local expected_status="$2"
  local actual_status="$3"

  if [ "$actual_status" -eq "$expected_status" ]; then
    echo -e "${GREEN}✓ PASS${NC}: $test_name (HTTP $actual_status)"
    PASS=$((PASS + 1))
  else
    echo -e "${RED}✕ FAIL${NC}: $test_name (expected HTTP $expected_status, got HTTP $actual_status)"
    FAIL=$((FAIL + 1))
  fi
}

assert_contains() {
  local test_name="$1"
  local expected="$2"
  local body="$3"

  if echo "$body" | grep -q "$expected"; then
    echo -e "${GREEN}  ↳ Contains${NC}: '$expected'"
  else
    echo -e "${RED}  ↳ MISSING${NC}: '$expected' not found in response"
    FAIL=$((FAIL + 1))
  fi
}

echo "═══════════════════════════════════════════════════════════════"
echo " Phase 17 — Remediation Smoke Test"
echo " Target: $ENDPOINT"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# ─── Test 1: Valid restart_deployment → 200 ──────────────────────────────────
echo "── Test 1: restart_deployment (valid) ──"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"$CORRELATION_ID\",
    \"action\": \"restart_deployment\",
    \"deploymentName\": \"chaos-crash\",
    \"namespace\": \"chaos-validation\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status "restart_deployment → 200" 200 "$HTTP_CODE"
assert_contains "Test 1" "restart_deployment" "$BODY"
assert_contains "Test 1" "completed" "$BODY"
echo ""

# ─── Test 2: Idempotency — same correlationId → 200 (cached) ────────────────
echo "── Test 2: Idempotency (same correlationId) ──"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"$CORRELATION_ID\",
    \"action\": \"restart_deployment\",
    \"deploymentName\": \"chaos-crash\",
    \"namespace\": \"chaos-validation\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status "idempotent re-POST → 200" 200 "$HTTP_CODE"
assert_contains "Test 2" "completed" "$BODY"
echo ""

# ─── Test 3: scale_deployment with valid replicas → 200 ─────────────────────
echo "── Test 3: scale_deployment (replicas=3) ──"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"660e8400-e29b-41d4-a716-446655440001\",
    \"action\": \"scale_deployment\",
    \"deploymentName\": \"chaos-oom\",
    \"namespace\": \"chaos-validation\",
    \"replicas\": 3
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status "scale_deployment → 200" 200 "$HTTP_CODE"
assert_contains "Test 3" "scale_deployment" "$BODY"
echo ""

# ─── Test 4: fix_container_image → 200 ──────────────────────────────────────
echo "── Test 4: fix_container_image ──"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"770e8400-e29b-41d4-a716-446655440002\",
    \"action\": \"fix_container_image\",
    \"deploymentName\": \"chaos-imagepull\",
    \"namespace\": \"chaos-validation\",
    \"containerName\": \"main\",
    \"correctImage\": \"nginx:1.27-alpine\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status "fix_container_image → 200" 200 "$HTTP_CODE"
assert_contains "Test 4" "fix_container_image" "$BODY"
echo ""

# ─── Test 5: Invalid params → 400 (missing action) ──────────────────────────
echo "── Test 5: Validation error (missing action) ──"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"880e8400-e29b-41d4-a716-446655440003\",
    \"deploymentName\": \"app\",
    \"namespace\": \"default\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status "missing action → 400" 400 "$HTTP_CODE"
assert_contains "Test 5" "Validation" "$BODY"
echo ""

# ─── Test 6: Invalid action name → 400 ──────────────────────────────────────
echo "── Test 6: Invalid action name ──"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"990e8400-e29b-41d4-a716-446655440004\",
    \"action\": \"delete_pod\",
    \"deploymentName\": \"app\",
    \"namespace\": \"default\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status "invalid action → 400" 400 "$HTTP_CODE"
echo ""

# ─── Test 7: Replicas out of bounds → 400 ───────────────────────────────────
echo "── Test 7: Replicas out of bounds (15) ──"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"aa0e8400-e29b-41d4-a716-446655440005\",
    \"action\": \"scale_deployment\",
    \"deploymentName\": \"app\",
    \"namespace\": \"default\",
    \"replicas\": 15
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status "replicas=15 → 400" 400 "$HTTP_CODE"
echo ""

# ─── Test 8: X-Correlation-Id header present ────────────────────────────────
echo "── Test 8: X-Correlation-Id response header ──"
HEADERS=$(curl -s -D - -o /dev/null -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{
    \"correlationId\": \"bb0e8400-e29b-41d4-a716-446655440006\",
    \"action\": \"restart_deployment\",
    \"deploymentName\": \"web\",
    \"namespace\": \"chaos-validation\"
  }")
if echo "$HEADERS" | grep -qi "X-Correlation-Id"; then
  echo -e "${GREEN}✓ PASS${NC}: X-Correlation-Id header present"
  PASS=$((PASS + 1))
else
  echo -e "${RED}✕ FAIL${NC}: X-Correlation-Id header missing"
  FAIL=$((FAIL + 1))
fi
echo ""

# ─── Summary ─────────────────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════════════"
echo -e " Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
echo "═══════════════════════════════════════════════════════════════"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
exit 0
