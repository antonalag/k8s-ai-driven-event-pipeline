# services/k8s-collector

Kubernetes cluster monitor that watches Pod state changes via the native Informer API and publishes structured events to Apache Kafka.

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 21 (Temurin) |
| Spring Boot | 3.5.14 |
| Kubernetes Java Client | 22.0.0 |
| Spring Kafka | 3.3.x (BOM-managed) |
| Build Tool | Gradle 8.x |

---

## Responsibilities

1. **Pod State Watching** — Registers a Kubernetes Informer against the cluster API server to receive real-time callbacks on Pod lifecycle changes (ADDED, MODIFIED, DELETED).
2. **Event Filtering** — Selectively processes Pods in failure states (`Failed`, `Pending`, `Unknown`) and ignores healthy transitions to minimize noise.
3. **Event Mapping** — Transforms raw `V1Pod` objects into structured `KubernetesEvent` records conforming to the contract at `specs/schemas/k8s-event.v1.json`.
4. **Kafka Publishing** — Asynchronously publishes `KubernetesEvent` records to the `k8s-pod-events` topic using `KafkaTemplate` with pod name as the partition key (guarantees ordered delivery per pod).

---

## Architecture

```
src/main/java/com/k8s/pipeline/collector/
├── domain/
│   ├── model/
│   │   └── PodPhase.java              # Enum: Running, Pending, Succeeded, Failed, Unknown
│   └── ports/
│       ├── EventPublisherPort.java     # SPI: void publish(KubernetesEvent event)
│       └── EventPublishException.java  # Domain exception for publish failures
├── service/
│   └── PodWatcherService.java          # Informer lifecycle + event routing
├── infrastructure/
│   └── messaging/kafka/
│       └── KafkaEventPublisher.java    # KafkaTemplate adapter implementing EventPublisherPort
└── K8sCollectorApplication.java        # Spring Boot entry point
```

### Design Principles

- **Hexagonal layering** — Domain defines the `EventPublisherPort` interface; the Kafka adapter implements it in the infrastructure layer.
- **Informer-based (not polling)** — Uses the Kubernetes Java Client's `SharedInformerFactory` for efficient watch-based event delivery with local cache.
- **Resilient to Kafka downtime** — Events buffer in the Kafka producer's in-memory buffer (32 MB default). The Informer retains its local cache, so events are not lost during transient Kafka unavailability.
- **Idempotent production** — Kafka producer runs with `enable.idempotence=true` to prevent duplicate messages on retry.

---

## Kafka Integration

| Property | Value |
|----------|-------|
| Topic | `k8s-pod-events` |
| Partitions | 3 |
| Partition Key | Pod name (ensures per-pod ordering) |
| Serialization | JSON (`JsonSerializer<KubernetesEvent>`) |
| Idempotence | Enabled |

---

## Configuration

Key application properties (overridable via environment variables):

| Property | Default | Description |
|----------|---------|-------------|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker address |
| `spring.kafka.producer.key-serializer` | `StringSerializer` | Partition key serializer |
| `spring.kafka.producer.value-serializer` | `JsonSerializer` | Event payload serializer |

### Kubernetes Access

The Informer reads cluster state via the standard kube-config resolution:
1. In-cluster ServiceAccount token (when deployed to K8s)
2. `~/.kube/config` (local development)
3. `KUBECONFIG` environment variable override

---

## Build & Test

```bash
# Full build + tests
./gradlew :services:k8s-collector:clean :services:k8s-collector:build --no-daemon

# Tests only
./gradlew :services:k8s-collector:test --no-daemon
```

### Running Locally

Requires:
- A running Kubernetes cluster (k3d, kind, or minikube)
- Kafka broker on `localhost:9092` (via `make up` or standalone)

```bash
# Start with Gradle bootRun
./gradlew :services:k8s-collector:bootRun --no-daemon
```

---

## Event Contract

Published events conform to `specs/schemas/k8s-event.v1.json`:

```json
{
  "podName": "my-app-7b4c6f8d9-x2k5m",
  "namespace": "default",
  "status": "Failed",
  "reason": "CrashLoopBackOff",
  "message": "Back-off restarting failed container",
  "timestamp": "2025-06-22T10:30:00Z",
  "nodeName": "k3d-cluster-agent-0",
  "containerStatuses": [...]
}
```
