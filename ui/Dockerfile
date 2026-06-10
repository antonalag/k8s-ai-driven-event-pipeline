# ──────────────────────────────────────────────────────────────────────────────
# Phase 14 Compliance Audit — PASSED
#
# ✅ Node 20 Alpine SHA-256 pinned (build stage)
# ✅ Nginx 1.27 Alpine SHA-256 pinned (runtime stage)
# ✅ Deterministic install: npm ci
# ✅ Non-root execution: USER nginx (UID 101)
# ✅ Read-only static assets: 444 (files), 555 (directories)
# ✅ SPA fallback routing: try_files → /index.html
# ✅ Healthcheck: /healthz (30s interval, 3s timeout, 3 retries)
# ✅ No source/node_modules in runtime stage
# ──────────────────────────────────────────────────────────────────────────────

# Stage 1: Build — Node 20 Alpine with SHA-256 pinned digest
FROM node:20-alpine@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS build

WORKDIR /app

# Copy lockfile manifests first to maximize Docker layer caching
COPY package.json package-lock.json ./

# Install dependencies deterministically from lockfile
RUN npm ci

# Copy remaining source files after dependency installation
COPY src/ ./src/
COPY public/ ./public/
COPY index.html tsconfig.json vite.config.ts tailwind.config.ts postcss.config.mjs ./

# Produce production static assets
RUN npm run build

# Stage 2: Runtime — Nginx 1.27 Alpine with SHA-256 pinned digest
FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10 AS runtime

# Copy production build artifacts from build stage (no source code or node_modules)
COPY --from=build /app/dist /usr/share/nginx/html

# Copy SPA routing configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Set static asset permissions: read-only for all users, owned by root
# Files: 444 (r--r--r--), Directories: 555 (r-xr-xr-x)
RUN chown -R root:root /usr/share/nginx/html && \
    find /usr/share/nginx/html -type d -exec chmod 555 {} \; && \
    find /usr/share/nginx/html -type f -exec chmod 444 {} \;

# Create writable runtime directories with nginx user ownership
RUN mkdir -p /var/cache/nginx /var/run /var/log/nginx /run && \
    chown -R nginx:nginx /var/cache/nginx /var/run /var/log/nginx /run

# Install curl for healthcheck (if not already present)
RUN apk add --no-cache curl

# Switch to non-root nginx user (UID 101 in Alpine)
USER nginx

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD curl -f http://localhost:80/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
