# System Constitution: Kubernetes-Native AI Agent (v1.0)

## 1. System Mission
Transform raw Kubernetes signals (events, Pod status changes) into structured, actionable knowledge through a contract-driven distributed pipeline and an AI reasoning layer.

## 2. Platform Layers (Macro Vision)
The system will be built incrementally across isolated, contract-backed layers:
1. **Ingestion Layer (Phase 1):** Kubernetes Collector using local Informers written in Java/Spring Boot.
2. **Streaming Layer (Phase 2):** Apache Kafka backbone for event routing and decoupling.
3. **Intelligence Layer (Phase 3):** Structured AI Analyzer powered by Ollama.
4. **Storage Layer (Phase 4):** OpenSearch persistence for AI analysis reports, enabling historical querying and observability dashboards.
5. **Cross-Cutting & Resilience (Phases 5-10):** Clean Architecture, Context History, BYOK, Circuit Breakers, CI/CD pipelines, and RFC 7807 error surfaces.
6. **Observability Interface Layer (Phase 11 & 12):** Real-time client dashboard shell built with React, TypeScript, and Tailwind CSS, fully covered by TDD unit and property-based testing (fast-check).
7. **Live Data Integration (Phase 13):**: Dynamic frontend-backend synchronization utilizing TanStack Query for real-time polling, DTO contract mapping, and defensive RFC 7807 error handling. 
8. **Frontend Containerization & Global Orchestration (Phase 14):** Unified Docker Compose orchestration integrating all platform services (Kafka, ai-analyzer, observability-ui) with shared `platform-net` network, zero-CORS Nginx reverse-proxy, SHA-256 supply-chain pinning, and defense-in-depth container hardening.
9. **Advanced Intelligence via MCP (Phase 15):** Model Context Protocol intelligence layer introducing a dedicated MCP Server container as a semantic firewall for read-only Kubernetes cluster context, a native MCP Client in the ai-analyzer with circuit breaker resilience, and enriched three-section prompt construction combining failure events, historical analysis, and live cluster state.
10. **End-to-End Validation & Diagnostic Calibration (Phase 16):** Controlled chaos failure injection under three canonical Kubernetes failure typologies, E2E pipeline trace validation with MCP enrichment, and LLM prompt calibration for actionable RFC 7807 diagnostics rendered in the Observability UI.
11. **1-Click Automated Remediation & Write-Back Tools (Phase 17):** Closing the semantic loop with secure, type-safe mutation operations on the Kubernetes cluster through MCP Server write-back tools, backend `RemediationPort` orchestration with dedicated Mutation Circuit Breaker, and Observability UI `ExecuteActionButton` integration for one-click automated cluster repair.

## 3. Non-Negotiable SDD Rules
1. **Contract-First:** NO Java code shall be written without a prior data schema (JSON Schema / AsyncAPI) acting as a strict contract.
2. **Structured AI:** The AI agent is strictly forbidden from returning free-text responses; its outputs must be validated against a predefined schema.

## 4. Roadmap Status

### ✅ Phase 1 — Ingestion Layer (Completed)
- [x] **Milestone 1:** Design the event contract schema at `specs/schemas/k8s-event.v1.json`.
- [x] **Milestone 2:** Initialize the `services/k8s-collector` microservice using Java 21 & Spring Boot 3.x.
- [x] **Milestone 3:** Implement the Kubernetes Informer to watch and stream Pod state updates.

### ✅ Phase 2 — Streaming Layer (Completed)
- [x] **Milestone 4:** Setup local Kafka infrastructure using Docker Compose (KRaft mode, single broker, topic `k8s-pod-events` with 3 partitions) at `deployments/docker-compose/`.
- [x] **Milestone 5:** Integrate a Kafka Producer in `services/k8s-collector` to publish `KubernetesEvent` records to the `k8s-pod-events` topic.

### ✅ Phase 3 — Intelligence Layer (Completed)
- [x] **Milestone 6:** Design the AI analysis contract schema at `specs/schemas/ai-analysis.v1.json`.
- [x] **Milestone 7 (Completed):** Initialize the `services/ai-analyzer` microservice (Spring Boot 3.5.x, Java 21, Gradle multi-project module). Implemented reactive Kafka consumer backbone (`PodEventConsumer`) with selective routing (Failed/Pending/Unknown only), Ollama `RestClient` integration (`OllamaAnalyzerService`), structured SRE system prompt with embedded JSON Schema contract, and defensive markdown-fence stripping for reliable `AiAnalysis` parsing.
- [x] **Milestone 8 (Completed):** Evolved `services/ai-analyzer` into a hybrid Consumer+Producer service. After a successful Ollama diagnosis, the structured `AiAnalysis` result is published to the `ai-analysis-events` Kafka topic via a `KafkaTemplate`-backed `AiAnalysisProducer` (async send with per-pod partition key). The intelligence pipeline loop is closed.

### ✅ Phase 4 — Storage Layer (Completed)
**Architecture:** The `services/ai-analyzer` service is extended to also act as a consumer of the `ai-analysis-events` topic. Each consumed `AiAnalysisEvent` is persisted as a document in the `ai-analysis-reports` OpenSearch index. This gives the platform a durable, queryable store of all AI verdicts, enabling historical analysis, SRE dashboards, and audit trails.

**Storage contract:**
- **Index:** `ai-analysis-reports`
- **Document ID:** `{podName}-{analyzedAt}` (ensures idempotent re-indexing)
- **Fields:** all fields from `specs/schemas/ai-analysis.v1.json` plus pipeline metadata (`analyzedAt`, `sourceEventTimestamp`)
- **Client:** `spring-data-opensearch-starter` (Spring Data compatible, OpenSearch 2.x)

- [x] **Milestone 9 (Completed):** Implement `AiAnalysisDocument` entity, `AiAnalysisRepository` (Spring Data OpenSearch), and `AiAnalysisStorageConsumer` Kafka listener that persists every `ai-analysis-events` message to the `ai-analysis-reports` index.

### ✅ Phase 5 — Cross-Cutting — Code Quality & Architecture Refactor (Completed)
This milestone is transversal and applies to all existing services. It enforces Clean Architecture, DDD layering, TDD coverage, and spec hygiene across the entire codebase.

- [x] **Milestone 10a:** Update `architecture.md` with definitive Clean Architecture rules: pure domain layer, infrastructure confinement, TDD mandate, no dead specs.
- [x] **Milestone 10b (In Progress):** Reorganize `services/ai-analyzer` packages into `domain/`, `service/`, `infrastructure/`, `config/` following the new architecture rules. Move framework-annotated classes (`AiAnalysisDocument`) out of `domain/` into `infrastructure/`. Remove dead spec files (`specs/asyncapi-kafka.yaml`, `specs/openapi-ai-agent.yaml`).
- [x] **Milestone 10c:** Add unit tests for `OllamaAnalyzerService` covering: prompt construction, defensive markdown stripping, clean JSON parsing, and exception handling on malformed responses. All tests must pass green via `./gradlew clean build`.
- [x] **Milestone 10d:** Add robust unit tests for `OllamaLanguageModelAdapter` covering: history prompt construction, defensive markdown stripping, malformed JSON parsing, and exception handling. All tests must pass green via `./gradlew clean build`.

### ✅ Phase 6 — Intelligent Correlation & Context History (Completed)
**Architecture:** Enhance the `services/ai-analyzer` reasoning layer to provide the AI model with operational memory. Before sending a `KubernetesEvent` to the AI provider, the system will query OpenSearch via the `AiAnalysisRepositoryPort` to retrieve previous analysis verdicts for the same Pod. This history will be injected into a new system prompt layout, allowing the LLM to cross-reference past failures, detect cascading regressions, and avoid redundant diagnostic steps (BYOK-ready contextual enrichment).

- [x] **Milestone 11:** Update `system.spec.md` and design the prompt template configuration for historical context injection.
- [x] **Milestone 12:** Adapt `AiLanguageModelPort` and `OllamaAnalyzerService` to orchestrate history retrieval and inject past verdicts into the reasoning pipeline.
- [x] **Milestone 13:** Update the Ollama/BYOK infrastructure adapter to parse the history list and format it cleanly inside the LLM prompt without regression.

### ✅ Phase 7 — Multi-Model Support & BYOK Strategy (Completed)
**Architecture:** Decouple the intelligence layer from Ollama-specific configurations to allow seamless switching between local LLMs and cloud-based providers (OpenAI, Anthropic, or custom corporate endpoints) via API Keys (Bring Your Own Key). This introduces an agnostic AI routing configuration and a standard payload mapper.

- [x] **Milestone 14:** Define a unified `@ConfigurationProperties` class in `config/` to register and validate all platform properties, eliminating unknown property warnings.
- [x] **Milestone 15:** Design the infrastructure skeleton for the generic HTTP BYOK Adapter (`ByokLanguageModelAdapter`) activated via `platform.ai.provider=byok`.
- [x] **Milestone 16:** Implement the real HTTP client, Java 21 DTO records, payload mappers (`ByokPayloadMapper`, `ByokResponseExtractor`), and full `ByokLanguageModelAdapter` orchestration for external AI providers (OpenAI-compatible and custom endpoints) with defensive parsing and TDD coverage.
- [x] **Milestone 17:** Dynamic AI Provider Routing — Orchestrate runtime selection between Ollama and BYOK adapters via `@ConditionalOnProperty(name = "platform.ai.provider")` on each `@Configuration` class. Removed `@Component` from all adapters (now POJOs instantiated by `@Bean` methods). Added `AiProviderValidator` with `@PostConstruct` fail-fast validation. Full PBT suite (jqwik) covering 7 correctness properties, ArchUnit tests enforcing domain purity, and integration tests validating mutual exclusivity of provider beans.

### ✅ Phase 8 — Pipeline Resilience & Circuit Breaker (Completed)
**Architecture:** Protect the event consumption pipeline so that when the AI provider (Ollama or BYOK) fails due to network errors, timeouts, or rate limits (HTTP 429), the pipeline continues processing events. A Circuit Breaker + Fallback decorator wraps `AiLanguageModelPort` in the configuration layer, generating degraded `AiAnalysis` responses (verdict "DEGRADED") when the provider is unavailable. Domain purity is preserved — the service layer remains unaware of the resilience mechanism.

- [x] **Milestone 18 (Completed):** Implement Circuit Breaker with Fallback — `ResilientAiLanguageModelAdapter` decorator wrapping `AiLanguageModelPort` via Resilience4j, `CircuitBreakerProperties` externalized configuration, `ResilienceConfig` with `@Primary` decorated bean, state transition logging, and full PBT suite (jqwik) covering 9 correctness properties. 83 tests passing.

### ✅ Phase 9 — CI/CD Pipeline Setup (Completed)
**Architecture:** Automate build verification and security scanning on every push/PR via GitHub Actions. The pipeline enforces compilation, full test suite execution, and filesystem vulnerability scanning using Trivy. All workflow actions are SHA-pinned (immutable references) following supply-chain security best practices.

- [x] **Milestone 19 (Completed):** Configure GitHub Actions CI workflow at `.github/workflows/ci.yml` — SHA-pinned actions (checkout `v4.1.1`, setup-java `v4.2.1`, trivy-action `v0.18.0`), explicit minimal permissions (`contents: read`), Java 21 Temurin with Gradle cache, `./gradlew test --no-daemon`, and Trivy fs scan with `CRITICAL,HIGH` severity gate + `exit-code: 1` fail policy.

### ✅ Phase 10 — API Contracts & RFC 7807 Implementation (Completed)
**Architecture:** Standardise all REST error responses from the `ai-analyzer` service surface using RFC 7807 Problem Details. A centralised `GlobalExceptionHandler` maps domain, validation, and infrastructure exceptions to machine-readable `ProblemDetail` payloads. Stack traces and internal details are never leaked to the client.

- [x] **Milestone 20 (Completed):** Implement `GlobalExceptionHandler` at `infrastructure/web/` extending `ResponseEntityExceptionHandler`. Handles `MethodArgumentNotValidException` (400 with field-level errors), `CallNotPermittedException` (503 Circuit Breaker open), and `AiAnalysisException` (502 upstream failure). Enabled `spring.mvc.problemdetails.enabled=true`. 83 tests passing.
- [x] **Milestone 21 (Completed):** Implement REST Query Endpoint `GET /api/v1/analyses` — `AiAnalysisQueryController` with optional `namespace`/`podName` filtering, CQRS-lite read port (`AiAnalysisQueryPort`), `AiAnalysisView` read model, `OpenSearchAnalysisQueryAdapter`, `AiAnalysisResponse` DTO, and OpenAPI contract at `specs/openapi-ai-analyzer.v1.yaml`. 83 tests passing, no regression.

### ✅ Phase 11 — Observability Interface Layer (Completed)
**Architecture:** Initialize a completely decoupled, client-side single-page application (SPA) under the `ui/` directory using React, TypeScript, and Vite. The application will leverage Tailwind CSS and Shadcn/ui primitives for styling, and TanStack Query (React Query) to handle server-state fetching and polling from the `services/ai-analyzer` REST surface (`GET /api/v1/analyses`). The UI will be containerized using a minimal multi-stage Dockerfile based on Nginx to serve the static assets.

- [x] **Milestone 22:** Initialize the `ui/` module scaffolding with Vite + React + TypeScript, configuring strict compiler options (`noImplicitAny: true`) and package automation scripts matching the CI workflow pipeline (`npm run typecheck`, `npm run lint`, `npm run build`).
- [x] **Milestone 23:** Configure code style standards and quality gates locally, setting up ESLint rules and Tailwind CSS utility class isolation to prevent styling pollution.
- [x] **Milestone 24:** Create the containerization blueprint at `ui/Dockerfile` utilizing a secure multi-stage build layout (compilation step via Node 20 + execution step via minimal non-root Nginx alpine image with custom single-page routing properties).
- [x] **Milestone 25 (Completed):** Observability Dashboard Visual Integration — Implemented the full observability dashboard UI with modular component architecture (`AnalysisCard`, `StatusBadge`, `FilterBar`, `AnalysisList`, `EmptyState`), TanStack Query data-fetching layer with polling, Tailwind CSS + Shadcn/ui design system, responsive grid layout, namespace/pod filtering, and a pragmatic "Copy Commands" button providing one-click kubectl remediation snippets. All components follow strict TypeScript contracts and the decoupled presentation pattern.

### ✅ Phase 12 — Frontend Quality & Property-Based Testing (Completed)
**Architecture:** Established a rigorous testing infrastructure for the `ui/` module using Vitest as the test runner, fast-check for property-based testing (PBT), and Testing Library for component assertions. Quality gates enforce type-safety, lint compliance, and successful production builds in CI.

- [x] **Milestone 26 (Completed):** TDD & PBT Quality Gates — Configured Vitest + fast-check + Testing Library test harness, defined 4 correctness properties (navigation exclusivity, log entry structural completeness, severity-driven visual treatment, ProblemDetail field rendering), implemented property-based and example-based test suites covering all dashboard components (Sidebar, TopBar, LogViewer, AIDiagnosisPanel, App Shell), and validated all quality gates (`npm run typecheck`, `npm run lint`, `npm run build`). 73 tests passing.

### ✅ Phase 13 — Frontend-Backend Live Data Integration (Completed)
- [x] **Milestone 27:** Design the connection architecture between the `ui/` module and the `services/ai-analyzer` REST endpoint (`GET /api/v1/analyses`).
- [x] **Milestone 28:** Implement TanStack Query polling integration to fetch live analysis data at a 5-second interval.
- [x] **Milestone 29:** Map the backend `AiAnalysisResponse` DTO contract to frontend TypeScript display models.

### ✅ Phase 14 — Frontend Containerization & Global Orchestration (Completed)
**Architecture:** Validate and harden the existing containerized React SPA, create a unified Docker Compose orchestration file integrating all platform services (Kafka, ai-analyzer, observability-ui) with shared networking, and establish zero-CORS containerized operation via Nginx reverse-proxy with secure cross-origin fallback for local development.

- [x] **Milestone 30:** Validate and harden the `ui/Dockerfile` multi-stage build pipeline (Node 20 Alpine → Nginx 1.27 Alpine) with SHA-256 pinning, non-root execution, and SPA routing configuration.
- [x] **Milestone 31:** Create unified `deployments/docker-compose.yaml` orchestrating all platform services (Kafka, ai-analyzer, observability-ui) with shared `platform-net` network, healthcheck chains, and security hardening.
- [x] **Milestone 32:** Implement CORS fallback configuration and Nginx reverse-proxy for `/api/` path, ensuring zero-CORS containerized operation and secure cross-origin fallback for local development.

### ✅ Phase 15 — Advanced Intelligence via Model Context Protocol (MCP)
**Architecture:** Introduce an MCP Server as a semantic firewall that exposes curated, read-only Kubernetes cluster context (pod descriptions, events, container logs) via JSON-RPC 2.0 over HTTP. The existing `ai-analyzer` service is extended with a native MCP Client adapter that interrogates the MCP Server for enriched context before constructing the final LLM prompt, combining three data sources — the original Kafka failure event, historical analysis from OpenSearch, and live cluster state from MCP — into a single reasoning payload for significantly deeper root-cause analysis.

- [x] **Milestone 33:** MCP Server container and tool implementation — Node.js TypeScript MCP Server exposing read-only tools (describe_pod, get_events, get_logs) via JSON-RPC 2.0, with tool whitelisting, mock mode for local development, SHA-256 pinned container, and security hardening.
- [x] **Milestone 34:** Spring Boot MCP Client integration with circuit breaker — McpContextPort domain port, McpClientAdapter infrastructure adapter using RestClient for JSON-RPC communication, dedicated mcpCircuitBreaker (Resilience4j) with degraded mode fallback.
- [x] **Milestone 35:** Enriched diagnostic flow and prompt construction — Extended OllamaAnalyzerService orchestration, three-section prompt (event + history + MCP context) with priority-based truncation, additive AiAnalysis fields (mcpToolsUsed, mcpContextAvailable), backward-compatible Kafka serialization.

### ✅ Phase 16 — End-to-End Validation & Diagnostic Calibration (Completed)
**Architecture:** Controlled chaos failure injection validating the complete integrated pipeline under three canonical Kubernetes failure typologies (CrashLoopBackOff, OOMKilled, ImagePullBackOff), E2E verification of the MCP enrichment flow (describe_pod → get_events → get_logs) with circuit breaker resilience, and LLM prompt fine-tuning for actionable RFC 7807 diagnostics with concrete kubectl commands and configuration changes.

- [x] **Milestone 36 (Completed):** Chaos scenario definition — Three reproducible failure injection manifests (CrashLoopBackOff, OOMKilled, ImagePullBackOff) deployed in an isolated chaos-validation namespace with SHA-256 pinned images, idempotent lifecycle, and metadata labels for filtering.
- [x] **Milestone 37 (Completed):** E2E pipeline execution and trace validation — End-to-end pipeline verification from Kubernetes event ingestion through Kafka streaming, MCP context enrichment (describe_pod → get_events → get_logs), AI analysis, and OpenSearch persistence with mcpToolsUsed metadata, including structured JSON observability logging with correlation IDs.
- [x] **Milestone 38 (Completed):** Final prompt calibration RFC 7807 — LLM prompt structure iteration ensuring diagnostic responses produce 1–5 concrete, actionable recommendedActions (kubectl commands or configuration changes) per failure verdict, with priority-based context truncation and failure-typology-specific instructions.

### ✅ Phase 17 — 1-Click Automated Remediation & Write-Back Tools (Completed)
**Architecture:** Close the semantic loop by enabling direct remediation from the Observability UI. The user can trigger curated, type-safe mutation operations on the Kubernetes cluster through a secure write-back extension of the MCP Server. The backend orchestrates remediation requests via a new `RemediationPort` domain abstraction protected by a dedicated Mutation Circuit Breaker, ensuring fault isolation between read-path diagnostics and write-path mutations. Every remediation action is correlated back to the original failure via `correlationId`, creating an auditable cause→action→outcome chain persisted in OpenSearch.

**Security Model:**
- **No free-form command execution.** The MCP Server exposes strictly typed, parameterized tools validated via Zod schemas.
- **Allowlist-only mutations.** Only three initial operations are permitted: `restart_deployment`, `scale_deployment`, `fix_container_image`.
- **RBAC simulation layer.** Each tool invocation passes through a simulated RBAC gate that validates namespace/resource permissions before execution, preparing the path for real RBAC integration.
- **Idempotency guarantee.** Each mutation carries a `correlationId` used for deduplication; repeated submissions of the same action return the cached result.

**Write-Back Tools (MCP Server Extension):**
| Tool Name | Parameters | Purpose |
|-----------|-----------|---------|
| `restart_deployment` | `deploymentName: string`, `namespace: string` | Rolling restart via annotation patch (kubectl rollout restart equivalent) |
| `scale_deployment` | `deploymentName: string`, `namespace: string`, `replicas: number (1-10)` | Scale deployment replicas within safe bounds |
| `fix_container_image` | `deploymentName: string`, `namespace: string`, `containerName: string`, `correctImage: string` | Patch container image spec to resolve ImagePullBackOff |

- [x] **Milestone 39 (Completed):** MCP Server Write-Back Tools & RBAC Simulation — Extend the MCP Server with three strictly-typed mutation tools (`restart_deployment`, `scale_deployment`, `fix_container_image`) validated via Zod schemas with bounded parameters. Implement a simulated RBAC authorization layer that validates namespace access and resource permissions before tool execution. Add idempotency deduplication via correlationId. All tools operate in mock mode for local development with structured JSON result payloads.
- [x] **Milestone 40 (Completed):** Backend RemediationPort & Mutation Circuit Breaker — Implement `RemediationPort` domain port and `RemediationAdapter` infrastructure adapter communicating with MCP Server write-back tools via JSON-RPC 2.0. Dedicated `mutationCircuitBreaker` (Resilience4j) isolated from the read-path circuit breaker. `RemediationOrchestrator` application service coordinating request validation, circuit breaker wrapping, MCP dispatch, and structured audit logging with inherited `correlationId`. REST endpoint `POST /api/v1/remediations` accepting typed `RemediationRequest` DTOs and returning RFC 7807 responses on failure.
- [x] **Milestone 41 (Completed):** Observability UI 'Execute Action' Integration & E2E Remediation Tests — Implement `ExecuteActionButton` component within `RecommendedActionBlock`, with loading spinner, concurrent-action lock, and RFC 7807 success/error banner rendering. TanStack Mutation hook for `POST /api/v1/remediations`. E2E smoke test validating the full loop: chaos pod → AI diagnosis → user click → MCP mutation → cluster state correction → UI confirmation banner.

### ✅ Phase 18 — Analysis Dismissal & Lifecycle Management (Completed)
**Architecture:** Introduces a lifecycle state machine for AI analysis reports, enabling operators to dismiss analysis cards from the active Observability Dashboard without applying remediation. The design follows Option B — a new `AnalysisLifecycle` domain aggregate wrapping the immutable `AiAnalysis` record with mutable state (PENDING → DISMISSED / REMEDIATED). The feature spans all platform layers: pure domain model with state guards, hexagonal port/adapter pattern, event-driven Kafka publication, OpenSearch persistence updates, RFC 7807 REST endpoint, and React frontend integration with TanStack Query mutations.

**State Machine:**
- `PENDING` → `DISMISSED` (operator dismiss via REST)
- `PENDING` → `REMEDIATED` (successful remediation — existing flow)
- Terminal states (`DISMISSED`, `REMEDIATED`) reject further transitions

**Security Model:**
- Only PENDING analyses can be dismissed (idempotent guard)
- RFC 7807 error responses for 404 (not found) and 409 (already resolved)
- Audit trail via structured logging and Kafka event publication

- [x] **Milestone 42 (Completed):** Domain Layer — `AnalysisStatus` enum (PENDING/REMEDIATED/DISMISSED), `AnalysisLifecycle` mutable entity with dismiss state guard, `AnalysisLifecycleEvent` domain event record, `DismissalResult` value object, `DismissAnalysisUseCase` inbound port, `AnalysisLifecycleRepositoryPort` outbound port, `LifecycleMessagingPort` outbound port, domain exceptions (`AnalysisNotFoundException`, `AnalysisAlreadyResolvedException`). Zero Spring imports — pure domain.
- [x] **Milestone 43 (Completed):** Service Layer — `DismissAnalysisService` implementing `DismissAnalysisUseCase` with dismiss flow (findById → guard → transition → save → publish event → return result). Follows `RemediationOrchestrator` structural pattern.
- [x] **Milestone 44 (Completed):** Infrastructure Layer — REST endpoint `POST /api/v1/analyses/{id}/dismiss` with optional reason body, `GlobalExceptionHandler` extensions (404/409 RFC 7807), `KafkaLifecycleMessagingAdapter` for event publication, `AnalysisLifecycleConsumer` for async OpenSearch updates, `OpenSearchLifecycleRepositoryAdapter` for persistence, query adapter filter excluding DISMISSED analyses. All adapters use `@ConditionalOnProperty` for environment-specific activation.
- [x] **Milestone 45 (Completed):** Testing & Quality — 6 jqwik correctness properties (construction invariants, dismiss transition, reason defaulting, event publication, rejection guards, query exclusion), JUnit 5 unit tests, MockMvc controller tests, ArchUnit domain purity verification. 9 architecture tests enforcing Clean Architecture compliance.
- [x] **Milestone 46 (Completed):** Frontend Integration — `useDismissAnalysis` TanStack Query mutation hook with query invalidation on success, `DismissButton` React component with inline reason input and quick-dismiss flow, `AnalysisCard` integration with derived document ID. Dismiss triggers refetch → card exits dashboard via existing `card-exit` animation.