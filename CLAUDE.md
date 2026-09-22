# CLAUDE.md

PolicyPilot is an AI copilot over a deterministic rules engine, built for a technical interview at ESI Labs.
A model authors rules from Hebrew policy text, reviews them against the text, answers questions with citations
and proposes changes; a deterministic Java engine makes every decision and records a full trace; a person
approves every policy change. **The model proposes and explains, the rules engine decides, a person approves.**
No decision ever passes through a model.

## Quality bar

Tidy and high quality is the baseline, not a stretch goal: every file has one obvious place, every name follows
the convention next to it, every version is pinned and every value traces to a document, and every change ships
with its tests. Leave every folder you touch tidier than you found it.

## Repository layout (Document 2, Deployment Topology)

| Path | Contents |
| --- | --- |
| `backend/` | Maven project: Java 21, Spring Boot 4.0.x, Spring AI 2.0.x; `Dockerfile`; `railway.json` (deploy settings the CI deploy job applies); Flyway migrations; prompts from day 7 |
| `frontend/` | Vite project: React 19, TypeScript strict, TanStack Query; `vercel.json` with the SPA rewrite |
| `fixtures/` | The DSL schema, the demo policy with its rule set and 200 cases, the conformance suite, the evaluation set, the Python reference |
| `docs/` | Documents 1 to 7, `progress-checklist.md`, `worklog.md`, `agent-briefing.he.md`; `quality/` and `eval/` reports; `demo/` (a screenshot per demo step) and `assets/` (the README's images) |
| `docker-compose.yml` | `db` (pgvector), `backend`, `frontend`; `ollama` and `ollama-pull` behind `--profile ollama` (`docker-compose.ollama.yml`) |
| `.github/workflows/` | `ci.yml` (the eight stages, then the Railway deploy of the stage 6 digest), `eval.yml` (manual live evaluation), `compose-smoke.yml` (`make up` timed on a clean runner) |
| `scripts/` | CI hygiene checks and the job summary, the Ollama pull script, the git pre-commit hook, `wait-for-health.sh` |
| `Makefile`, `RUNBOOK.md` | `make up` is the one command after `docker compose up`; the runbook is the owner's operating guide, in Hebrew |

Folder names: the two projects are `backend/` and `frontend/`. The documents called them `api/` and `web/` until
the living documents were renamed on 2026-09-19. The Java package `web` keeps its name: it is the HTTP layer of the
backend, not the frontend.

## Backend packages (Document 2, Backend Module Structure)

Twelve packages under `com.liorshaya.policypilot`, dependencies allowed inward only, enforced by
`backend/src/test/java/.../architecture/PackageRulesTest.java`:

| Package | May depend on |
| --- | --- |
| `rules` | JDK, Jackson (the schema validator and RE2J are allowed as well) |
| `engine` | `rules` |
| `policy` | `rules`, persistence |
| `ruleset` | `rules`, `engine`, `policy`, `audit`, persistence |
| `decision` | `ruleset`, `engine`, `rules`, persistence |
| `ai` | `rules`, `engine`, `policy`, `ruleset`, `decision`, `rag`, persistence |
| `ai.adapter` | Spring AI, `ai` interfaces, persistence; **the only package that imports `org.springframework.ai`** |
| `rag` | `policy`, `rules`, `ruleset`, `ai` (the `EmbeddingGateway` interface), persistence |
| `change` | `ai`, `ruleset`, `engine`, `decision`, `audit` |
| `audit` | persistence |
| `demo` | `policy`, `ruleset`, `decision`, `audit`, persistence |
| `web` | every package above; **nothing depends on `web`** |

The `ruleset` package (rule sets, their versions, publishing) was added on 2026-09-20 (Document 2 first); on the
same day `ai` and `ai.adapter` gained persistence, because Document 2 places the model call ledger, the token
ledger and the response cache in them. On 2026-09-21 `rag` gained `ruleset` (day 8): the published version to chunk
and its `embedding_status` belong to `ruleset`, and `rag` reads them through its service, not its tables.
On 2026-09-22 `ai.chat` joined `ai` (day 9): the chat use case, which reads and writes the database and is gated with
the orchestrating modules; what of the chat needs no database is `ai.service.chat`, on the unit-test gate.
Two cross-cutting packages were added on 2026-09-18: `config` (Spring configuration, `PolicyPilotProperties`)
and `common` (dependency-free helpers). Every module may use them except `rules` and `engine`, which stay pure.
Inside a module that owns state the layers are `service` (entry points other modules call), `entity` and
`repository` (private to the module). The HTTP layer is `web.controller`, `web.request`, `web.response`,
`web.error`, `web.security`, `web.validation`. Adding an arrow means changing Document 2 first, then the test.

## Commands

```
make up                                   docker compose up --build, then wait for /actuator/health
make test                                 backend ./mvnw verify (Testcontainers) + frontend Vitest with coverage
make check                                CI stage 1 hygiene checks + reference self-test + generator diff
cd backend && ./mvnw verify               unit, architecture, integration tests and the JaCoCo gates
cd frontend && npm test                   Vitest; npm run lint; npm run e2e (Playwright)
python3 fixtures/reference/reference_check.py     must end with ALL OK
python3 fixtures/tools/generate_cases.py          must produce no diff
gh run list --limit 10                            recent CI runs; gh run view <id> --log-failed for the failing step
```

## Working rules (Document 6, Working Method and Definition of Done)

- Tests are listed before the code: every task starts by turning its specification rows into named, empty,
  failing tests; then red, green, refactor. About 40% of a task is its tests and fixtures.
- Expected values come from outside the code: the reference implementation and the fixtures for the engine and
  the validator, the OpenAPI document for the API, recordings and the labeled set for the AI layer. A test that
  copies its expected value from running the code is rejected.
- Every generated test is interrogated before it is kept: it asserts a specific value from the specification,
  it would fail if the behavior were wrong, and it does not mock the thing under test.
- No test depends on the network, the clock, the locale or test order; model calls in tests go through the
  `RecordedGateway`; the database, the validator and the engine are always real.
- A pull request merges to `main` only with CI stages 1 to 6 green (7 when `frontend/` or the API contract
  changed); a red pipeline is fixed before any new test is written. Coverage thresholds are never lowered to
  pass; PIT on `engine` and `rules` becomes a hard gate on day 3 (`pitest.failWhenNoMutations` in the pom).
- No secrets anywhere in the repository: `.env` is ignored, `.env.example` lists names only, gitleaks runs in
  CI and as the pre-commit hook (`make hooks`). Stage 1 scans every branch it fetches: a secret on any branch turns
  stage 1 red for every run until that branch or commit is gone. `main` is protected: pull requests only.
- CI stage 6 loads the image, scans it with Trivy and pushes only an image that passed. A High or Critical
  finding with a fix is resolved by moving the library to the fixed version, never by relaxing the scan.
- Dependabot (`.github/dependabot.yml`) opens grouped weekly pull requests inside the documented version lines
  only. Leaving a line (Java 21, Spring Boot 4.0.x, Spring AI 2.0.x, a library major) is a document change first.

## What never changes without changing the document first

Package names, routes, validator codes, thresholds, rule ids, fixture paths, environment variable names, day
numbers. `fixtures/` is committed as delivered and never edited to make a test pass; when the reference and
the Java disagree, Document 3 decides and the fix lands in code, reference and fixture together. When a
document is silent or two documents disagree, stop and ask instead of guessing.

## Languages

Code, comments, tests, commit messages, this file, the worklog and the documents are in English. Only policy
text, demo questions and the model's free-text output are in Hebrew. `RUNBOOK.md` and
`docs/agent-briefing.he.md` are written for the owner and are in Hebrew on purpose.

## Daily routine (Document 7, Daily routine and tracking)

1. First fifteen minutes: read yesterday's `docs/worklog.md` line; slipped items come first.
2. List the day's tests from its specification rows as named, empty, failing tests; that list is the scope.
3. Fixtures before code; expected values from the reference, the OpenAPI document, recordings or the labeled set.
4. Red, green, refactor per test; merge only green; `main` deploys itself.
5. Last hour: Definition of Done for the day's tasks, the demo-step check on the cloud site, the worklog line:
   `date | day N | done | slipped | decisions | coverage per package, PIT score, batch time, first-token time`.

## Schedule (decided 2026-09-18)

The interview is on Monday 2026-10-05, so the plan runs as the two-week version of Document 7 (Scope ladder). Day 1
is done; days 2 to 9 keep their Work Plan content, except that day 9 builds only the `decision` and `simulate` tools;
day 10 (Sun Oct 4) is rehearsal, video, README and freeze. Rungs 2 to 10 and the evaluation runner are cut: do not
build the reviewer, explain, the guided panel, the `stats` and `rules` tools, the change flow, the resets or the
evaluation runner unless the owner adds one back. Dates and struck-through boxes: `docs/progress-checklist.md`.

## Where to look

| Question | Document and section |
| --- | --- |
| What to build today, its tests, the gate | `docs/07-work-plan.md`, the day's row; `docs/progress-checklist.md` |
| DSL semantics, validator codes, trace format, conformance | `docs/03-rules-dsl-specification.md` |
| Prompt contracts, repair loop, retrieval, tools, evaluation metrics | `docs/04-ai-pipeline-and-prompts.md` |
| Security controls, injection defenses, limits, red-team fixtures | `docs/05-security-specification.md` |
| Test levels, coverage gates, CI stages, fixtures layout | `docs/06-test-strategy.md` |
| Modules, flows, data model, API routes, profiles, deployment | `docs/02-architecture.md` |
| Scope, demo script, requirements, Definition of Done, glossary | `docs/01-project-brief.md` |
| Fixture file shapes | `fixtures/README.md` |
| How to run, deploy and operate, day-0 accounts | `RUNBOOK.md` (Hebrew) |

## Open values and known tensions (do not guess; ask the owner)

- `policypilot.ai.daily-token-budget` is 400,000 tokens since day 7 (2026-09-20): at the dearest price in the
  lineup, the strong model's output at 12 USD per million, that is 4.80 USD, under the owner's 5 USD a day.
- `backend/pom.xml` overrides `tomcat.version` (11.0.26): Spring Boot 4.0.8 manages Tomcat 11.0.24, which has
  three critical CVEs. Remove the override once a Spring Boot 4.0.x release manages 11.0.25 or later.
