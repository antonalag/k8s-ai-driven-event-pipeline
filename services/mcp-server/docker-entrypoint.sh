#!/bin/sh
# ──────────────────────────────────────────────────────────────────────────────
# MCP Server Docker entrypoint
# Rewrites kubeconfig server addresses to be reachable from inside Docker.
# Required for MCP_MODE=live to connect to the K8s API.
# ──────────────────────────────────────────────────────────────────────────────
set -e

KUBE_SRC="/home/node/.kube/config"
KUBE_DST="/tmp/.kube/config"

if [ -f "$KUBE_SRC" ]; then
  mkdir -p /tmp/.kube
  # Rewrite 0.0.0.0 and 127.0.0.1 to host.docker.internal
  # Add insecure-skip-tls-verify because k3d certs don't include host.docker.internal as SAN
  sed -e 's|https://0\.0\.0\.0:|https://host.docker.internal:|g' \
      -e 's|https://127\.0\.0\.1:|https://host.docker.internal:|g' \
      -e 's|https://localhost:|https://host.docker.internal:|g' \
      -e '/certificate-authority-data:/d' \
      -e '/server:/a\    insecure-skip-tls-verify: true' \
      "$KUBE_SRC" > "$KUBE_DST"
  export KUBECONFIG="$KUBE_DST"
  # Node.js fetch needs this to skip TLS verification for self-signed k3d certs
  export NODE_TLS_REJECT_UNAUTHORIZED=0
  echo "mcp-server: kubeconfig rewritten for Docker networking → $KUBE_DST"
else
  if [ "$MCP_MODE" = "live" ]; then
    echo "mcp-server: WARNING — MCP_MODE=live but no kubeconfig found at $KUBE_SRC"
    echo "mcp-server: Read tools will fail. Falling back to error responses."
  fi
fi

exec node dist/index.js "$@"
