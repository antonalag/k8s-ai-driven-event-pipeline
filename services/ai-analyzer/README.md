# services/ai-analyzer

AI reasoning engine and REST API surface for the Kubernetes troubleshooting pipeline.

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 21 (Temurin) |
| Spring Boot | 3.5.14 |
| Spring Kafka | 3.3.x (BOM-managed) |
| Spring Data OpenSearch | 1.8.1 |
| Resilience4j | 2.2.0 |
| Jackson (JSR-310) | BOM-managed |
| jqwik (PBT) | 1.9.2 |
| ArchUnit | 1.3.0 |
| Build Tool | Gradle 8.x (Kotlin DSL wrapper) |

---

## Responsibilities

1. **Kafka Consumer** — Listens to `k8s-pod-events` topic, filters events by pod status (`Failed`, `Pending`, `Unknown`), and routes them to the analysis pipeline.
2. **MCP Context Enrichment** — Queries the MCP Server via JSON-RPC 2.0 for live cluster context (`describe_pod`, `get_events`, `get_logs`) before LLM inference.
3. **AI Inference** — Constructs a structured three-section prompt (failure event + historical context from OpenSearch + MCP cluster state) and submits it to the configured AI provider (Ollama or BYOK).
4. **Structured Output Validation** — Enforces JSON Schema compliance on every LLM response. Free-text or malformed output is rejected.
5. **Kafka Producer** — Publishes validated `AiAnalysisEvent` to `ai-analysis-events` topic for downstream persistence.
6. **OpenSearch Persistence** — Consumes its own `ai-analysis-events` topic and indexes documents to the `ai-analysis-reports` index with deterministic IDs (`{podName}-{analyzedAt}`).
7. **REST API** — Exposes `GET /api/v1/analyses` (query with optional namespace/podName filters) and `POST /api/v1/remediations` (1-click cluster mutations).
8. **RFC 7807 Error Surface** — All exceptions map to machine-readable `ProblemDetail` responses via `GlobalExceptionHandler`.

---

## Architecture (Hexagonal / Ports & Adapters)

```
src/main/java/com/platform/analyzer/
├── domain/                    # Pure domain — zero framework annotations
│   ├── model/                 # AiAnalysis, KubernetesEvent, RemediationResult
│   └── ports/                 # SPI interfaces (AiLanguageModelPort, McpContextPort,
│                              #   AiAnalysisRepositoryPort, AiAnalysisQueryPort,
│                              #   RemediationPort, EventPublisherPort)
├── service/                   # Application services (PodAnalyzerService,
│                              #   RemediationOrchestrator)
├── infrastructure/            # Framework-bound adapters
│   ├── client/
│   │   ├── ollama/            # OllamaLanguageModelAdapter (RestClient → Ollama API)
│   │   ├── byok/             # ByokLanguageModelAdapter (OpenAI-compatible REST)
│   │   └── mcp/              # McpClientAdapter (JSON-RPC 2.0 → MCP Server)
│   ├── messaging/kafka/       # PodEventConsumer, AiAnalysisProducer,
│   │                          #   AiAnalysisStorageConsumer
│   ├── persistence/           # OpenSearchAnalysisQueryAdapter, AiAnalysisDocument,
│   │                          #   AiAnalysisRepository
│   ├── web/                   # AiAnalysisQueryController, RemediationController,
│   │                          #   GlobalExceptionHandler
│   └── remediation/           # RemediationAdapter (MCP write-back tools)
└── config/                    # Spring @Configuration classes, @ConfigurationProperties,
                               #   resilience decorators, AI provider routing
```

### Design Rules

- **Domain layer** has zero imports from `org.springframework`, `jakarta`, or any infrastructure framework.
- **Ports** are interfaces in `domain/ports/` implemented exclusively by adapters in `infrastructure/`.
- **Service layer** depends only on domain ports — never on concrete adapters.
- **Configuration layer** wires adapters to ports and applies cross-cutting decorators (circuit breakers, resilience wrappers).

---

## Circuit Breakers (Resilience4j)

Three independent circuit breakers protect different failure domains:

| Name | Scope | Fallback Behavior |
|------|-------|-------------------|
| `aiCircuitBreaker` | LLM inference (Ollama or BYOK HTTP calls) | Returns degraded `AiAnalysis` with verdict `DEGRADED` and "AI provider unavailable" message |
| `mcpCircuitBreaker` | MCP Server read-path (describe_pod, get_events, get_logs) | Prompt constructed without cluster context (`mcpContextAvailable: false`) |
| `mutationCircuitBreaker` | MCP Server write-path (remediation tools) | Returns `RemediationResult.Failure` with error code `CIRCUIT_OPEN`, HTTP 503 |

Each breaker operates with:
- Sliding window (count-based, configurable via environment variables)
- Failure rate threshold (default 50%)
- Wait duration in open state (default 30s)
- Permitted calls in half-open state for probing

Fault isolation is guaranteed — a failing AI provider does not affect MCP read operations, and MCP read failures do not block mutation operations.

---

## AI Provider Routing

Runtime provider selection via `PLATFORM_AI_PROVIDER` environment variable:

| Value | Adapter | Configuration |
|-------|---------|---------------|
| `ollama` | `OllamaLanguageModelAdapter` | `OLLAMA_API_URL`, `OLLAMA_MODEL` |
| `byok` | `ByokLanguageModelAdapter` | `BYOK_ENDPOINT`, `BYOK_API_KEY`, `BYOK_MODEL`, `BYOK_PROVIDER_TYPE` |

Provider beans are mutually exclusive via `@ConditionalOnProperty`. `AiProviderValidator` enforces fail-fast startup if required properties are missing.

---

## Build & Test

```bash
# Full build + all tests
./gradlew clean build --no-daemon

# Run tests only
./gradlew test --no-daemon

# Run a specific test class
./gradlew test --tests "com.platform.analyzer.*CircuitBreaker*" --no-daemon
```

### Test Coverage

- **Unit tests** — Domain logic, prompt construction, markdown stripping, JSON parsing
- **Property-based tests (jqwik)** — 9 resilience properties, 7 provider routing properties, BYOK payload mapping
- **Architecture tests (ArchUnit)** — Domain purity enforcement, no framework leakage
- **Integration tests** — Provider bean mutual exclusivity, Kafka consumer wiring

---

## Configuration

Key application properties (overridable via environment variables):

| Property | Default | Description |
|----------|---------|-------------|
| `platform.ai.provider` | `ollama` | AI backend selection |
| `platform.mcp.server-url` | `http://localhost:3001` | MCP Server endpoint |
| `platform.mcp.connection-timeout` | `5` | MCP connection timeout (seconds) |
| `platform.mcp.read-timeout` | `10` | MCP read timeout (seconds) |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker address |
| `spring.data.opensearch.uris` | `http://localhost:9200` | OpenSearch cluster |

---

## Container

```dockerfile
# Multi-stage: Gradle build → JRE 21 runtime (non-root, read-only fs)
docker build -f services/ai-analyzer/Dockerfile -t ai-analyzer .
```

Exposed port: **8082**
Health endpoint: `GET /actuator/health`
