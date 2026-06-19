# ══════════════════════════════════════════════════════════════════════════════
# k8s-ai-driven-event-pipeline — Developer Makefile
# ══════════════════════════════════════════════════════════════════════════════

.DEFAULT_GOAL := help

COMPOSE_FILE := deployments/docker-compose.yaml
BOOTSTRAP    := scripts/bootstrap.sh

# ──────────────────────────────────────────────────────────────────────────────
# Targets
# ──────────────────────────────────────────────────────────────────────────────

.PHONY: init check up down test test-backend test-mcp test-ui build clean help

## init: Pre-flight checks + build + launch all services
init: $(BOOTSTRAP)
	@bash $(BOOTSTRAP)

## check: Run pre-flight checks only (no launch)
check: $(BOOTSTRAP)
	@bash $(BOOTSTRAP) --check

## up: Start services (skip pre-flight — assumes already validated)
up:
	docker compose -f $(COMPOSE_FILE) --env-file .env up --build -d

## down: Stop and remove all containers and volumes
down:
	docker compose -f $(COMPOSE_FILE) down -v --remove-orphans

## test: Run all test suites (backend + mcp + ui)
test: test-backend test-mcp test-ui

## test-backend: Run Java/Gradle tests
test-backend:
	./gradlew clean test --no-daemon

## test-mcp: Run MCP Server TypeScript tests
test-mcp:
	cd services/mcp-server && npm run build && npm test

## test-ui: Run UI typecheck + lint + tests + build
test-ui:
	cd ui && npm run typecheck && npm run lint && npm run test && npm run build

## build: Build all containers without starting
build:
	docker compose -f $(COMPOSE_FILE) --env-file .env build

## clean: Remove containers, volumes, and build artifacts
clean: down
	./gradlew clean --no-daemon 2>/dev/null || true
	rm -rf ui/dist services/mcp-server/dist

## help: Show this help
help:
	@echo ""
	@echo "  k8s-ai-driven-event-pipeline"
	@echo "  ────────────────────────────────────────"
	@echo ""
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/## /  /' | sort
	@echo ""
