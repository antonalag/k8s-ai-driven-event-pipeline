# System Constitution: Kubernetes-Native AI Agent (v1.0)

## 1. System Mission
Transform raw Kubernetes signals (events, Pod status changes) into structured, actionable knowledge through a contract-driven distributed pipeline and an AI reasoning layer.

## 2. Platform Layers (Macro Vision)
The system will be built incrementally across 3 isolated, contract-backed layers:
1. **Ingestion Layer (Phase 1 - Current):** Kubernetes Collector using local Informers written in Java/Spring Boot.
2. **Streaming Layer (Phase 2):** Apache Kafka backbone for event routing and decoupling.
3. **Intelligence Layer (Phase 3):** Structured AI Analyzer powered by Ollama.

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

### 🚧 Phase 3 — Intelligence Layer (Current)
- [x] **Milestone 6:** Design the AI analysis contract schema at `specs/schemas/ai-analysis.v1.json`.
- [x] **Milestone 7 (Completed):** Initialize the `services/ai-analyzer` microservice (Spring Boot 3.5.x, Java 21, Gradle multi-project module). Implemented reactive Kafka consumer backbone (`PodEventConsumer`) with selective routing (Failed/Pending/Unknown only), Ollama `RestClient` integration (`OllamaAnalyzerService`), structured SRE system prompt with embedded JSON Schema contract, and defensive markdown-fence stripping for reliable `AiAnalysis` parsing.
- [ ] **Milestone 8 (In Progress):** Evolve `services/ai-analyzer` into a hybrid Consumer+Producer service. After a successful Ollama diagnosis, publish the structured `AiAnalysis` result to the `ai-analysis-events` Kafka topic using a `KafkaTemplate`-backed `AiAnalysisProducer`. The `ai-analyzer` service thus acts as both a consumer of `k8s-pod-events` and a producer of `ai-analysis-events`, closing the intelligence pipeline loop.