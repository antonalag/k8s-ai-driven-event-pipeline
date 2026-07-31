# ══════════════════════════════════════════════════════════════════════════════
# k8s-ai-driven-event-pipeline — Developer Makefile
# ══════════════════════════════════════════════════════════════════════════════

.DEFAULT_GOAL := help

COMPOSE_FILE := deployments/docker-compose.yaml
BOOTSTRAP    := scripts/bootstrap.sh
E2E_COMPOSE  := -f $(COMPOSE_FILE) -f deployments/docker-compose.e2e.yaml
E2E_WAIT     ?= 15
SEED_SCRIPT  := scripts/seed-kafka-events.sh

# ──────────────────────────────────────────────────────────────────────────────
# Targets
# ──────────────────────────────────────────────────────────────────────────────

.PHONY: init check up down test test-backend test-mcp test-ui build clean test-e2e help

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

## test-e2e: Run full E2E pipeline (start stack, seed, Playwright, cleanup)
test-e2e:
	@echo "══════════════════════════════════════════════════════════"
	@echo " E2E Pipeline — Starting Docker Compose stack..."
	@echo "══════════════════════════════════════════════════════════"
	@docker compose $(E2E_COMPOSE) --env-file .env up --build -d
	@echo ""
	@echo "⏳ Waiting for services to become healthy..."
	@sleep 10
	@echo ""
	@echo "🌱 Seeding Kafka with synthetic events..."
	@bash $(SEED_SCRIPT)
	@echo ""
	@echo "⏳ Waiting $(E2E_WAIT)s for pipeline to process events..."
	@sleep $(E2E_WAIT)
	@echo ""
	@echo "🎭 Running Playwright E2E tests..."
	@cd ui && npx playwright test || (echo "❌ E2E tests failed" && cd .. && docker compose $(E2E_COMPOSE) down -v --remove-orphans && exit 1)
	@echo ""
	@echo "🧹 Cleaning up Docker Compose stack..."
	@docker compose $(E2E_COMPOSE) down -v --remove-orphans
	@echo ""
	@echo "✅ E2E Pipeline completed successfully!"

## help: Show this help
help:
	@echo ""
	@echo "  k8s-ai-driven-event-pipeline"
	@echo "  ────────────────────────────────────────"
	@echo ""
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/## /  /' | sort
	@echo ""
