# deployments/

Infrastructure-as-Code manifests for local development and chaos testing.

---

## Directory Structure

```
deployments/
├── docker-compose.yaml          # Global orchestration (includes all services)
├── docker-compose/
│   └── docker-compose.yml       # Kafka infrastructure (KRaft mode)
├── chaos/
│   ├── namespace.yaml           # chaos-validation namespace
│   ├── crashloop-pod.yaml       # CrashLoopBackOff injection
│   ├── oomkilled-pod.yaml       # OOMKilled injection
│   ├── imagepull-pod.yaml       # ImagePullBackOff injection
│   ├── golden-path-deployment.yaml  # Demo scenario (Deployment-level for mutation tools)
│   └── Makefile                 # Chaos lifecycle helpers
├── k8s/                         # (Reserved for production K8s manifests)
├── security-audit.sh            # Container security scanning
└── smoke-test.sh                # Platform smoke test
```

---

## Docker Compose — Local Platform

### Services

| Service | Image | Port | Role |
|---------|-------|------|------|
| `kafka` | `confluentinc/cp-kafka:7.6.1` (SHA-256 pinned) | 9092 (host), 29092 (internal) | Event streaming backbone (KRaft mode, no ZooKeeper) |
| `kafka-init` | Same as above | — | One-shot topic creator (`k8s-pod-events`, `ai-analysis-events`, 3 partitions each) |
| `opensearch` | `opensearchproject/opensearch:2.18.0` | 9200 | AI analysis report persistence and historical querying |
| `k8s-collector` | Built from `services/k8s-collector/Dockerfile` | 8081 | Kubernetes Informer → Kafka event producer |
| `mcp-server` | Built from `services/mcp-server/Dockerfile` | 3001 | MCP intelligence layer (JSON-RPC 2.0) |
| `ai-analyzer` | Built from `services/ai-analyzer/Dockerfile` | 8082 | AI reasoning engine + REST API |
| `observability-ui` | Built from `ui/Dockerfile` | 3000 | Frontend dashboard (Nginx) |

### Networking

All services communicate over a shared `platform-net` bridge network. The UI's Nginx reverse-proxy routes `/api/` to `ai-analyzer:8082` internally, eliminating CORS in container mode.

The `k8s-collector` mounts `~/.kube/config` read-only and uses a startup script that rewrites `0.0.0.0`/`127.0.0.1` server addresses to `host.docker.internal` for Docker network reachability. The `extra_hosts: host.docker.internal:host-gateway` directive ensures the host machine is resolvable from inside the container.

### Kafka Configuration (KRaft)

- **Mode:** Combined broker + controller (single node)
- **Cluster ID:** Static base64 UUID for reproducibility
- **Topics:** `k8s-pod-events` (3 partitions), `ai-analysis-events` (auto-created by Spring Kafka)
- **Retention:** 24 hours, 100 MB segment size
- **Security:** `no-new-privileges`, memory-bounded (1 GB limit)

### Commands

```bash
# Start the full platform
docker compose -f deployments/docker-compose.yaml --env-file .env up --build -d

# Stop and clean volumes
docker compose -f deployments/docker-compose.yaml down -v --remove-orphans

# View Kafka topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consume from topic (debugging)
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic k8s-pod-events \
  --from-beginning --max-messages 5
```

---

## Chaos Injection Manifests

All manifests target the `chaos-validation` namespace and use SHA-256 pinned images.

| Manifest | Failure Mode | Mechanism |
|----------|-------------|-----------|
| `crashloop-pod.yaml` | `CrashLoopBackOff` | Container exits immediately (invalid command) |
| `oomkilled-pod.yaml` | `OOMKilled` | Memory limit exceeded by stress workload |
| `imagepull-pod.yaml` | `ImagePullBackOff` | Non-existent image tag |
| `golden-path-deployment.yaml` | `ImagePullBackOff` | Deployment-level (supports mutation tools for remediation) |

### Applying Chaos

```bash
# Create namespace (idempotent)
kubectl apply -f deployments/chaos/namespace.yaml

# Inject a specific failure
kubectl apply -f deployments/chaos/crashloop-pod.yaml

# Watch pod state
kubectl get pods -n chaos-validation -w

# Clean up
kubectl delete -f deployments/chaos/crashloop-pod.yaml
```

### Golden Path Demo (Full Pipeline)

```bash
# Inject → observe → remediate → verify
kubectl apply -f deployments/chaos/golden-path-deployment.yaml
# Open http://localhost:3000 and wait for AI diagnosis
# Click [Apply Recommended Fix]
kubectl get pods -n chaos-validation  # Pod should be Running
```

---

## Security Hardening

All containers in the Compose stack enforce:

- `read_only: true` filesystem (writable only via explicit `tmpfs` mounts)
- `no-new-privileges:true` security option
- `user: "1000:1000"` (non-root execution)
- Memory limits and reservations
- Healthchecks with start period grace
