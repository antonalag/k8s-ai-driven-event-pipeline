# scripts/

Automation scripts for platform bootstrapping, validation, and smoke testing.

---

## Directory Contents

| Script | Purpose |
|--------|---------|
| `bootstrap.sh` | Pre-flight validation + platform launch |
| `smoke-test.sh` | Platform health verification |
| `remediation-smoke-test.sh` | E2E remediation API contract validation |
| `mcp-smoke-test.sh` | MCP Server JSON-RPC endpoint verification |
| `security-audit.sh` | Container image vulnerability scanning |
| `audit-output.txt` | Latest security audit results |

---

## bootstrap.sh

The primary entry point for reproducible platform setup. Invoked via `make init` or `make check`.

### Usage

```bash
# Full pre-flight + build + docker compose up
bash scripts/bootstrap.sh

# Pre-flight validation only (no launch)
bash scripts/bootstrap.sh --check
```

### Pre-flight Check Stages

| Stage | Validation |
|-------|-----------|
| **1/4 — Environment Configuration** | Ensures `.env` exists (copies from `.env.example` if missing), loads environment variables |
| **2/4 — CLI Dependencies** | Verifies `docker`, `docker compose` (plugin), `kubectl`, and local K8s runtime (k3d/kind/minikube) |
| **3/4 — AI Provider Validation** | For `ollama`: checks reachability + model availability (auto-pulls if missing). For `byok`: validates endpoint connectivity + API key authentication |
| **4/4 — Pre-flight Summary** | Gates on accumulated errors. Aborts with actionable messages if any check fails |

### Exit Behavior

- **Exit 0:** All checks passed. If not in `--check` mode, launches `docker compose up --build -d`.
- **Exit 1:** One or more checks failed. Prints error count and specific remediation hints.

### Post-Launch Output

On success, prints service endpoints:
- UI: `http://localhost:3000`
- AI Analyzer: `http://localhost:8082`
- MCP Server: `http://localhost:3001/health`
- Kafka: `localhost:9092`

---

## remediation-smoke-test.sh

Validates the `POST /api/v1/remediations` contract end-to-end against a running `ai-analyzer` instance (with `MCP_MODE=mock`).

### Usage

```bash
# Default target: http://localhost:8082
bash scripts/remediation-smoke-test.sh

# Custom base URL
bash scripts/remediation-smoke-test.sh http://localhost:8082
```

### Test Cases

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Valid `restart_deployment` | HTTP 200, action completed |
| 2 | Idempotency (same `correlationId`) | HTTP 200, cached result |
| 3 | Valid `scale_deployment` (replicas=3) | HTTP 200 |
| 4 | Valid `fix_container_image` | HTTP 200 |
| 5 | Missing `action` field | HTTP 400, validation error |
| 6 | Invalid action name (`delete_pod`) | HTTP 400 |
| 7 | Replicas out of bounds (15) | HTTP 400 |
| 8 | `X-Correlation-Id` response header | Header present |

### Exit Codes

- **0:** All assertions passed.
- **1:** One or more failures detected.

---

## mcp-smoke-test.sh

Validates MCP Server tool invocations via raw JSON-RPC 2.0 HTTP requests.

### Usage

```bash
bash scripts/mcp-smoke-test.sh
```

---

## security-audit.sh

Runs container image scanning using Trivy or equivalent tooling. Results are persisted to `audit-output.txt` for CI artifact collection.

### Usage

```bash
bash scripts/security-audit.sh
```
