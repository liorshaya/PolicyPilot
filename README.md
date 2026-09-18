# PolicyPilot

An AI copilot over a deterministic rules engine. A model authors rules from policy text (Hebrew or English),
reviews them against the text, answers questions with citations and proposes changes; a deterministic Java
engine makes every decision and records a full trace; a person approves every policy change.

**The model proposes and explains, the rules engine decides, a person approves.** No decision ever passes
through a model.

> This README is the repository skeleton's placeholder. Document 8, the README with the architecture diagram,
> the demo script, the known limitations and the recorded run, is written on days 16 to 19 of the work plan.

## Layout

| Path | Contents |
| --- | --- |
| [`backend/`](backend/) | Java 21, Spring Boot 4, Spring AI 2, PostgreSQL 16 with pgvector; one Maven project, one Docker image |
| [`frontend/`](frontend/) | React 19, TypeScript, Vite, TanStack Query; deployed to Vercel |
| [`fixtures/`](fixtures/) | The Rules DSL schema, the demo policy with its rule set and 200 cases, the conformance suite, the evaluation set, the Python reference implementation |
| [`docs/`](docs/) | The seven design documents, the progress checklist and the worklog |
| [`RUNBOOK.md`](RUNBOOK.md) | How to run, test, deploy and operate the project (Hebrew) |
| [`CLAUDE.md`](CLAUDE.md) | The working rules every coding session starts from |

## Quick start

```
cp .env.example .env        # fill in OPENAI_API_KEY (names only are committed)
make up                     # database + backend + frontend, waits for the health check
make test                   # backend (unit + integration on Testcontainers) and frontend tests
make check                  # the CI hygiene checks and the Python reference self-test
```

The backend answers at `http://localhost:8080/actuator/health`, the web app at `http://localhost:5173`.
`make up-ollama` runs the same stack with a local model instead of OpenAI.

## Stack

Java 21 · Spring Boot 4.0 · Spring AI 2.0 · PostgreSQL 16 + pgvector · Flyway · React 19 · TypeScript · Vite ·
TanStack Query · Vitest · Playwright · Docker Compose · GitHub Actions · Railway · Vercel
