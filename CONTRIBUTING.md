# Contributing to k8s-ai-driven-event-pipeline

First off, thanks for taking the time to contribute! This guide will help you
get started.

## Prerequisites

- Java 21 (Temurin recommended)
- Node.js 20+
- Docker & Docker Compose v2
- Gradle 8.x (wrapper included)
- A running Kubernetes cluster (minikube/kind for local dev)

## Local Setup

```bash
# Clone the repository
git clone https://github.com/antonalag/k8s-ai-driven-event-pipeline.git
cd k8s-ai-driven-event-pipeline

# Run pre-flight checks, build, and launch all services
make init

# Or run tests only
make test
```

## Project Structure

| Directory | Description |
|-----------|-------------|
| `services/ai-analyzer` | Spring Boot AI analysis service (Java 21) |
| `services/k8s-collector` | Kubernetes event ingestion service |
| `services/mcp-server` | MCP Server (Node.js/TypeScript) |
| `ui/` | React + TypeScript Observability Dashboard |
| `deployments/` | Docker Compose, K8s manifests, chaos scenarios |
| `specs/` | JSON Schema and OpenAPI contracts |
| `docs/` | Architecture and specification documents |

## Available Make Targets

| Command | Description |
|---------|-------------|
| `make init` | Pre-flight checks + build + launch all services |
| `make test` | Run all test suites (backend + mcp + ui) |
| `make test-backend` | Run Java/Gradle tests only |
| `make test-mcp` | Run MCP Server TypeScript tests |
| `make test-ui` | Run UI typecheck + lint + tests + build |
| `make up` | Start services (skip pre-flight) |
| `make down` | Stop and remove all containers and volumes |
| `make clean` | Remove containers, volumes, and build artifacts |

## Branching Strategy

- **Main branch:** `main` — always deployable
- **Feature branches:** `feature/<short-description>`
- **Fix branches:** `fix/<short-description>`
- **All changes go through Pull Requests** — direct pushes to `main` are not allowed

## Commit Conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Usage |
|--------|-------|
| `feat:` | A new feature |
| `fix:` | A bug fix |
| `docs:` | Documentation only changes |
| `chore:` | Maintenance tasks (deps, CI, config) |
| `refactor:` | Code change that neither fixes a bug nor adds a feature |
| `test:` | Adding or correcting tests |

Example: `feat: add audit log pagination endpoint`

## Pull Request Process

1. Create a feature branch from `main`
2. Make your changes following the coding style below
3. Ensure all tests pass: `make test`
4. Push your branch and open a PR against `main`
5. Fill in the PR template (summary, type, testing, checklist)
6. Wait for CI to pass and request a review

## Coding Style

- **Architecture:** Clean Architecture with hexagonal layering (domain → service → infrastructure)
- **API Design:** Contract-First — schemas and OpenAPI specs are defined before implementation
- **Error Handling:** RFC 7807 Problem Details for all REST error responses
- **Backend:** Java 21, Spring Boot 3.5, no framework annotations in domain layer
- **Frontend:** Strict TypeScript (`noImplicitAny: true`), React + TanStack Query
- **Testing:** JUnit 5 + jqwik (PBT) for backend, Vitest + fast-check for frontend

## Running Tests

```bash
# All tests
make test

# Backend only (Java/Gradle)
make test-backend

# MCP Server only (TypeScript)
make test-mcp

# UI only (typecheck + lint + unit tests + build)
make test-ui
```

## Code of Conduct

This project follows the [Contributor Covenant v2.1](CODE_OF_CONDUCT.md).
Please read it before participating.

## Questions?

Open an issue or reach out at **anjoalDev@gmail.com**.
