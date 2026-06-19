# Security Policy

## Supported Versions

| Component | Version | Status |
|-----------|---------|--------|
| ai-analyzer (Spring Boot) | 3.5.14 | Actively maintained |
| k8s-collector (Spring Boot) | 3.5.14 | Actively maintained |
| mcp-server (Node.js) | 20.x LTS | Actively maintained |
| observability-ui (React/Vite) | Latest | Actively maintained |

## Supply Chain Security

All container base images are pinned via SHA-256 digest to prevent tag-mutation attacks:

| Image | Pinned Digest |
|-------|---------------|
| `eclipse-temurin:21-jdk-alpine` | `sha256:4fb80de7...` |
| `eclipse-temurin:21-jre-alpine` | `sha256:704db3c4...` |
| `node:20-alpine` | `sha256:dd75a9e8...` (mcp-server) / `sha256:fb4cd12c...` (ui) |
| `nginx:1.27-alpine` | `sha256:65645c7b...` |
| `confluentinc/cp-kafka:7.6.1` | `sha256:620734d9...` |

All GitHub Actions in CI are pinned to immutable commit SHAs (not mutable tags).

## Known Audit Advisories

### MCP Server (`services/mcp-server`)

- **20 advisories** (19 moderate, 1 high) — all in transitive `devDependencies` of Jest (`glob@7`, `inflight@1.0.6`).
- These packages are test-time only. They are never included in the production Docker image (which uses `npm ci --omit=dev`).
- Remediation: will resolve when Jest 30+ drops legacy glob dependency.

### Observability UI (`ui/`)

- **2 high advisories** — in transitive `devDependencies` of the Vite/ESBuild build toolchain.
- These never reach the production bundle (`dist/`). The runtime image serves only static HTML/CSS/JS via Nginx.
- Remediation: tracked upstream in Vite releases.

### Backend (Java/Gradle)

- **`@MockBean` deprecation warning** — Spring Boot 3.5 marks `org.springframework.boot.test.mock.mockito.MockBean` for removal. This is a test-only annotation with no production impact.
- Remediation: migrate to `@MockitoBean` when upgrading to Spring Boot 3.6+.

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please open a private security advisory via GitHub's "Report a vulnerability" feature on this repository. Do not open a public issue.

We will acknowledge receipt within 48 hours and aim to provide a fix or mitigation plan within 7 days.

## Security Design Principles

1. **Zero-Trust Containers**: All services run as non-root users with `no-new-privileges`, `read_only` filesystems, and memory limits.
2. **No Secrets in Code**: All credentials use environment variable placeholders (`${ENV_VAR}`). No API keys, tokens, or passwords are committed.
3. **Allowlist-Only Mutations**: The MCP Server write-back tools enforce a strict tool whitelist and simulated RBAC namespace authorization before any cluster mutation.
4. **Idempotent Operations**: All remediation mutations carry a `correlationId` for deduplication, preventing double-execution.
5. **Network Isolation**: Services communicate over a dedicated `platform-net` Docker bridge network. No service exposes ports beyond what is required.
6. **RFC 7807 Error Surfaces**: Internal stack traces and implementation details are never leaked to API consumers.
