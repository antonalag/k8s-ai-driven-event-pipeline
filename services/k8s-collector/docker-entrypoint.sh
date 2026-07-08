#!/bin/sh
# ──────────────────────────────────────────────────────────────────────────────
# k8s-collector Docker entrypoint
# Rewrites kubeconfig server addresses to be reachable from inside Docker.
# ──────────────────────────────────────────────────────────────────────────────
set -e

KUBE_SRC="/home/appuser/.kube/config"
KUBE_DST="/tmp/.kube/config"

if [ -f "$KUBE_SRC" ]; then
  # Only rewrite kubeconfig in local/dev environments
  if [ "${ENVIRONMENT:-local}" != "local" ]; then
    echo "k8s-collector: ENVIRONMENT=$ENVIRONMENT — using kubeconfig as-is (no rewrite)"
    export KUBECONFIG="$KUBE_SRC"
  else
    mkdir -p /tmp/.kube
    sed -e 's|https://0\.0\.0\.0:|https://host.docker.internal:|g' \
        -e 's|https://127\.0\.0\.1:|https://host.docker.internal:|g' \
        -e 's|https://localhost:|https://host.docker.internal:|g' \
        -e '/certificate-authority-data:/d' \
        -e '/server:/a\    insecure-skip-tls-verify: true' \
        "$KUBE_SRC" > "$KUBE_DST"
    export KUBECONFIG="$KUBE_DST"
    echo "k8s-collector: kubeconfig rewritten for Docker networking → $KUBE_DST"
  fi
else
  echo "k8s-collector: WARNING — no kubeconfig found at $KUBE_SRC"
  echo "k8s-collector: The Informer will fail to connect to the K8s API."
fi

exec java -jar /app/app.jar "$@"
