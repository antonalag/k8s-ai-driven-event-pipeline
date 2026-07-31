# ──────────────────────────────────────────────────────────────────────────────
# Mock AI Server — Minimal Dockerfile
#
# Zero-dependency Node.js HTTP server that simulates an OpenAI-compatible
# endpoint for E2E testing. Returns deterministic AiAnalysis responses
# based on keywords in the request prompt.
#
# Security: Non-root execution (UID 1000:1000), SHA-256 pinned base image
# ──────────────────────────────────────────────────────────────────────────────

FROM node:20-alpine@sha256:dd75a9e8995e7f9d83f64af16d07c1edbc97139b08246ed8cb7f5ea1d28c726d

WORKDIR /app

# Copy only the server file (zero dependencies — no package.json needed)
COPY server.js ./

# Non-root user
USER 1000:1000

EXPOSE 8090

ENV MOCK_AI_PORT=8090

HEALTHCHECK --interval=5s --timeout=3s --retries=3 --start-period=5s \
    CMD wget --spider -q http://127.0.0.1:8090/health || exit 1

ENTRYPOINT ["node", "server.js"]
