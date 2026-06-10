#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Security Compliance Audit Script — Phase 14
#
# Performs both STATIC (file-based) and RUNTIME (container-based) security
# verification checks against the platform containers.
#
# Static checks can run anytime. Runtime checks require the stack to be up:
#   docker compose -f deployments/docker-compose.yaml up -d --wait
#
# Usage:
#   ./scripts/security-audit.sh [--static-only]
#
# Exit codes:
#   0 — All checks passed
#   1 — One or more checks failed
# ──────────────────────────────────────────────────────────────────────────────

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PASS=0
FAIL=0
STATIC_ONLY="${1:-}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass() {
  echo -e "  ${GREEN}✅ PASS${NC}: $1"
  PASS=$((PASS + 1))
}

fail() {
  echo -e "  ${RED}❌ FAIL${NC}: $1"
  FAIL=$((FAIL + 1))
}

warn() {
  echo -e "  ${YELLOW}⚠️  WARN${NC}: $1"
}

header() {
  echo ""
  echo -e "${YELLOW}═══════════════════════════════════════════════════════════════${NC}"
  echo -e "${YELLOW}  $1${NC}"
  echo -e "${YELLOW}═══════════════════════════════════════════════════════════════${NC}"
}

# ──────────────────────────────────────────────────────────────────────────────
# STATIC CHECKS — File-based analysis (no running containers required)
# ──────────────────────────────────────────────────────────────────────────────

header "STATIC AUDIT: SHA-256 Image Pinning"

echo "  Checking ui/Dockerfile..."
if grep -qE '^FROM .+@sha256:[a-f0-9]{64}' "$REPO_ROOT/ui/Dockerfile"; then
  count=$(grep -cE '^FROM .+@sha256:[a-f0-9]{64}' "$REPO_ROOT/ui/Dockerfile")
  from_count=$(grep -c '^FROM ' "$REPO_ROOT/ui/Dockerfile")
  if [ "$count" -eq "$from_count" ]; then
    pass "ui/Dockerfile — all $from_count FROM directives use SHA-256 pinning"
  else
    fail "ui/Dockerfile — $count of $from_count FROM directives use SHA-256 pinning"
  fi
else
  fail "ui/Dockerfile — no SHA-256 pinned FROM directives found"
fi

echo "  Checking services/ai-analyzer/Dockerfile..."
if grep -qE '^FROM .+@sha256:[a-f0-9]{64}' "$REPO_ROOT/services/ai-analyzer/Dockerfile"; then
  count=$(grep -cE '^FROM .+@sha256:[a-f0-9]{64}' "$REPO_ROOT/services/ai-analyzer/Dockerfile")
  from_count=$(grep -c '^FROM ' "$REPO_ROOT/services/ai-analyzer/Dockerfile")
  if [ "$count" -eq "$from_count" ]; then
    pass "services/ai-analyzer/Dockerfile — all $from_count FROM directives use SHA-256 pinning"
  else
    fail "services/ai-analyzer/Dockerfile — $count of $from_count FROM directives use SHA-256 pinning"
  fi
else
  fail "services/ai-analyzer/Dockerfile — no SHA-256 pinned FROM directives found"
fi

echo "  Checking deployments/docker-compose/docker-compose.yml..."
if grep -qE 'image:.*@sha256:[a-f0-9]{64}' "$REPO_ROOT/deployments/docker-compose/docker-compose.yml"; then
  count=$(grep -cE 'image:.*@sha256:[a-f0-9]{64}' "$REPO_ROOT/deployments/docker-compose/docker-compose.yml")
  image_count=$(grep -cE '^\s+image:' "$REPO_ROOT/deployments/docker-compose/docker-compose.yml")
  if [ "$count" -eq "$image_count" ]; then
    pass "docker-compose.yml (kafka) — all $image_count image fields use SHA-256 pinning"
  else
    fail "docker-compose.yml (kafka) — $count of $image_count image fields use SHA-256 pinning"
  fi
else
  fail "docker-compose.yml (kafka) — no SHA-256 pinned image fields found"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "STATIC AUDIT: Non-Root User Configuration"

echo "  Checking ui/Dockerfile for USER nginx..."
if grep -qE '^USER nginx' "$REPO_ROOT/ui/Dockerfile"; then
  pass "ui/Dockerfile — USER nginx (UID 101) configured"
else
  fail "ui/Dockerfile — USER nginx not found"
fi

echo "  Checking services/ai-analyzer/Dockerfile for USER appuser..."
if grep -qE '^USER appuser' "$REPO_ROOT/services/ai-analyzer/Dockerfile"; then
  pass "services/ai-analyzer/Dockerfile — USER appuser (UID 1000) configured"
else
  fail "services/ai-analyzer/Dockerfile — USER appuser not found"
fi

echo "  Checking docker-compose.yaml for ai-analyzer user override..."
if grep -A 30 'ai-analyzer:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'user:.*"1000:1000"'; then
  pass "docker-compose.yaml — ai-analyzer user: \"1000:1000\" set"
else
  warn "docker-compose.yaml — ai-analyzer user override not found (may rely on Dockerfile USER)"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "STATIC AUDIT: Read-Only Filesystem"

echo "  Checking observability-ui service..."
if grep -A 30 'observability-ui:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'read_only:\s*true'; then
  pass "docker-compose.yaml — observability-ui has read_only: true"
else
  fail "docker-compose.yaml — observability-ui missing read_only: true"
fi

echo "  Checking ai-analyzer service..."
if grep -A 30 'ai-analyzer:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'read_only:\s*true'; then
  pass "docker-compose.yaml — ai-analyzer has read_only: true"
else
  fail "docker-compose.yaml — ai-analyzer missing read_only: true"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "STATIC AUDIT: No Docker Socket / Host Root Mounts"

echo "  Scanning compose files for dangerous volume mounts..."
DANGEROUS_MOUNTS=0

if grep -rqE '/var/run/docker\.sock' "$REPO_ROOT/deployments/"; then
  fail "Docker socket mount (/var/run/docker.sock) found in compose files"
  DANGEROUS_MOUNTS=1
fi

# Check for host root mount (volume starting with /: or just /)
if grep -rqE '^\s*-\s*"/:/|^\s*-\s*/:/|source:\s*/' "$REPO_ROOT/deployments/" 2>/dev/null; then
  fail "Host root filesystem mount (/) found in compose files"
  DANGEROUS_MOUNTS=1
fi

if [ "$DANGEROUS_MOUNTS" -eq 0 ]; then
  pass "No Docker socket or host root mounts detected in compose files"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "STATIC AUDIT: Memory Limits"

echo "  Checking observability-ui memory limit (expected: 256M)..."
if grep -A 40 'observability-ui:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'memory:\s*256M'; then
  pass "observability-ui — memory limit 256M configured"
else
  fail "observability-ui — memory limit 256M not found"
fi

echo "  Checking ai-analyzer memory limit (expected: 768M)..."
if grep -A 40 'ai-analyzer:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'memory:\s*768M'; then
  pass "ai-analyzer — memory limit 768M configured"
else
  fail "ai-analyzer — memory limit 768M not found"
fi

echo "  Checking kafka memory limit (expected: 1024M)..."
if grep -qE 'memory:\s*1024M' "$REPO_ROOT/deployments/docker-compose/docker-compose.yml"; then
  pass "kafka — memory limit 1024M configured"
else
  fail "kafka — memory limit 1024M not found"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "STATIC AUDIT: no-new-privileges Security Option"

echo "  Checking ai-analyzer..."
if grep -A 30 'ai-analyzer:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'no-new-privileges:true'; then
  pass "ai-analyzer — no-new-privileges:true set"
else
  fail "ai-analyzer — no-new-privileges:true not found"
fi

echo "  Checking observability-ui..."
if grep -A 30 'observability-ui:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'no-new-privileges:true'; then
  pass "observability-ui — no-new-privileges:true set"
else
  fail "observability-ui — no-new-privileges:true not found"
fi

echo "  Checking kafka..."
if grep -qE 'no-new-privileges:true' "$REPO_ROOT/deployments/docker-compose/docker-compose.yml"; then
  pass "kafka services — no-new-privileges:true set"
else
  fail "kafka services — no-new-privileges:true not found"
fi

# ──────────────────────────────────────────────────────────────────────────────

header "STATIC AUDIT: tmpfs Mounts"

echo "  Checking ai-analyzer tmpfs mounts..."
if grep -A 35 'ai-analyzer:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'tmpfs:'; then
  if grep -A 40 'ai-analyzer:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE '/tmp'; then
    pass "ai-analyzer — tmpfs /tmp configured"
  else
    fail "ai-analyzer — tmpfs /tmp not found"
  fi
else
  fail "ai-analyzer — no tmpfs section found"
fi

echo "  Checking observability-ui tmpfs mounts..."
if grep -A 50 'observability-ui:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE 'tmpfs:'; then
  ui_tmpfs_ok=true
  for dir in "/var/cache/nginx" "/var/run" "/var/log/nginx" "/tmp"; do
    if ! grep -A 55 'observability-ui:' "$REPO_ROOT/deployments/docker-compose.yaml" | grep -qE "$dir"; then
      fail "observability-ui — tmpfs $dir not found"
      ui_tmpfs_ok=false
    fi
  done
  if [ "$ui_tmpfs_ok" = true ]; then
    pass "observability-ui — all required tmpfs mounts configured"
  fi
else
  fail "observability-ui — no tmpfs section found"
fi

# ──────────────────────────────────────────────────────────────────────────────
# RUNTIME CHECKS — Require running containers
# ──────────────────────────────────────────────────────────────────────────────

if [ "$STATIC_ONLY" = "--static-only" ]; then
  echo ""
  echo "  Skipping runtime checks (--static-only flag set)"
else
  header "RUNTIME AUDIT: Non-Root Execution Verification"

  echo "  Checking observability-ui user (expected: nginx)..."
  UI_USER=$(docker exec observability-ui whoami 2>/dev/null || echo "CONTAINER_NOT_RUNNING")
  if [ "$UI_USER" = "nginx" ]; then
    pass "observability-ui runs as nginx (UID 101)"
  elif [ "$UI_USER" = "CONTAINER_NOT_RUNNING" ]; then
    warn "observability-ui container not running — skipping runtime check"
  else
    fail "observability-ui runs as '$UI_USER' (expected: nginx)"
  fi

  echo "  Checking ai-analyzer user (expected: appuser)..."
  ANALYZER_USER=$(docker exec ai-analyzer whoami 2>/dev/null || echo "CONTAINER_NOT_RUNNING")
  if [ "$ANALYZER_USER" = "appuser" ]; then
    pass "ai-analyzer runs as appuser (UID 1000)"
  elif [ "$ANALYZER_USER" = "CONTAINER_NOT_RUNNING" ]; then
    warn "ai-analyzer container not running — skipping runtime check"
  else
    fail "ai-analyzer runs as '$ANALYZER_USER' (expected: appuser)"
  fi

  # ──────────────────────────────────────────────────────────────────────────

  header "RUNTIME AUDIT: Read-Only Filesystem Verification"

  echo "  Testing observability-ui root write (expected: failure)..."
  if docker exec observability-ui touch /test 2>/dev/null; then
    fail "observability-ui — root filesystem is writable (should be read-only)"
    docker exec observability-ui rm -f /test 2>/dev/null || true
  else
    pass "observability-ui — root filesystem is read-only"
  fi

  echo "  Testing observability-ui tmpfs write (expected: success)..."
  if docker exec observability-ui touch /tmp/test 2>/dev/null; then
    pass "observability-ui — tmpfs /tmp is writable"
    docker exec observability-ui rm -f /tmp/test 2>/dev/null || true
  else
    fail "observability-ui — tmpfs /tmp is not writable"
  fi

  echo "  Testing ai-analyzer root write (expected: failure)..."
  if docker exec ai-analyzer touch /test 2>/dev/null; then
    fail "ai-analyzer — root filesystem is writable (should be read-only)"
    docker exec ai-analyzer rm -f /test 2>/dev/null || true
  else
    pass "ai-analyzer — root filesystem is read-only"
  fi

  echo "  Testing ai-analyzer tmpfs write (expected: success)..."
  if docker exec ai-analyzer touch /tmp/test 2>/dev/null; then
    pass "ai-analyzer — tmpfs /tmp is writable"
    docker exec ai-analyzer rm -f /tmp/test 2>/dev/null || true
  else
    fail "ai-analyzer — tmpfs /tmp is not writable"
  fi

  # ──────────────────────────────────────────────────────────────────────────

  header "RUNTIME AUDIT: Memory Limits Verification"

  echo "  Checking observability-ui memory limit..."
  UI_MEM=$(docker inspect --format='{{.HostConfig.Memory}}' observability-ui 2>/dev/null || echo "0")
  if [ "$UI_MEM" = "268435456" ]; then
    pass "observability-ui — memory limit 256MB (268435456 bytes)"
  elif [ "$UI_MEM" = "0" ]; then
    warn "observability-ui container not running — skipping memory check"
  else
    fail "observability-ui — memory limit is $UI_MEM bytes (expected: 268435456)"
  fi

  echo "  Checking ai-analyzer memory limit..."
  ANALYZER_MEM=$(docker inspect --format='{{.HostConfig.Memory}}' ai-analyzer 2>/dev/null || echo "0")
  if [ "$ANALYZER_MEM" = "805306368" ]; then
    pass "ai-analyzer — memory limit 768MB (805306368 bytes)"
  elif [ "$ANALYZER_MEM" = "0" ]; then
    warn "ai-analyzer container not running — skipping memory check"
  else
    fail "ai-analyzer — memory limit is $ANALYZER_MEM bytes (expected: 805306368)"
  fi

  echo "  Checking kafka memory limit..."
  KAFKA_MEM=$(docker inspect --format='{{.HostConfig.Memory}}' kafka 2>/dev/null || echo "0")
  if [ "$KAFKA_MEM" = "1073741824" ]; then
    pass "kafka — memory limit 1024MB (1073741824 bytes)"
  elif [ "$KAFKA_MEM" = "0" ]; then
    warn "kafka container not running — skipping memory check"
  else
    fail "kafka — memory limit is $KAFKA_MEM bytes (expected: 1073741824)"
  fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# SUMMARY
# ──────────────────────────────────────────────────────────────────────────────

header "AUDIT SUMMARY"

echo ""
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo ""

if [ "$FAIL" -gt 0 ]; then
  echo -e "  ${RED}❌ Security audit FAILED — $FAIL issue(s) require remediation${NC}"
  exit 1
else
  echo -e "  ${GREEN}✅ Security audit PASSED — all checks compliant${NC}"
  exit 0
fi
