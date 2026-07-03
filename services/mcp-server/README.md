# services/mcp-server

Model Context Protocol (MCP) intelligence server exposing curated Kubernetes cluster context and secure mutation tools via JSON-RPC 2.0 over HTTP.

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Node.js | >= 20.0.0 |
| TypeScript | 5.7.x |
| @modelcontextprotocol/sdk | 1.12.x |
| @kubernetes/client-node | 1.0.x |
| Zod | 3.24.x |
| Jest | 29.7.x |
| ts-jest | 29.2.x |

---

## Responsibilities

1. **Semantic Firewall** — Acts as the sole gateway between the AI reasoning layer and the Kubernetes API. No raw kubectl passthrough is permitted.
2. **Read-Only Context Tools** — Exposes curated cluster introspection via three read tools, providing structured context for LLM prompt enrichment.
3. **Write-Back Mutation Tools** — Exposes three strictly-typed, parameterized mutation operations for 1-click cluster remediation.
4. **Zod Schema Validation** — Every incoming tool invocation is validated against a Zod schema before execution. Malformed or out-of-bounds parameters are rejected with structured JSON-RPC errors.
5. **Idempotency Cache (LRU)** — Mutation operations carry a `correlationId`. Repeated submissions return cached results, preventing duplicate cluster state changes.
6. **RBAC Simulation** — Each mutation validates namespace access against an allowlist (`MCP_ALLOWED_NAMESPACES`) before execution.
7. **Dual-Mode Operation** — Runs in `mock` mode (synthetic responses, no cluster access) for local development and `live` mode (real kubectl operations) for cluster-connected deployments.

---

## Protocol: JSON-RPC 2.0

The server implements the [Model Context Protocol](https://modelcontextprotocol.io/) specification using JSON-RPC 2.0 over HTTP POST.

### Request Format

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": "1",
  "params": {
    "name": "describe_pod",
    "arguments": {
      "podName": "my-app-xyz",
      "namespace": "default"
    }
  }
}
```

### Error Codes

| Code | Meaning |
|------|---------|
| `-32600` | Invalid request (malformed JSON-RPC) |
| `-32601` | Method not found (unknown tool) |
| `-32602` | Invalid params (Zod validation failure) |
| `-32001` | Resource not found (K8s API 404) |
| `-32002` | RBAC denied (namespace not in allowlist) |

---

## Tools

### Read Tools (Context Enrichment)

| Tool | Parameters | Returns |
|------|-----------|---------|
| `describe_pod` | `podName: string`, `namespace: string` | Pod spec, status, conditions, container statuses |
| `get_events` | `podName: string`, `namespace: string` | Warning/Normal events associated with the pod |
| `get_logs` | `podName: string`, `namespace: string`, `containerName?: string`, `tailLines?: number` | Container stdout/stderr (last N lines) |

### Write Tools (Mutation)

| Tool | Parameters | Purpose | Bounds |
|------|-----------|---------|--------|
| `restart_deployment` | `deploymentName`, `namespace`, `correlationId` | Rolling restart via annotation patch | — |
| `scale_deployment` | `deploymentName`, `namespace`, `replicas`, `correlationId` | Scale replica count | `replicas: 1–10` |
| `fix_container_image` | `deploymentName`, `namespace`, `containerName`, `correctImage`, `correlationId` | Patch container image spec | — |

All write tools enforce:
- **Zod schema validation** with strict type checking and bounded numeric ranges
- **Namespace allowlist** check (`MCP_ALLOWED_NAMESPACES`)
- **Idempotency deduplication** via `correlationId` (LRU cache with configurable TTL)

---

## Source Structure

```
src/
├── index.ts              # HTTP server entry point + health endpoint
├── rpc-handler.ts        # JSON-RPC 2.0 dispatcher + method routing
├── types.ts              # Shared TypeScript type definitions
├── middleware/           # RBAC gate, idempotency cache, request logging
├── tools/
│   ├── describe-pod.ts   # Read: pod description
│   ├── get-events.ts     # Read: pod events
│   ├── get-logs.ts       # Read: container logs
│   ├── restart-deployment.ts  # Write: rolling restart
│   ├── scale-deployment.ts    # Write: replica scaling
│   └── fix-container-image.ts # Write: image patch
└── __tests__/            # Jest test suites
```

---

## Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `MCP_MODE` | `live` | Operation mode: `live` (real K8s API, requires kubeconfig) or `mock` (synthetic responses) |
| `MCP_PORT` | `3001` | HTTP listen port |
| `MCP_ALLOWED_NAMESPACES` | `default,chaos-validation` | Comma-separated namespace allowlist for mutations |
| `MCP_IDEMPOTENCY_TTL_SECONDS` | `300` | Idempotency cache entry TTL |

### Kubernetes Access (Live Mode)

In Docker Compose, the MCP Server mounts `~/.kube/config` read-only. A `docker-entrypoint.sh` script automatically:
1. Rewrites server URLs (`0.0.0.0`/`127.0.0.1`/`localhost` → `host.docker.internal`)
2. Removes `certificate-authority-data` (incompatible SAN)
3. Adds `insecure-skip-tls-verify: true`

This allows seamless connectivity to local k3d/kind/minikube clusters from inside Docker.

---

## Build & Test

```bash
# Install dependencies
npm ci

# Compile TypeScript
npm run build

# Run tests
npm test

# Type-check without emit
npm run lint

# Start server
npm start
```

---

## Container

```dockerfile
# Multi-stage: Node 20 build → Node 20 Alpine runtime
# Non-root (user 1000:1000), read-only filesystem, no-new-privileges
docker build -t mcp-server .
```

Exposed port: **3001**
Health endpoint: `GET /health`

---

## Idempotency Cache

The LRU cache stores mutation results keyed by `correlationId`:

- **Capacity:** Bounded (prevents unbounded memory growth)
- **TTL:** Configurable via `MCP_IDEMPOTENCY_TTL_SECONDS` (default 5 minutes)
- **Behavior:** If a `correlationId` is found in cache, the cached result is returned immediately without re-executing the mutation
- **Scope:** Per-process (cache resets on container restart)

This guarantees that network retries, UI double-clicks, or backend retry logic cannot produce duplicate cluster state changes.
