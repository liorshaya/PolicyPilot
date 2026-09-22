# PolicyPilot: the one command after `docker compose up` is `make up` (Work Plan day 1).
# Run `make` to list the targets.
SHELL := /bin/bash
.DEFAULT_GOAL := help
COMPOSE := docker compose
COMPOSE_OLLAMA := docker compose --profile ollama -f docker-compose.yml -f docker-compose.ollama.yml

.PHONY: help up up-ollama down clean-data logs ps build test test-backend test-frontend lint e2e check hooks

help: ## List the targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Start the database, the backend and the frontend, then wait for the health check
	$(COMPOSE) up --build --detach
	scripts/wait-for-health.sh
	@echo ""
	@echo "  backend   http://localhost:8080/actuator/health   (API docs at /api/docs, behind the access code)"
	@echo "  frontend  http://localhost:5173"
	@echo "  database  localhost:5432  policypilot / policypilot"

up-ollama: ## The same with a local model: Ollama plus qwen3:14b and bge-m3, backend on the ollama profile
	$(COMPOSE_OLLAMA) up --build --detach
	scripts/wait-for-health.sh
	@echo ""
	@echo "  ollama    http://localhost:11434   (the models download on first start; see: make logs)"
	@echo "  backend   http://localhost:8080/actuator/health"
	@echo "  frontend  http://localhost:5173"

down: ## Stop everything (data volumes are kept)
	$(COMPOSE_OLLAMA) down --remove-orphans

clean-data: ## Stop everything AND delete the database, model and node_modules volumes
	$(COMPOSE_OLLAMA) down --remove-orphans --volumes

logs: ## Follow the logs of every service
	$(COMPOSE_OLLAMA) logs --follow --tail=100

ps: ## Show the running services
	$(COMPOSE_OLLAMA) ps

build: ## Compile and package both applications without the integration tests
	cd backend && ./mvnw -B -ntp verify -DskipITs
	cd frontend && npm ci && npm run build

test: test-backend test-frontend ## Run every test: backend (unit + integration on Testcontainers) and frontend

test-backend: ## Backend: ./mvnw verify (needs Docker for the Testcontainers database)
	cd backend && ./mvnw -B -ntp verify

test-frontend: ## Frontend: type check and Vitest with coverage
	cd frontend && npm ci && npm run typecheck && npm run test:coverage

lint: ## Frontend ESLint and Prettier
	cd frontend && npm run lint && npm run format:check

e2e: ## Frontend Playwright (starts the dev server itself)
	cd frontend && npm run e2e

check: ## The CI hygiene checks and the Python reference self-test, exactly as stage 1 and 2 run them
	python3 scripts/ci/check_fixture_privacy.py
	scripts/ci/check_schema_copies.sh
	scripts/ci/check_generated_client.sh
	python3 scripts/ci/check_readme_limitations.py
	python3 fixtures/reference/reference_check.py | tail -n 1 | grep -qx 'ALL OK' && echo "reference self-test: ALL OK"
	python3 fixtures/tools/generate_cases.py >/dev/null && git diff --exit-code --stat -- fixtures/ && echo "generator: no diff"

hooks: ## Install the git pre-commit hook (gitleaks on staged changes)
	git config core.hooksPath scripts/git-hooks
	@echo "pre-commit hook installed; it needs gitleaks (brew install gitleaks)"
