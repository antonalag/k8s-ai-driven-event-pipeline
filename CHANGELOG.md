# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0-beta.1] - 2025-07-31

### Added

- **Ingestion Layer:** Kubernetes Informer-based Pod event collector (`k8s-collector`) streaming real-time cluster state changes via Spring Boot and the official K8s Java client.
- **Streaming Layer:** Apache Kafka backbone (KRaft mode) with `k8s-pod-events` and `ai-analysis-events` topics for fully decoupled event routing.
- **AI Analysis Engine:** Structured AI reasoning service (`ai-analyzer`) powered by Ollama with schema-enforced JSON output, SRE system prompt, and defensive markdown-fence stripping.
- **Storage Layer:** OpenSearch persistence for AI analysis reports with idempotent document indexing and historical querying.
- **Context History:** Intelligent correlation injecting previous analysis verdicts into the LLM prompt for cross-reference and regression detection.
- **Multi-Model Support (BYOK):** Dynamic AI provider routing between local Ollama and external OpenAI-compatible endpoints via `platform.ai.provider` configuration.
- **MCP Intelligence Layer:** Model Context Protocol server exposing read-only Kubernetes tools (`describe_pod`, `get_events`, `get_logs`) via JSON-RPC 2.0 for enriched root-cause analysis.
- **MCP Write-Back Tools:** Strictly-typed mutation operations (`restart_deployment`, `scale_deployment`, `fix_container_image`) with RBAC simulation and idempotency guarantees.
- **Automated Remediation:** One-click cluster repair from the Observability UI through `RemediationPort` orchestration with dedicated Mutation Circuit Breaker.
- **Analysis Lifecycle Management:** State machine (PENDING → DISMISSED/REMEDIATED) enabling operators to dismiss analysis cards with audit trail.
- **Audit Log:** Paginated historical query endpoint (`GET /api/v1/analyses/history`) for resolved analyses with `modelUsed` LLM tracking.
- **Observability UI:** React + TypeScript + TanStack Query dashboard with real-time polling, namespace/pod filtering, AI diagnosis cards, remediation buttons, and audit log tab.
- **Chaos Validation:** Three canonical failure injection scenarios (CrashLoopBackOff, OOMKilled, ImagePullBackOff) for E2E pipeline validation.
- **CI/CD Pipeline:** GitHub Actions workflow with SHA-pinned actions, Java 21 Temurin, Gradle cache, and Trivy filesystem vulnerability scanning.
- **RFC 7807 Error Surfaces:** Centralized `GlobalExceptionHandler` producing machine-readable Problem Detail responses for all REST error conditions.
- **Contract-First Design:** JSON Schema contracts (`k8s-event.v1.json`, `ai-analysis.v1.json`) and OpenAPI spec (`openapi-ai-analyzer.v1.yaml`) governing all data flows.

### Changed

- Reorganized all services into Clean Architecture hexagonal layers (domain → service → infrastructure) with strict domain purity enforcement via ArchUnit.
- Migrated from `@Component`-annotated adapters to `@Bean`-based provider routing with `@ConditionalOnProperty` mutual exclusivity.

### Security

- SHA-256 pinning for all container images and GitHub Actions (no mutable tags).
- Non-root container execution across all Dockerfiles.
- RBAC simulation layer for MCP write-back tool authorization.
- Trivy CRITICAL/HIGH severity gate in CI with `exit-code: 1` fail policy.
- Zero-CORS containerized operation via Nginx reverse-proxy.
- Secrets isolation via environment variable placeholders (never hardcoded).

[0.1.0-beta.1]: https://github.com/antonalag/k8s-ai-driven-event-pipeline/releases/tag/v0.1.0-beta.1
