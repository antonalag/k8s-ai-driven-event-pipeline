#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Seed Kafka Events Script
# Publishes 3 synthetic KubernetesEvent JSON messages to the k8s-pod-events topic.
# Used by the E2E pipeline to simulate pod failure events for AI analysis.
# =============================================================================

KAFKA_CONTAINER="kafka"
TOPIC="k8s-pod-events"
BOOTSTRAP="kafka:29092"

# ---------------------------------------------------------------------------
# Pre-check: Validate the kafka container is running and healthy
# ---------------------------------------------------------------------------
if ! docker inspect --format='{{.State.Health.Status}}' "$KAFKA_CONTAINER" 2>/dev/null | grep -q "healthy"; then
  echo "ERROR: Kafka container '$KAFKA_CONTAINER' is not healthy or not running." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Define synthetic events
# ---------------------------------------------------------------------------
EVENT_1='{"podName":"crashloop-pod-e2e-001","namespace":"chaos-validation","status":"Failed","timestamp":"2025-07-31T10:00:00Z"}'
EVENT_2='{"podName":"oomkilled-pod-e2e-002","namespace":"chaos-validation","status":"Failed","timestamp":"2025-07-31T10:01:00Z"}'
EVENT_3='{"podName":"imagepull-pod-e2e-003","namespace":"chaos-validation","status":"Failed","timestamp":"2025-07-31T10:02:00Z"}'

# ---------------------------------------------------------------------------
# Publish events to Kafka
# ---------------------------------------------------------------------------
echo "$EVENT_1" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC"
echo "[SEED] Published event for pod: crashloop-pod-e2e-001"

echo "$EVENT_2" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC"
echo "[SEED] Published event for pod: oomkilled-pod-e2e-002"

echo "$EVENT_3" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC"
echo "[SEED] Published event for pod: imagepull-pod-e2e-003"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "[SEED] ✓ All 3 events published to topic '$TOPIC'"
