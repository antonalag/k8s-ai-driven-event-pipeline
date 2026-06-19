#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# Pre-flight Check & Bootstrap Script
# k8s-ai-driven-event-pipeline
#
# Usage:
#   ./scripts/bootstrap.sh          — Full pre-flight + docker compose up
#   ./scripts/bootstrap.sh --check  — Pre-flight only (no compose start)
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"
ENV_EXAMPLE="$PROJECT_ROOT/.env.example"
COMPOSE_FILE="$PROJECT_ROOT/deployments/docker-compose.yaml"

# ──────────────────────────────────────────────────────────────────────────────
# Colors & Helpers
# ──────────────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

pass()  { echo -e "  ${GREEN}✔${NC} $1"; }
fail()  { echo -e "  ${RED}✖${NC} $1"; }
warn()  { echo -e "  ${YELLOW}⚠${NC} $1"; }
info()  { echo -e "  ${CYAN}ℹ${NC} $1"; }
header() {
  echo ""
  echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${CYAN}  $1${NC}"
  echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

abort() {
  echo ""
  fail "$1"
  echo ""
  exit 1
}

ERRORS=0

# ──────────────────────────────────────────────────────────────────────────────
# 1. Environment File Management
# ──────────────────────────────────────────────────────────────────────────────
header "1/4  Environment Configuration"

if [ ! -f "$ENV_EXAMPLE" ]; then
  abort ".env.example not found at project root. Repository may be corrupted."
fi

if [ ! -f "$ENV_FILE" ]; then
  info "No .env file found. Creating from .env.example..."
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  warn ".env created with default values. Review and update secrets before production use."
else
  pass ".env file exists"
fi

# Load environment variables
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# ──────────────────────────────────────────────────────────────────────────────
# 2. Dependency Checks
# ──────────────────────────────────────────────────────────────────────────────
header "2/4  CLI Dependencies"

check_command() {
  local cmd="$1"
  local min_version="${2:-}"
  local install_hint="${3:-}"

  if command -v "$cmd" &>/dev/null; then
    local version
    version=$("$cmd" --version 2>/dev/null | head -1 || echo "unknown")
    pass "$cmd — $version"
    return 0
  else
    fail "$cmd is not installed or not in PATH."
    if [ -n "$install_hint" ]; then
      info "  Install: $install_hint"
    fi
    ERRORS=$((ERRORS + 1))
    return 1
  fi
}

check_command "docker" "" "https://docs.docker.com/get-docker/"
check_command "kubectl" "" "https://kubernetes.io/docs/tasks/tools/"

# Docker Compose (plugin mode)
if docker compose version &>/dev/null; then
  compose_version=$(docker compose version --short 2>/dev/null || echo "unknown")
  pass "docker compose — v$compose_version"
else
  fail "docker compose plugin is not available."
  info "  Install: https://docs.docker.com/compose/install/"
  ERRORS=$((ERRORS + 1))
fi

# Kubernetes cluster (k3d / kind / minikube)
K8S_RUNTIME=""
if command -v k3d &>/dev/null; then
  K8S_RUNTIME="k3d"
  pass "k3d — $(k3d version 2>/dev/null | head -1 || echo 'installed')"
elif command -v kind &>/dev/null; then
  K8S_RUNTIME="kind"
  pass "kind — $(kind version 2>/dev/null || echo 'installed')"
elif command -v minikube &>/dev/null; then
  K8S_RUNTIME="minikube"
  pass "minikube — $(minikube version --short 2>/dev/null || echo 'installed')"
else
  warn "No local K8s runtime found (k3d/kind/minikube). Required for full pipeline."
  info "  Recommended: https://k3d.io/"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 3. AI Provider Validation
# ──────────────────────────────────────────────────────────────────────────────
header "3/4  AI Provider Validation"

PROVIDER="${PLATFORM_AI_PROVIDER:-ollama}"
info "Configured provider: $PROVIDER"

if [ "$PROVIDER" = "ollama" ]; then
  OLLAMA_URL="${OLLAMA_API_URL:-http://localhost:11434}"
  MODEL="${OLLAMA_MODEL:-llama3.1}"

  # Check Ollama is running
  if curl -sf --max-time 3 "$OLLAMA_URL/api/tags" >/dev/null 2>&1; then
    pass "Ollama is reachable at $OLLAMA_URL"

    # Check if model is downloaded
    if curl -sf --max-time 5 "$OLLAMA_URL/api/tags" | grep -q "\"$MODEL\""; then
      pass "Model '$MODEL' is available"
    else
      warn "Model '$MODEL' not found locally. Pulling..."
      info "  Running: ollama pull $MODEL (this may take several minutes)"
      if ollama pull "$MODEL"; then
        pass "Model '$MODEL' downloaded successfully"
      else
        fail "Failed to pull model '$MODEL'. Check Ollama logs."
        ERRORS=$((ERRORS + 1))
      fi
    fi
  else
    abort "Ollama is not running on $OLLAMA_URL. Please start it or switch to BYOK mode (PLATFORM_AI_PROVIDER=byok in .env)."
  fi

elif [ "$PROVIDER" = "byok" ]; then
  ENDPOINT="${BYOK_ENDPOINT:-}"
  API_KEY="${BYOK_API_KEY:-}"
  PROVIDER_TYPE="${BYOK_PROVIDER_TYPE:-OPENAI_COMPATIBLE}"

  if [ -z "$ENDPOINT" ]; then
    fail "BYOK_ENDPOINT is not set in .env"
    ERRORS=$((ERRORS + 1))
  fi

  if [ -z "$API_KEY" ]; then
    fail "BYOK_API_KEY is not set in .env"
    ERRORS=$((ERRORS + 1))
  fi

  if [ -n "$ENDPOINT" ] && [ -n "$API_KEY" ]; then
    # Determine validation endpoint based on provider type
    if [ "$PROVIDER_TYPE" = "OPENAI_COMPATIBLE" ]; then
      PING_URL="$ENDPOINT/v1/models"
    else
      PING_URL="$ENDPOINT/api/tags"
    fi

    HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 3 \
      -H "Authorization: Bearer $API_KEY" \
      "$PING_URL" 2>/dev/null || echo "000")

    if [ "$HTTP_CODE" = "200" ]; then
      pass "BYOK endpoint reachable and API key valid ($ENDPOINT)"
    elif [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
      abort "The API Key provided for BYOK ($ENDPOINT) is invalid or expired. HTTP $HTTP_CODE."
    elif [ "$HTTP_CODE" = "000" ]; then
      fail "Cannot reach BYOK endpoint at $ENDPOINT (timeout or DNS failure)."
      ERRORS=$((ERRORS + 1))
    else
      warn "BYOK endpoint returned HTTP $HTTP_CODE. May still work for inference."
    fi
  fi
else
  fail "Unknown AI provider: '$PROVIDER'. Must be 'ollama' or 'byok'."
  ERRORS=$((ERRORS + 1))
fi

# ──────────────────────────────────────────────────────────────────────────────
# 4. Final Gate
# ──────────────────────────────────────────────────────────────────────────────
header "4/4  Pre-flight Summary"

if [ "$ERRORS" -gt 0 ]; then
  abort "Pre-flight failed with $ERRORS error(s). Fix the issues above and retry."
fi

pass "All pre-flight checks passed."

# ──────────────────────────────────────────────────────────────────────────────
# Launch (skip if --check mode)
# ──────────────────────────────────────────────────────────────────────────────
if [ "${1:-}" = "--check" ]; then
  echo ""
  info "Check-only mode. Skipping docker compose launch."
  exit 0
fi

echo ""
info "Starting platform services..."
echo ""

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up --build -d

echo ""
pass "Platform launched successfully."
info "  UI:          http://localhost:3000"
info "  AI Analyzer: http://localhost:8082"
info "  MCP Server:  http://localhost:3001/health"
info "  Kafka:       localhost:9092"
echo ""
info "Stop with: docker compose -f $COMPOSE_FILE down -v"
echo ""
