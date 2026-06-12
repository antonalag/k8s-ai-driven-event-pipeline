# ──────────────────────────────────────────────────────────────────────────────
# MCP Server — Multi-Stage Dockerfile (Phase 15 / Milestone 33)
#
# Stage 1: Build with Node 20 Alpine (TypeScript compilation)
# Stage 2: Runtime with minimal Node 20 Alpine (production deps only)
#
# Security: Non-root user (UID 1000:1000), SHA-256 pinned base images
#
# NOTE: Build context is services/mcp-server/
# ──────────────────────────────────────────────────────────────────────────────

# Stage 1: Builder
FROM node:20-alpine@sha256:dd75a9e8995e7f9d83f64af16d07c1edbc97139b08246ed8cb7f5ea1d28c726d AS builder
WORKDIR /app

# Copy package files and install all dependencies (including dev)
COPY package.json package-lock.json* ./
RUN npm ci --ignore-scripts

# Copy source and compile TypeScript
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

# Stage 2: Runtime
FROM node:20-alpine@sha256:dd75a9e8995e7f9d83f64af16d07c1edbc97139b08246ed8cb7f5ea1d28c726d AS runtime
WORKDIR /app

# Copy package files and install production dependencies only
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force

# Copy compiled output from builder
COPY --from=builder /app/dist ./dist

# Non-root user
USER 1000:1000

EXPOSE 3001

ENV MCP_MODE=mock \
    MCP_PORT=3001

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=20s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3001/health || exit 1

CMD ["node", "dist/index.js"]
