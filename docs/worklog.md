# PolicyPilot worklog

One line per day (Document 7, Daily routine and tracking): date, day number, what was done, what slipped,
decisions taken, and the numbers copied from the CI job summary (line coverage per package, PIT score, batch
time, first-token time; `n/a` until the number exists). Gate proofs and rehearsal fix lists are added under the
day they belong to. Slipped items open the next morning.

Format of a line:

```
| date | day | done | slipped | decisions | coverage per package | PIT | batch | first token |
```

| Date | Day | Done | Slipped | Decisions | Coverage per package | PIT | Batch | First token |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-09-18 | pre-1 | Repository reorganised into a monorepo (`backend/`, `frontend/`, `fixtures/`, `docs/`); the delivered `docs/` and `fixtures/` zips extracted as canonical, older drafts removed (kept in the first commit); backend skeleton (Spring Boot 4.0.8, Spring AI 2.0.1, Flyway V1 with pgvector, the eleven packages plus `config` and `common`, layer sub-packages, JaCoCo gates, PIT profile, Dockerfile by digest, Maven wrapper 3.9.16); ArchUnit suite (module table, nothing depends on `web`, Spring AI only in `ai.adapter`, forbidden APIs, engine purity); context-loads tests for `openai`, `ollama`, `openai,cloud`; Flyway and pgvector integration test; health endpoint test; `DATABASE_URL` translation for Railway with unit tests; frontend skeleton (React 19.3, TypeScript 6.0 strict, Vite 8.3, TanStack Query, Vitest 5 with the Document 6 thresholds, Playwright 1.63, MSW, ESLint 10) with the static access gate, component tests and the gate e2e test; Docker Compose (db, backend, frontend, optional ollama with the pull script) and `make up`; CI with the eight stages pinned to commit SHAs, `eval.yml`, Dependabot, the PR template with the Definition of Done; Semgrep rules incl. test-without-assertion; hygiene scripts (fixture privacy, schema copies, generated client, quarantine count, job summary); `.gitignore`, `.env.example`, `CLAUDE.md`, `RUNBOOK.md` (Hebrew), root README placeholder. Verified locally: `./mvnw verify` green (32 unit, 11 integration), Vitest 5 tests, Playwright 2 tests, reference self-test `ALL OK`, generator no diff, Semgrep 0 findings, compose config valid. | Day 1 task 5 (first deployment) and the CI run on GitHub: both need the owner's accounts (RUNBOOK section 7). Gate G0 not yet checked. | Folder names `backend/` and `frontend/` (documents said `api/`, `web/`; exports patched, living documents to follow); `config` and `common` added as cross-cutting packages; `ai.daily-token-budget` left unset (no value in the documents); `@eslint-react` replaces `eslint-plugin-react` (no ESLint 10 support); jsdom pinned to 29.1.1 for Node 24.12; PIT `failWhenNoMutations=false` until day 3. | config 100% line (unit + IT); other packages empty | n/a (no classes in `engine`, `rules` yet) | n/a | n/a |
