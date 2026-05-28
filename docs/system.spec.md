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
- [ ] **Milestone 1:** Design the event contract schema at `specs/schemas/k8s-event.v1.json`.
- [ ] **Milestone 2:** Initialize the `services/k8s-collector` microservice using Java 21 & Spring Boot 3.x.
- [ ] **Milestone 3:** Implement the Kubernetes Informer to watch and stream Pod state updates.