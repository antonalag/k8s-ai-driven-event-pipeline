#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Security Compliance Audit Script — Phase 14 / Task 7.2
#
# Validates container security hardening across all platform services.
#
# Two modes:
#   1. STATIC analysis — runs against files, no Docker required
#   2. RUNTIME checks — requires running containers (auto-skipped if not up)
#
# Usage:
#   ./deployments/security-audit.sh              # Full audit (static + runtime)
#   ./deployments/security-audit.sh --static-only  # Static checks only
#
# Validates Requirements: 6.1, 6.2, 6.3, 6.5, 6.6, 6.7, 6.8
#
# Exit codes:
#   0 — All executed checks passed
#   1 — One or more checks failed
# ──────────────────────────────────────────────────────────────────────────────

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PASS=0
FAIL=0
SKIP=0
STATIC_ONLY="${1:-}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

pass() {
  echo -e "  ${GREEN}✅ PASS${NC}: $1"
  PASS=$((PASS + 1))
}

fail() {
  echo -e "  ${RED}❌ FAIL${NC}: $1"
  FAIL=$((FAIL + 1))
}

skip() {
  echo -e "  ${YELLOW}⏭  SKIP${NC}: $1"
  SKIP=$((SKIP + 1))
}

header() {
  echo ""
  echo -e "${CYAN}───────────────────────────────────────────────────────────────${NC}"
  echo -e "${CYAN}  $1${NC}"
  echo -e "${CYAN}───────────────────────────────────────────────────────────────${NC}"
}

# ──────────────────────────────────────────────────────────────────────────────
# STATIC ANALYSIS — Can run without Docker
# ──────────────────────────────────────────────────────────────────────────────

header "1. SHA-256 Image Pinning Audit (Req 6.8)"

# Check all Dockerfiles
DOCKERFILES=(
  "$REPO_ROOT/ui/Dockerfile"
  "$REPO_ROOT/services/ai-analyzer/Dockerfile"
)

for dockerfile in "${DOCKERFILES[@]}"; do
  rel_path="${dockerfile#$REPO_ROOT/}"
  if [ ! -f "$dockerfile" ]; then
    fail "$rel_path — file not found"
    continue
  fi

  from_total=$(grep -c '^FROM ' "$dockerfile")
  from_pinned=$(grep -cE '^FROM .+@sha256:[a-f0-9]{64}' "$dockerfile" || true)

  if [ "$from_pinned" -eq "$from_total" ] && [ "$from_total" -gt 0 ]; then
    pass "$rel_path — all $from_total FROM directives use @sha256: pinning"
  else
    fail "$rel_path — $from_pinned of $from_total FROM directives use @sha256: pinning"
    # Show the offending lines
    grep '^FROM ' "$dockerfile" | grep -v '@sha256:' | while read -r line; do
      echo -e "       ${RED}→ $line${NC}"
    done
  fi
done

# Check compose image fields
COMPOSE_KAFKA="$REPO_ROOT/deployments/docker-compose/docker-compose.yml"
if [ -f "$COMPOSE_KAFKA" ]; then
  image_total=$(grep -cE '^\s+image:' "$COMPOSE_KAFKA" || true)
  image_pinned=$(grep -cE '^\s+image:.*@sha256:[a-f0-9]{64}' "$COMPOSE_KAFKA" || true)

  if [ "$image_pinned" -eq "$image_total" ] && [ "$image_total" -gt 0 ]; then
    pass "docker-compose/docker-compose.yml — all $image_total image fields use @sha256: pinning"
  else
    fail "docker-compose/docker-compose.yml — $image_pinned of $image_total image fields use @sha256: pinning"
  fi
else
  fail "docker-compose/docker-compose.yml — file not found"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "2. no-new-privileges Security Option (Req 6.7)"

COMPOSE_GLOBAL="$REPO_ROOT/deployments/docker-compose.yaml"

# Application services that MUST have no-new-privileges
APP_SERVICES=("ai-analyzer" "observability-ui")

for svc in "${APP_SERVICES[@]}"; do
  # Use sed to extract service block (from service key to next top-level service or end)
  svc_block=$(sed -n "/^  ${svc}:/,/^  [a-z]/p" "$COMPOSE_GLOBAL")
  if echo "$svc_block" | grep -qE 'no-new-privileges:true'; then
    pass "$svc — no-new-privileges:true is set"
  else
    # Fallback: check the whole file since last service may not have a following service
    if sed -n "/^  ${svc}:/,\$p" "$COMPOSE_GLOBAL" | grep -qE 'no-new-privileges:true'; then
      pass "$svc — no-new-privileges:true is set"
    else
      fail "$svc — no-new-privileges:true NOT found in docker-compose.yaml"
    fi
  fi
done

# Also check kafka services
if grep -qE 'no-new-privileges:true' "$COMPOSE_KAFKA"; then
  pass "kafka services — no-new-privileges:true is set"
else
  fail "kafka services — no-new-privileges:true NOT found"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "3. Read-Only Filesystem (Req 6.6)"

for svc in "${APP_SERVICES[@]}"; do
  svc_block=$(sed -n "/^  ${svc}:/,/^  [a-z]/p" "$COMPOSE_GLOBAL")
  if echo "$svc_block" | grep -qE 'read_only:\s*true'; then
    pass "$svc — read_only: true is set"
  else
    if sed -n "/^  ${svc}:/,\$p" "$COMPOSE_GLOBAL" | grep -qE 'read_only:\s*true'; then
      pass "$svc — read_only: true is set"
    else
      fail "$svc — read_only: true NOT found in docker-compose.yaml"
    fi
  fi
done

# ──────────────────────────────────────────────────────────────────────────────

header "4. No Docker Socket or Host Root Mounts (Req 6.3)"

DANGEROUS_FOUND=0

# Search compose YAML files (exclude scripts) for docker.sock
if grep -rlE 'docker\.sock' "$REPO_ROOT/deployments/" --include='*.yml' --include='*.yaml' 2>/dev/null | grep -q .; then
  fail "Docker socket mount (/var/run/docker.sock) found in compose files"
  DANGEROUS_FOUND=1
fi

# Check for host root mounts in YAML files: volumes mapping /:
if grep -rE '^\s*-\s*["\x27]?/:/|^\s*-\s*["\x27]?/["\x27]?\s*$' "$REPO_ROOT/deployments/" --include='*.yml' --include='*.yaml' 2>/dev/null | grep -q .; then
  fail "Host root filesystem mount (/) found in compose files"
  DANGEROUS_FOUND=1
fi

# Check for Docker socket path in volume mounts
if grep -rE '/var/run/docker\.sock' "$REPO_ROOT/deployments/" --include='*.yml' --include='*.yaml' 2>/dev/null | grep -q .; then
  fail "Docker socket path found in volume mounts"
  DANGEROUS_FOUND=1
fi

if [ "$DANGEROUS_FOUND" -eq 0 ]; then
  pass "No Docker socket or host root filesystem mounts detected"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "5. Memory Limits (Req 6.5)"

# Expected memory limits
declare -A EXPECTED_MEM
EXPECTED_MEM[observability-ui]="256M"
EXPECTED_MEM[ai-analyzer]="768M"

for svc in "${!EXPECTED_MEM[@]}"; do
  expected="${EXPECTED_MEM[$svc]}"
  svc_block=$(sed -n "/^  ${svc}:/,/^  [a-z]/p" "$COMPOSE_GLOBAL")
  if echo "$svc_block" | grep -qE "memory:\s*${expected}"; then
    pass "$svc — memory limit ${expected} configured"
  else
    if sed -n "/^  ${svc}:/,\$p" "$COMPOSE_GLOBAL" | grep -qE "memory:\s*${expected}"; then
      pass "$svc — memory limit ${expected} configured"
    else
      fail "$svc — memory limit ${expected} NOT found"
    fi
  fi
done

# Check kafka memory in its compose file
if grep -B5 -A5 'limits:' "$COMPOSE_KAFKA" | grep -qE 'memory:\s*1024M'; then
  pass "kafka — memory limit 1024M configured"
else
  fail "kafka — memory limit 1024M NOT found"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "6. Non-Root USER Directive in Dockerfiles (Req 6.1, 6.2)"

echo "  Checking ui/Dockerfile for USER directive..."
if grep -qE '^USER\s+nginx' "$REPO_ROOT/ui/Dockerfile"; then
  pass "ui/Dockerfile — USER nginx (UID 101)"
else
  fail "ui/Dockerfile — non-root USER directive not found"
fi

echo "  Checking services/ai-analyzer/Dockerfile for USER directive..."
if grep -qE '^USER\s+appuser' "$REPO_ROOT/services/ai-analyzer/Dockerfile"; then
  pass "services/ai-analyzer/Dockerfile — USER appuser (UID 1000)"
else
  fail "services/ai-analyzer/Dockerfile — non-root USER directive not found"
fi

# ──────────────────────────────────────────────────────────────────────────────
# RUNTIME CHECKS — Require running containers
# ──────────────────────────────────────────────────────────────────────────────

if [ "$STATIC_ONLY" = "--static-only" ]; then
  header "RUNTIME CHECKS: Skipped (--static-only)"
  echo "  Runtime checks require containers to be running."
  echo "  Start the stack with: docker compose -f deployments/docker-compose.yaml up -d --wait"
  echo "  Then re-run this script without --static-only."
else
  # Check if Docker is available
  if ! command -v docker &>/dev/null; then
    header "RUNTIME CHECKS: Skipped (Docker not available)"
    skip "Docker command not found — cannot run runtime checks"
  else
    # Check if containers are running
    UI_RUNNING=$(docker inspect --format='{{.State.Running}}' observability-ui 2>/dev/null || echo "false")
    ANALYZER_RUNNING=$(docker inspect --format='{{.State.Running}}' ai-analyzer 2>/dev/null || echo "false")

    if [ "$UI_RUNNING" = "false" ] && [ "$ANALYZER_RUNNING" = "false" ]; then
      header "RUNTIME CHECKS: Skipped (containers not running)"
      echo "  No platform containers detected. Start with:"
      echo "    docker compose -f deployments/docker-compose.yaml up -d --wait"
      skip "Containers not running — runtime verification skipped"
    else

      # ── Non-root execution verification ──────────────────────────────────

      header "7. Runtime: Non-Root Execution (Req 6.1, 6.2)"

      if [ "$UI_RUNNING" = "true" ]; then
        UI_USER=$(docker exec observability-ui whoami 2>/dev/null || echo "UNKNOWN")
        if [ "$UI_USER" = "nginx" ]; then
          pass "observability-ui runs as 'nginx' (UID 101)"
        else
          fail "observability-ui runs as '$UI_USER' — expected 'nginx'"
        fi
      else
        skip "observability-ui container not running"
      fi

      if [ "$ANALYZER_RUNNING" = "true" ]; then
        ANALYZER_USER=$(docker exec ai-analyzer whoami 2>/dev/null || echo "UNKNOWN")
        if [ "$ANALYZER_USER" = "appuser" ]; then
          pass "ai-analyzer runs as 'appuser' (UID 1000)"
        else
          fail "ai-analyzer runs as '$ANALYZER_USER' — expected 'appuser'"
        fi
      else
        skip "ai-analyzer container not running"
      fi

      # ── Read-only filesystem verification ────────────────────────────────

      header "8. Runtime: Read-Only Filesystem (Req 6.6)"

      if [ "$UI_RUNNING" = "true" ]; then
        # Root write should FAIL
        if docker exec observability-ui touch /test-security-audit 2>/dev/null; then
          fail "observability-ui — root filesystem is WRITABLE (should be read-only)"
          docker exec observability-ui rm -f /test-security-audit 2>/dev/null || true
        else
          pass "observability-ui — root filesystem is read-only"
        fi

        # tmpfs write should SUCCEED
        if docker exec observability-ui touch /tmp/test-security-audit 2>/dev/null; then
          pass "observability-ui — tmpfs /tmp is writable"
          docker exec observability-ui rm -f /tmp/test-security-audit 2>/dev/null || true
        else
          fail "observability-ui — tmpfs /tmp is NOT writable"
        fi

        if docker exec observability-ui touch /var/cache/nginx/test-security-audit 2>/dev/null; then
          pass "observability-ui — tmpfs /var/cache/nginx is writable"
          docker exec observability-ui rm -f /var/cache/nginx/test-security-audit 2>/dev/null || true
        else
          fail "observability-ui — tmpfs /var/cache/nginx is NOT writable"
        fi
      else
        skip "observability-ui container not running — filesystem checks skipped"
      fi

      if [ "$ANALYZER_RUNNING" = "true" ]; then
        # Root write should FAIL
        if docker exec ai-analyzer touch /test-security-audit 2>/dev/null; then
          fail "ai-analyzer — root filesystem is WRITABLE (should be read-only)"
          docker exec ai-analyzer rm -f /test-security-audit 2>/dev/null || true
        else
          pass "ai-analyzer — root filesystem is read-only"
        fi

        # tmpfs write should SUCCEED
        if docker exec ai-analyzer touch /tmp/test-security-audit 2>/dev/null; then
          pass "ai-analyzer — tmpfs /tmp is writable"
          docker exec ai-analyzer rm -f /tmp/test-security-audit 2>/dev/null || true
        else
          fail "ai-analyzer — tmpfs /tmp is NOT writable"
        fi
      else
        skip "ai-analyzer container not running — filesystem checks skipped"
      fi

      # ── Memory limits verification ──────────────────────────────────────

      header "9. Runtime: Memory Limits Verification (Req 6.5)"

      if [ "$UI_RUNNING" = "true" ]; then
        UI_MEM=$(docker inspect --format='{{.HostConfig.Memory}}' observability-ui 2>/dev/null || echo "0")
        if [ "$UI_MEM" = "268435456" ]; then
          pass "observability-ui — memory limit 256MB (268435456 bytes) confirmed"
        else
          fail "observability-ui — memory limit is ${UI_MEM} bytes (expected 268435456 / 256MB)"
        fi
      else
        skip "observability-ui container not running — memory check skipped"
      fi

      if [ "$ANALYZER_RUNNING" = "true" ]; then
        ANALYZER_MEM=$(docker inspect --format='{{.HostConfig.Memory}}' ai-analyzer 2>/dev/null || echo "0")
        if [ "$ANALYZER_MEM" = "805306368" ]; then
          pass "ai-analyzer — memory limit 768MB (805306368 bytes) confirmed"
        else
          fail "ai-analyzer — memory limit is ${ANALYZER_MEM} bytes (expected 805306368 / 768MB)"
        fi
      else
        skip "ai-analyzer container not running — memory check skipped"
      fi
    fi
  fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# SUMMARY
# ──────────────────────────────────────────────────────────────────────────────

header "AUDIT SUMMARY"

echo ""
echo -e "  ${GREEN}Passed:  $PASS${NC}"
echo -e "  ${RED}Failed:  $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""

if [ "$FAIL" -gt 0 ]; then
  echo -e "  ${RED}❌ Security audit FAILED — $FAIL issue(s) require remediation${NC}"
  exit 1
else
  echo -e "  ${GREEN}✅ Security audit PASSED — all executed checks are compliant${NC}"
  exit 0
fi
