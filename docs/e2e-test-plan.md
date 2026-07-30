# Plan de Pruebas End-to-End — Plataforma v1.0

## Prerrequisitos

Antes de iniciar las pruebas, asegurar que el entorno cumple con:

- **Docker** (v24+) y **Docker Compose** (v2.20+) instalados
- **Cluster Kubernetes** accesible (minikube, kind, o cloud) con `kubectl` configurado (`~/.kube/config`)
- **Ollama** ejecutándose en el host con un modelo cargado (recomendado `llama3.1:8b`)
- **Puertos libres:** 3000 (UI), 8081 (Collector), 8082 (AI Analyzer), 9092 (Kafka), 9200 (OpenSearch), 11434 (Ollama)
- Namespace `chaos-validation` disponible para inyección de caos

### Arrancar el stack completo ✅

```bash
docker compose -f deployments/docker-compose.yaml up -d --build --wait
```

> **Nota:** El primer build puede tardar varios minutos (descarga de imágenes base + compilación Gradle + npm build).

### Verificar que Ollama tiene un modelo disponible ✅

```bash
ollama list
# Debe mostrar al menos un modelo (ej: llama3.1:8b)
```

---

## 1. Infraestructura — Validar que todo arranca ✅

| # | Check | Comando | Resultado esperado |
|---|-------|---------|-------------------|
| 1.1 | Todos los contenedores healthy | `docker compose -f deployments/docker-compose.yaml ps` | Todos en estado `healthy` (excepto `kafka-init` que termina con exit 0) |
| 1.2 | Kafka responde | `docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092` | Lista de API versions sin error |
| 1.3 | Topics Kafka creados | `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list` | Muestra `k8s-pod-events` y `ai-analysis-events` |
| 1.4 | OpenSearch cluster operativo | `curl -s http://localhost:9200/_cluster/health \| jq .status` | `"green"` o `"yellow"` |
| 1.5 | AI Analyzer actuator UP | `curl -s http://localhost:8082/actuator/health \| jq .status` | `"UP"` |
| 1.6 | MCP Server healthy | `docker exec mcp-server wget -qO- http://127.0.0.1:3001/health` | Respuesta 200 con body JSON |
| 1.7 | Observability UI accesible | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000` | `200` |

### Checklist rápido

- [X] 1.1 — Todos los contenedores healthy
- [X] 1.2 — Kafka broker responde
- [X] 1.3 — Topics `k8s-pod-events` y `ai-analysis-events` existen
- [X] 1.4 — OpenSearch cluster green/yellow
- [X] 1.5 — AI Analyzer actuator UP
- [X] 1.6 — MCP Server healthy
- [X] 1.7 — Observability UI sirve en localhost:3000

---

## 2. Validación del Contrato API (Backend)

### 2.1 Endpoint GET /api/v1/analyses ✅

```bash
# Estado inicial — sin diagnósticos
curl -s http://localhost:8082/api/v1/analyses | jq .
```
- [X] HTTP 200
- [X] Respuesta: array vacío `[]` (si no hay eventos previos)

### 2.2 Endpoint GET /api/v1/analyses/history ✅

```bash
curl -s http://localhost:8082/api/v1/analyses/history | jq .
```
- [X] HTTP 200
- [X] Respuesta con estructura paginada: `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`

### 2.3 Validación de parámetros — page negativo ✅

```bash
curl -s -o /dev/null -w "%{http_code}" "http://localhost:8082/api/v1/analyses/history?page=-1"
```
- [X] HTTP 400
- [X] Body RFC 7807: `{ "type": "...", "title": "Bad Request", "status": 400, "detail": "..." }`

### 2.4 Validación de size — cap silencioso a 100 ✅
 
```bash
curl -s "http://localhost:8082/api/v1/analyses/history?size=500" | jq .size
```
- [X] HTTP 200
- [X] Campo `size` en respuesta: `100` (cap silencioso)

### 2.5 POST /api/v1/remediations — body inválido ✅

```bash
curl -s -X POST http://localhost:8082/api/v1/remediations \
  -H "Content-Type: application/json" \
  -d '{"invalid": "body"}' \
  -w "\n%{http_code}"
```
- [X] HTTP 400
- [X] Body RFC 7807 con campo `detail` describiendo campos faltantes

### 2.6 POST /api/v1/analyses/{id}/dismiss — ID inexistente ✅

```bash
curl -s -X POST http://localhost:8082/api/v1/analyses/nonexistent-id-12345/dismiss \
  -H "Content-Type: application/json" \
  -w "\n%{http_code}"
```
- [X] HTTP 404
- [X] Body RFC 7807: `"title": "Analysis Not Found"`

---

## 3. Golden Path — Pipeline Completo (Test más importante)

Este test valida la cadena completa: chaos → detección → diagnóstico AI → UI → remediación → recuperación.

### 3.1 Inyectar caos ✅

```bash
kubectl apply -f deployments/chaos/golden-path-deployment.yaml
```

- [X] Namespace `chaos-validation` creado
- [X] Deployment `golden-path-app` creado

```bash
kubectl get pods -n chaos-validation -w
```

- [X] Pod entra en estado `ImagePullBackOff` o `ErrImagePull` en ~5-10 segundos

### 3.2 Verificar ingestión de eventos ✅

Esperar ~10 segundos después de que el pod falle.

```bash
docker logs k8s-collector --tail 30 | grep -i "golden-path"
```

- [X] Los logs muestran que el evento del pod fue detectado y publicado a Kafka

### 3.3 Verificar diagnóstico AI ✅

Esperar ~15-30 segundos (depende de la velocidad del modelo LLM).

```bash
curl -s http://localhost:8082/api/v1/analyses | jq .
```

- [X] Aparece al menos una entrada nueva
- [X] Campo `verdict` = `"CRITICAL_FAILURE"`
- [X] Campo `podName` contiene `golden-path-app`
- [X] Campo `modelUsed` muestra el nombre del modelo configurado (ej: `llama3.1`)
- [X] Campo `recommendedActions` contiene al menos un comando actionable (referencia a `fix_container_image`)

### 3.4 Verificar UI — Dashboard ✅

- [X] Abrir http://localhost:3000 en el navegador
- [X] Tab "Dashboard" está activo por defecto
- [X] Aparece una card de diagnóstico con:
  - [X] Verdict: `CRITICAL_FAILURE` (badge rojo)
  - [X] Nombre del pod visible (`golden-path-app-xxxxx`)
  - [X] Root cause analysis visible (mención de imagen inválida)
  - [X] Recommended actions listadas
  - [X] Botón "Execute" visible en al menos una acción (`fix_container_image`)

### 3.5 Ejecutar remediación (1-Click) ✅

- [X] Click en el botón de ejecutar la acción `fix_container_image`
- [X] Spinner de loading aparece mientras se procesa
- [X] Banner de éxito aparece ("completed" o mensaje similar)

Verificar pod recuperado:

```bash
kubectl get pods -n chaos-validation
```

- [X] Pod transiciona a `Running` en <30 segundos
- [X] Imagen del contenedor ahora es `nginx:1.27-alpine`:
  ```bash
  kubectl get pod -n chaos-validation -l app=golden-path-app -o jsonpath='{.items[0].spec.containers[0].image}'
  ```

### 3.6 Verificar estado post-remediación ✅

- [X] La card desaparece del Dashboard en el siguiente ciclo de poll (~5s)
- [X] O el status de la card cambia a `REMEDIATED`
- [X] API confirma que ya no aparece en activos:
  ```bash
  curl -s http://localhost:8082/api/v1/analyses | jq 'length'
  # Debe ser 0 o no contener la entrada golden-path remediada
  ```

---

## 4. Dismiss Flow — Descarte de diagnósticos

### 4.1 Generar otro diagnóstico (si no hay uno activo)

```bash
kubectl delete -f deployments/chaos/golden-path-deployment.yaml --ignore-not-found
kubectl apply -f deployments/chaos/golden-path-deployment.yaml
```

Esperar ~30 segundos hasta que aparezca una nueva card en el Dashboard.

- [X] Nueva card de diagnóstico visible en http://localhost:3000

### 4.2 Dismiss desde la UI ✅

- [X] Click en botón "Dismiss" de la card
- [X] (Opcional) Escribir razón: `"Test dismiss — pod self-healed"`
- [X] Confirmar dismiss
- [X] Card desaparece del Dashboard con animación
- [ ] API ya no la devuelve como activa:
  ```bash
  curl -s http://localhost:8082/api/v1/analyses | jq .
  # No contiene la entrada dismissed
  ```

### 4.3 Verificar idempotencia del dismiss ✅

Obtener el ID del análisis descartado (de la respuesta anterior o de OpenSearch):

```bash
# Usar un ID conocido del dismiss anterior
curl -s -X POST http://localhost:8082/api/v1/analyses/{id}/dismiss \
  -H "Content-Type: application/json" \
  -w "\n%{http_code}"
```

- [X] HTTP 409
- [X] Body RFC 7807: `"title": "Analysis Already Resolved"`

---

## 5. Audit Log — Histórico de resoluciones

### 5.1 Verificar tab Audit Log en la UI ✅

- [X] Click en "Audit Log" en el sidebar
- [X] La vista cambia a la tabla del histórico
- [X] Si hay entries: tabla muestra columnas (Resolved At, Pod Name, Verdict, Action, LLM Model)
- [X] Si está vacío: muestra mensaje "No resolved analyses yet" (o similar)

### 5.2 Verificar datos en la tabla ✅

- [X] Las entries de remediation/dismiss anteriores aparecen
- [X] Status `REMEDIATED` tiene badge verde
- [X] Status `DISMISSED` tiene badge ámbar/naranja
- [X] Columna "LLM Model" muestra el nombre del modelo (no `"unknown"`, salvo fallback)
- [X] Timestamps ordenados de más reciente a más antiguo

### 5.3 Verificar paginación ✅
 
- [X] Si hay más de 20 entries: botón "Next" habilitado
- [X] Botón "Previous" deshabilitado en la primera página
- [X] Indicador "Page X of Y" muestra valores correctos
- [X] Navegar next/previous funciona correctamente y carga datos nuevos

### 5.4 Verificar API directamente ✅

```bash
# Paginación explícita
curl -s "http://localhost:8082/api/v1/analyses/history?page=0&size=5" | jq .

# Verificar que solo contiene REMEDIATED y DISMISSED (no PENDING)
curl -s http://localhost:8082/api/v1/analyses/history | jq '.content[].status'
```

- [X] Todos los status son `"REMEDIATED"` o `"DISMISSED"`
- [X] Campo `resolvedAt` presente en todos los entries
- [X] Campo `modelUsed` presente en todos los entries

---

## 6. Resiliencia — Degradación controlada

### 6.1 Circuit Breaker — AI Provider caído ✅

```bash
# Parar Ollama (simula provider down)
systemctl stop ollama
# O si Ollama corre en Docker:
# docker stop ollama
```

- [X] Inyectar otro evento de chaos (recrear deployment golden-path)
- [X] Esperar ~30s
- [X] Diagnóstico aparece con verdict: `DEGRADED`
- [X] Campo `modelUsed` = `"unknown"`
- [X] La UI muestra la card con status DEGRADED

Restaurar:

```bash
systemctl start ollama
# Esperar a que cargue el modelo (~10s)
```

- [X] El siguiente evento de chaos produce un diagnóstico real con verdict correcto

### 6.2 MCP Server caído — Degradación de contexto ✅

```bash
docker stop mcp-server
```

- [X] Inyectar evento de chaos
- [X] Esperar ~30s
- [X] Diagnóstico se genera SIN enriquecimiento MCP
- [X] El diagnóstico aún es útil (basado solo en el evento Kafka + historia)

Restaurar:

```bash
docker start mcp-server
```

- [X] Esperar a healthcheck (~20s)
- [X] Siguiente diagnóstico incluye contexto MCP completo

---

## 7. Cleanup — Limpieza del entorno ✅

```bash
# Borrar recursos de chaos en Kubernetes
kubectl delete -f deployments/chaos/golden-path-deployment.yaml --ignore-not-found
kubectl delete ns chaos-validation --ignore-not-found

# Parar stack Docker Compose
docker compose -f deployments/docker-compose.yaml down -v

# Verificar que no quedan contenedores huérfanos
docker ps -a | grep -E "kafka|ai-analyzer|observability-ui|opensearch|mcp-server|k8s-collector"
```

- [X] Ningún contenedor de la plataforma sigue corriendo
- [X] Volúmenes eliminados (flag `-v`)

---

## Resumen de Criterios de Aceptación

| Área | Checks | Crítico? |
|------|--------|----------|
| Infraestructura (Stack arranca) | 7 checks | ✅ Blocker |
| API Contract | 6 checks | ✅ Blocker |
| Golden Path (pipeline completo) | 14 checks | ✅ Blocker |
| Dismiss Flow | 4 checks | ✅ Blocker |
| Audit Log History | 8 checks | ✅ Blocker |
| Resiliencia | 6 checks | ⚠️ Importante |
| **TOTAL** | **45 checks** | |

---

## Notas Operativas

- **Tiempos de espera del LLM:** La latencia varía según el modelo y hardware. Con `llama3.1:8b` en GPU espera ~15-30s por diagnóstico. En CPU puede ser 60-120s.
- **Primer arranque:** La primera ejecución de `docker compose up --build` descarga imágenes base (~2GB) y compila los servicios. Puede tardar 5-10 minutos.
- **OpenSearch indexing:** Tras una operación de escritura, puede haber ~1s de delay antes de que aparezca en queries (refresh interval por defecto).
- **MCP_MODE:** El docker-compose usa `MCP_MODE=live`. Si no hay cluster K8s accesible desde los containers, cambiar a `MCP_MODE=mock` en el archivo `.env`.
- **Puertos ocupados:** Si algún puerto está en uso, verificar con `lsof -i :3000` y liberar antes de iniciar.
- **Logs de debug:** Para diagnósticos más detallados, usar `docker logs <container> --follow` en una terminal separada.
