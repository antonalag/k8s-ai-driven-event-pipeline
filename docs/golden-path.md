# Golden Path — Demo Scenario

## Overview

This document defines the exact technical script for the platform's "happy path" demo.
It exercises the full pipeline end-to-end: chaos injection → event detection → AI diagnosis → 1-click remediation → pod recovery.

---

## 1. Chaos Scenario Definition

| Property | Value |
|----------|-------|
| **Namespace** | `chaos-validation` |
| **Resource Type** | `Deployment` (required for mutation tools) |
| **Deployment Name** | `golden-path-app` |
| **Container Name** | `app` |
| **Failure Mode** | `ImagePullBackOff` via invalid image reference |
| **Broken Image** | `docker.io/library/nginx:99.99.99-nonexistent` |
| **Correct Image** | `docker.io/library/nginx:1.27-alpine` |

### Why ImagePullBackOff?

- Produces a clear, deterministic failure visible within seconds.
- The `fix_container_image` MCP tool provides an instant, verifiable fix.
- Recovery is immediate — no restart delay, no OOM ambiguity.
- Visual confirmation in the Observability UI: pod transitions from red to green in one action.

---

## 2. Pipeline Trace (Technical Chain)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 1: Chaos Injection                                                     │
│  kubectl apply -f deployments/chaos/golden-path-deployment.yaml              │
│  Result: Pod enters ImagePullBackOff → K8s emits Warning events              │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 2: Event Ingestion (k8s-collector)                                     │
│  Informer detects Pod status = Failed                                        │
│  Publishes KubernetesEvent to Kafka topic: k8s-pod-events                    │
│  Key: "golden-path-app-xxxxx" (pod name)                                     │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 3: AI Analysis (ai-analyzer)                                           │
│  PodEventConsumer receives event → status=Failed → routes to analysis        │
│  PodAnalyzerService orchestrates:                                            │
│    a) Retrieve history from OpenSearch (previous analyses for this pod)       │
│    b) MCP enrichment: describe_pod → get_events → get_logs                   │
│    c) Prompt construction (3-section: event + history + MCP context)          │
│    d) LLM inference (Ollama/BYOK)                                            │
│    e) Structured AiAnalysis response with recommendedActions                  │
│  Result: verdict=CRITICAL_FAILURE, recommendedActions with fix command        │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 4: Persistence & UI Rendering                                          │
│  AiAnalysisEvent published to Kafka topic: ai-analysis-events                │
│  Persisted to OpenSearch index: ai-analysis-reports                           │
│  Observability UI polls GET /api/v1/analyses → renders diagnosis card               │
│  Card shows: CRITICAL_FAILURE, root cause, recommended fix_container_image    │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 5: 1-Click Remediation                                                 │
│  User clicks [Apply Recommended Fix] on the kubectl set image action         │
│  UI sends POST /api/v1/remediations:                                         │
│    {                                                                          │
│      "action": "fix_container_image",                                         │
│      "deploymentName": "golden-path-app",                                     │
│      "namespace": "chaos-validation",                                         │
│      "containerName": "app",                                                  │
│      "correctImage": "docker.io/library/nginx:1.27-alpine",                   │
│      "correlationId": "<uuid>"                                                │
│    }                                                                          │
│  Backend → RemediationOrchestrator → MCP Server → kubectl patch              │
│  Result: Deployment image updated → new ReplicaSet → Pod Running              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Remediation Tool Selection

| Tool | Used In Golden Path? | Reason |
|------|---------------------|--------|
| `fix_container_image` | **YES** | Directly addresses ImagePullBackOff by patching the correct image |
| `restart_deployment` | No | Only useful for CrashLoopBackOff with transient config issues |
| `scale_deployment` | No | Doesn't fix the root cause of an image pull failure |

### Expected MCP Mutation Payload

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": "1",
  "params": {
    "name": "fix_container_image",
    "arguments": {
      "deploymentName": "golden-path-app",
      "namespace": "chaos-validation",
      "containerName": "app",
      "correctImage": "docker.io/library/nginx:1.27-alpine",
      "correlationId": "550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```

---

## 4. Success Criteria (Demo Verification)

| # | Check | Expected State |
|---|-------|----------------|
| 1 | Pod status after chaos injection | `ImagePullBackOff` or `ErrImagePull` |
| 2 | Kafka topic `k8s-pod-events` receives event | Message with status=Failed |
| 3 | AI diagnosis in Observability UI | verdict=CRITICAL_FAILURE with image fix recommendation |
| 4 | MCP enrichment visible | `mcpToolsUsed: [describe_pod, get_events, get_logs]` |
| 5 | Execute button renders | Parseable action → enabled button |
| 6 | Post-remediation pod status | `Running` within 30 seconds |
| 7 | UI updates automatically | Next poll cycle shows HEALTHY or no longer critical |

---

## 5. Demo Commands (Quick Reference)

```bash
# Pre-requisite: platform running
make init

# Inject chaos
kubectl apply -f deployments/chaos/golden-path-deployment.yaml

# Observe failure
kubectl get pods -n chaos-validation -w

# Open Observability UI
open http://localhost:3000

# After clicking [Apply Recommended Fix] in UI, verify recovery:
kubectl get pods -n chaos-validation

# Clean up
kubectl delete -f deployments/chaos/golden-path-deployment.yaml
```

---

## 6. Timing Expectations

| Phase | Expected Duration |
|-------|-------------------|
| Chaos → Pod enters ImagePullBackOff | ~5-10 seconds |
| Event reaches Kafka | ~2 seconds |
| AI diagnosis completes | ~10-30 seconds (model-dependent) |
| UI renders diagnosis | ~5 seconds (next poll cycle) |
| Remediation → Pod Running | ~10-20 seconds |
| **Total demo time** | **~45-90 seconds** |
