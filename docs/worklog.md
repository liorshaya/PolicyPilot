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
| 2026-09-18 | pre-1 | Repository reorganised into a monorepo (`backend/`, `frontend/`, `fixtures/`, `docs/`); the delivered `docs/` and `fixtures/` zips extracted as canonical, older drafts removed (kept in the first commit); backend skeleton (Spring Boot 4.0.8, Spring AI 2.0.1, Flyway V1 with pgvector, the eleven packages plus `config` and `common`, layer sub-packages, JaCoCo gates, PIT profile, Dockerfile by digest, Maven wrapper 3.9.16); ArchUnit suite (module table, nothing depends on `web`, Spring AI only in `ai.adapter`, forbidden APIs, engine purity); context-loads tests for `openai`, `ollama`, `openai,cloud`; Flyway and pgvector integration test; health endpoint test; `DATABASE_URL` translation for Railway with unit tests; frontend skeleton (React 19.3, TypeScript 6.0 strict, Vite 8.3, TanStack Query, Vitest 5 with the Document 6 thresholds, Playwright 1.63, MSW, ESLint 10) with the static access gate, component tests and the gate e2e test; Docker Compose (db, backend, frontend, optional ollama with the pull script) and `make up`; CI with the eight stages pinned to commit SHAs, `eval.yml`, Dependabot, the PR template with the Definition of Done; Semgrep rules incl. test-without-assertion; hygiene scripts (fixture privacy, schema copies, generated client, quarantine count, job summary); `.gitignore`, `.env.example`, `CLAUDE.md`, `RUNBOOK.md` (Hebrew), root README placeholder. Verified locally: `./mvnw verify` green (32 unit, 11 integration), Vitest 5 tests, Playwright 2 tests, reference self-test `ALL OK`, generator no diff, Semgrep 0 findings, compose config valid. First CI run on GitHub: stages 1 to 5 and 8 green, stage 6 red at the Trivy gate (CVE-2026-65182, CVE-2026-65905, CVE-2026-68525: critical, in Tomcat 11.0.24 as managed by Spring Boot 4.0.8), and the same failure on the 7 pull requests Dependabot opened right after the push; fixed in pull request #8 (Tomcat pinned to 11.0.26, stage 6 now scans before it pushes, Dependabot limited to the documented version lines), Dependabot pull requests #1 to #7 closed. | Gate G0 passed on 2026-09-18 (proof below). | Folder names `backend/` and `frontend/` (documents said `api/`, `web/`; exports patched, living documents to follow); `config` and `common` added as cross-cutting packages; `ai.daily-token-budget` left unset (no value in the documents); `@eslint-react` replaces `eslint-plugin-react` (no ESLint 10 support); jsdom pinned to 29.1.1 for Node 24.12; PIT `failWhenNoMutations=false` until day 3; `tomcat.version` overridden to 11.0.26 until a Spring Boot 4.0.x release manages a fixed Tomcat; the repository is public, so branch protection needs no paid plan; Railway set up as config as code (`backend/railway.json`), its database as a plain `pgvector/pgvector:pg16` image service because Railway's pgvector templates run PostgreSQL 18, and the Dockerfile without BuildKit cache mounts, which Railway accepts only with a service-specific id. | config 100% line (unit + IT); other packages empty | n/a (no classes in `engine`, `rules` yet) | n/a | n/a |

## Plan change: the two-week version (decided 2026-09-18)

The interview is on Monday, October 5, 2026, before day 20, so the plan follows the two-week version of Document 7
(Scope ladder). Day 1 was finished on 2026-09-18; days 2 to 10 run on Sep 22, 23, 24, 27, 28, 29, 30, Oct 1 and Oct 4,
with Hol HaMoed Sukkot worked and Fridays and Saturdays off. Cut on entry: rungs 2 to 10 and the evaluation runner;
the Brief's 90% rule-match line is measured by its manual checklist on the lending policy. Not scheduled by the
two-week version, and therefore known limitations: the nightly and manual resets (day 15) and the provider badge in
the UI (day 16). Spend caps: OpenAI 5 USD per day, which sets the ceiling for the app's daily token budget on day 7;
Railway on the Hobby plan. The dates and the struck-through boxes are in `docs/progress-checklist.md`.

## Gate G0 proof (collected 2026-09-18, ahead of day 1): passed

| G0 condition (Document 7, Phase gates) | Proof | Result |
| --- | --- | --- |
| `docker compose up` plus one command starts the system on a clean machine in under 5 minutes | Compose smoke on a fresh GitHub runner with none of the project's images or caches: https://github.com/liorshaya/PolicyPilot/actions/runs/35334127170 | 68 s of the 300 s budget; health UP and the gate page served |
| CI stages 1, 2, 4 and 6 run green on the skeleton | https://github.com/liorshaya/PolicyPilot/actions/runs/35334390631 | every job that runs on `main` green; stage 7 runs on pull requests only |
| The API answers `/actuator/health` on Railway | `curl https://policypilot-production-88ae.up.railway.app/actuator/health`, 2026-09-18 10:26 UTC | HTTP 200 in 0.44 s, `{"groups":["liveness","readiness"],"status":"UP"}` |
| The web app shows the access gate on Vercel | https://policypilot.liorshaya.com rendered in an emulated iPhone 13 (Playwright), 2026-09-18 | heading, code field and button behave, deep links answer 200, no console errors. At 10:47 UTC the live domain answered HTTP 200 with a valid certificate through a public DNS lookup, and the owner confirmed it opens; no photo from a physical phone is attached |
| `fixtures/` committed with the Python reference self-test passing in stage 2 | stage 2 of the run above | `ALL OK`; the generator reproduces the committed cases with no diff |

Other day 1 proofs:

- Test of the tests: a planted fake key failed stage 1 at the gitleaks step (rule `generic-api-key`) and branch
  protection blocked the merge: pull request #14, https://github.com/liorshaya/PolicyPilot/actions/runs/35334443606.
  Stage 1 scans every branch the checkout fetches, so a secret on any branch turns stage 1 red for every run until the
  branch or commit is gone.
- Branch protection on `main` since 2026-09-18: pull request required, stages 1 to 6 required, applies to
  administrators, linear history, no force pushes or deletions.
- Railway deploys only from a green `main` (Wait for CI on); Vercel deploys `main` to production and pull requests to
  previews.
- Railway deploys the digest stage 6 produced (Documents 5 and 7; decided 2026-09-18): Railway had been building its
  own image (sha256:1b380e61…), different from the scanned one. Since 11:18 UTC the service runs
  `ghcr.io/liorshaya/policypilot-backend@sha256:ddbee484…`, the stage 6 image of main at 3a4161e (deployment
  71a00330, SUCCESS, health UP), and the CI job `deploy-railway` deploys each new main digest after stages 1 to 6 pass.
