# System Constitution: Kubernetes-Native AI Agent (v1.0)

## 1. System Mission
Transform raw Kubernetes signals (events, Pod status changes) into structured, actionable knowledge through a contract-driven distributed pipeline and an AI reasoning layer.

## 2. Platform Layers (Macro Vision)
The system will be built incrementally across 4 isolated, contract-backed layers:
1. **Ingestion Layer (Phase 1):** Kubernetes Collector using local Informers written in Java/Spring Boot.
2. **Streaming Layer (Phase 2):** Apache Kafka backbone for event routing and decoupling.
3. **Intelligence Layer (Phase 3):** Structured AI Analyzer powered by Ollama.
4. **Storage Layer (Phase 4):** OpenSearch persistence for AI analysis reports, enabling historical querying and observability dashboards.

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

### ✅ Phase 5 — Intelligent Correlation & Context History (Completed)
**Architecture:** Enhance the `services/ai-analyzer` reasoning layer to provide the AI model with operational memory. Before sending a `KubernetesEvent` to the AI provider, the system will query OpenSearch via the `AiAnalysisRepositoryPort` to retrieve previous analysis verdicts for the same Pod. This history will be injected into a new system prompt layout, allowing the LLM to cross-reference past failures, detect cascading regressions, and avoid redundant diagnostic steps (BYOK-ready contextual enrichment).

- [x] **Milestone 11:** Update `system.spec.md` and design the prompt template configuration for historical context injection.
- [x] **Milestone 12:** Adapt `AiLanguageModelPort` and `OllamaAnalyzerService` to orchestrate history retrieval and inject past verdicts into the reasoning pipeline.
- [x] **Milestone 13:** Update the Ollama/BYOK infrastructure adapter to parse the history list and format it cleanly inside the LLM prompt without regression.