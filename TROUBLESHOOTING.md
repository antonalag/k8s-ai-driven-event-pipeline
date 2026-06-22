# Troubleshooting Guide

Common issues encountered when running the platform locally and their resolutions.

---

## Platform Startup (`make init`)

### Container `mcp-server` is unhealthy

**Symptom:**
```
✘ Container mcp-server  Error dependency mcp-server failed to start
```

**Cause:** The server binds to `0.0.0.0` (IPv4) but the Docker healthcheck resolves `localhost` to `::1` (IPv6) on Alpine Linux, resulting in "Connection refused".

**Fix:** Ensure the healthcheck in `deployments/docker-compose.yaml` uses `127.0.0.1` instead of `localhost`:
```yaml
test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://127.0.0.1:3001/health || exit 1"]
```

---

### Container `ai-analyzer` exited (1) — OpenSearch connection refused

**Symptom:**
```
Caused by: java.net.ConnectException: Connection refused
  at org.opensearch.data.client.orhlc.OpenSearchRestTemplate...
```

**Cause:** OpenSearch is not running or not reachable from the `ai-analyzer` container.

**Fix:** Ensure the `opensearch` service is defined in `deployments/docker-compose.yaml` and that `ai-analyzer` depends on it:
```yaml
depends_on:
  opensearch:
    condition: service_healthy
```

The `OPENSEARCH_URIS` environment variable inside the compose file should use the internal Docker network URL (`http://opensearch:9200`), not `host.docker.internal`.

---

### Container `ai-analyzer` is unhealthy — `/actuator/health` returns 404

**Symptom:**
```json
{"ExitCode": 1, "Output": "wget: server returned error: HTTP/1.1 404"}
```

**Cause:** The `spring-boot-starter-actuator` dependency is missing from `services/ai-analyzer/build.gradle`.

**Fix:** Ensure the dependency exists:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

---

### Container `ai-analyzer` exited (1) — CircuitBreakerRegistry ambiguity

**Symptom:**
```
Parameter 0 of constructor in CircuitBreakerStateAdapter required a single bean,
but 2 were found: mcpCircuitBreakerRegistry, mutationCircuitBreakerRegistry
```

**Cause:** Multiple `CircuitBreakerRegistry` beans exist and Spring cannot determine which one to inject.

**Fix:** The `CircuitBreakerStateAdapter` constructor must use `@Qualifier("mcpCircuitBreakerRegistry")` to disambiguate:
```java
public CircuitBreakerStateAdapter(
        @Qualifier("mcpCircuitBreakerRegistry") CircuitBreakerRegistry circuitBreakerRegistry) {
```

---

### Container `ai-analyzer` exited (1) — `NoClassDefFoundError: co/elastic/clients/ApiClient`

**Symptom:**
```
Caused by: java.lang.NoClassDefFoundError: co/elastic/clients/ApiClient
```

**Cause:** Spring Boot 3.5.x ships autoconfiguration for the native Elasticsearch Java Client, but this project uses `spring-data-opensearch` which doesn't include `co.elastic.clients`.

**Fix:** Exclude the conflicting autoconfiguration in `application.properties`:
```properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration,\
  org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration,\
  org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration,\
  org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration,\
  org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration
```

And add explicit repository scanning:
```java
@Configuration
@EnableElasticsearchRepositories(
    basePackages = "com.platform.analyzer.infrastructure.persistence.opensearch"
)
public class OpenSearchRepositoryConfig {}
```

---

### `network platform-net declared as external, but could not be found`

**Symptom:**
```
network platform-net declared as external, but could not be found
```

**Cause:** The Kafka sub-compose (`deployments/docker-compose/docker-compose.yml`) declares `platform-net` as `external: true`, but the network is created by the parent compose file and doesn't exist yet as an external resource.

**Fix:** Change the child compose network declaration from `external: true` to `driver: bridge`:
```yaml
networks:
  platform-net:
    driver: bridge
```

---

### Kafka WARN: `UNKNOWN_TOPIC_OR_PARTITION` for `ai-analysis-events`

**Symptom:**
```
The metadata response from the cluster reported a recoverable issue:
{ai-analysis-events=UNKNOWN_TOPIC_OR_PARTITION}
```

**Cause:** The `ai-analysis-events` topic is not pre-created. The `ai-analyzer` consumer group tries to subscribe before the topic exists.

**Fix:** Add topic creation to the `kafka-init` service in `deployments/docker-compose/docker-compose.yml`:
```bash
kafka-topics --bootstrap-server kafka:29092 \
  --create --if-not-exists \
  --topic ai-analysis-events \
  --partitions 3 --replication-factor 1
```

This warning is non-fatal — the topic auto-creates on first produce — but pre-creating eliminates noise in logs.

---

## Chaos Injection (`kubectl apply`)

### `error validating: failed to download openapi: Connection refused`

**Symptom:**
```
error: error validating "deployments/chaos/golden-path-deployment.yaml":
  Get "https://0.0.0.0:XXXXX/openapi/v2": dial tcp 0.0.0.0:XXXXX: connect: connection refused
```

**Cause:** No Kubernetes cluster is running locally. The `kubectl` context points to a stopped or non-existent cluster.

**Fix:**
```bash
# Check cluster status
k3d cluster list

# Start an existing cluster
k3d cluster start <cluster-name>

# Or create a new one
k3d cluster create dev

# Verify connectivity
kubectl cluster-info
```

The platform infrastructure (Docker Compose) runs independently, but chaos injection and the `k8s-collector` Informer require a live K8s cluster.

---

### Pods stuck in `Pending` after chaos injection

**Cause:** The local cluster might not have enough resources or the namespace doesn't exist.

**Fix:**
```bash
# Ensure the namespace exists
kubectl apply -f deployments/chaos/namespace.yaml

# Check node resources
kubectl describe nodes | grep -A5 "Allocated resources"
```

---

## Observability UI Issues

### Nginx: `Permission denied` for `/var/cache/nginx/client_temp`

**Symptom:**
```
nginx: [emerg] mkdir() "/var/cache/nginx/client_temp" failed (13: Permission denied)
```

**Cause:** The container is `read_only: true` and uses `tmpfs` mounts for writable directories. However, the tmpfs mounts default to `root:root` ownership, and the Nginx process runs as UID 101 (`nginx` user in Alpine).

**Fix:** Add `uid=101,gid=101` to each tmpfs mount in `deployments/docker-compose.yaml`:
```yaml
tmpfs:
  - /var/cache/nginx:uid=101,gid=101
  - /var/run:uid=101,gid=101
  - /var/log/nginx:uid=101,gid=101
  - /run:uid=101,gid=101
  - /tmp:uid=101,gid=101
```

---

### UI shows "API Error" — requests go to `/undefined/api/v1/analyses`

**Symptom:** Browser Network tab shows requests to `http://localhost:3000/undefined/api/v1/analyses`.

**Cause:** The Vite environment variable `VITE_API_BASE_URL` is not defined at build time. Vite replaces undefined env vars with the literal string `"undefined"` in the compiled output.

**Fix:**
1. Ensure `ui/.env` exists with an empty value (for container mode, Nginx proxies `/api/`):
   ```
   VITE_API_BASE_URL=
   ```
2. Ensure the `ui/Dockerfile` copies `.env*` before `npm run build`:
   ```dockerfile
   COPY .env* ./
   RUN npm run build
   ```
3. The client code should guard against the literal string `"undefined"`:
   ```typescript
   const envBase = import.meta.env.VITE_API_BASE_URL;
   const API_BASE_URL = (envBase && envBase !== 'undefined') ? envBase : '';
   ```
4. Rebuild the UI image: `docker compose -f deployments/docker-compose.yaml up -d --build observability-ui`

---

## AI Provider Issues

### Ollama: `Connection refused` from inside Docker

**Cause:** The `ai-analyzer` container tries to reach Ollama at `http://host.docker.internal:11434` but:
- On Linux, `host.docker.internal` may not resolve without `--add-host`
- Ollama might not be listening on all interfaces

**Fix (Linux):**
```bash
# Option 1: Ensure Ollama listens on all interfaces
OLLAMA_HOST=0.0.0.0 ollama serve

# Option 2: Use host network IP in .env
OLLAMA_API_URL=http://172.17.0.1:11434
```

The `172.17.0.1` address is the default Docker bridge gateway — accessible from containers without extra configuration.

---

### BYOK: `401 Unauthorized`

**Cause:** The API key in `.env` is invalid, expired, or the endpoint URL is wrong.

**Fix:**
```bash
# Test your key directly
curl -H "Authorization: Bearer $BYOK_API_KEY" \
  "$BYOK_ENDPOINT/v1/models"

# Common issues:
# - Missing "sk-" prefix for OpenAI keys
# - Endpoint should NOT include /v1/chat/completions (just the base URL)
# - DeepSeek uses: BYOK_ENDPOINT=https://api.deepseek.com
```

---

## General Docker Issues

### Port conflicts (address already in use)

**Fix:**
```bash
# Find what's using the port
lsof -i :9092  # Kafka
lsof -i :8082  # AI Analyzer
lsof -i :3000  # UI
lsof -i :9200  # OpenSearch

# Kill the conflicting process or change ports in .env
```

### Stale containers from previous runs

**Fix:**
```bash
# Full cleanup
make clean

# Nuclear option (removes ALL Docker resources)
docker system prune -a --volumes
```

---

## Quick Diagnostic Commands

```bash
# Check all container statuses
docker compose -f deployments/docker-compose.yaml ps

# View logs for a specific service
docker logs <container-name> 2>&1 | tail -30

# Inspect healthcheck state
docker inspect <container-name> --format='{{json .State.Health}}' | python3 -m json.tool

# Test internal connectivity from ai-analyzer
docker exec ai-analyzer wget -qO- http://opensearch:9200/_cluster/health
docker exec ai-analyzer wget -qO- http://mcp-server:3001/health

# Verify Kafka topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```
