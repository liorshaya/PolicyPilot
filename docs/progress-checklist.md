# PolicyPilot Progress Checklist

2026-09-17 · Lior Shaya

One box per task of the Work Plan (Document 7), ticked as it is done and never before its tests are green; a box that is skipped on purpose is struck through with the Scope ladder rung that cut it. Documents 1 to 7 are done, Document 8 and everything after day 0 are open.

## Four-week plan restored (decided 2026-09-22)

The interview is still on Monday, October 5, 2026, but the owner restored the full plan on September 22: rungs 2 to 10
of the Scope ladder and the evaluation runner come back, and days 10 to 19 keep their numbers and their content
(Document 7, Four-week plan restored). Each box that comes back carries "(restored: ...)" with the cut it undoes; the
README keeps that known limitation until the box is ticked, and `scripts/ci/check_readme_limitations.py` holds the two
files in step. Hol HaMoed Sukkot is worked; Fridays and Saturdays are off.

| Day | Date | Day | Date |
| --- | --- | --- | --- |
| 10 | Tue Sep 22 to Wed Sep 23 | 16 | Mon Sep 28, the last day that adds anything |
| 11 | Wed Sep 23, gate G2 | Slack | Tue Sep 29, only what slipped from days 10 to 16 |
| 12 | Thu Sep 24 | 17 | Wed Sep 30, rehearsal 1 |
| 13 | Thu Sep 24 | 18 | Thu Oct 1, rehearsal 2, the video, the rotation |
| 14 | Sun Sep 27 | 19 | Sun Oct 4, the tag, the freeze, gate G4 |
| 15 | Sun Sep 27, gate G3 |  |  |

**Before the restore, the two-week version (decided 2026-09-18).** Ten working days, the interview on the eleventh:
days 1 to 9 as planned, day 9 with the `decision` and `simulate` tools only, and a tenth day of warm-up, a short README,
the Definition of Done walk and the tag, named the v1.0.0 freeze (2026-09-22). Days 1 to 9 ran from September 18 to
22, ahead of the dates written in their blocks below. `v1.0.0` (7ad29de, with its SBOMs) stays the fallback.

## Documents

- [x] 1\. Project Brief
- [x] 2\. Architecture
- [x] 3\. Rules DSL Specification (with the JSON Schema, the lending rule set, the reference implementation)
- [x] 4\. AI Pipeline and Prompt Specification
- [x] 5\. Security Specification
- [x] 6\. Test Strategy
- [x] 7\. Work Plan
- [x] Cross-document consistency audit of Documents 1 to 6 (done before Document 7)
- [ ] 8\. README and Demo Script (written on days 16 to 19 from what was built; draft on day 16, video linked on day 18, Definition of Done ticked on day 19)

## Fixtures and tooling built ahead of day 1

- [x] `fixtures/` tree in the Document 6 layout: `schemas/ruleset-1.0.schema.json`, `policies/consumer-lending/` (`policy.he.md`, `ruleset.v1.json`, `sample-decision.json`), `reference/reference_check.py` (runs from any directory, self-test ALL OK)
- [x] `fixtures/tools/generate_cases.py`: 200 seeded cases from labeled strata, `cases-200.json` and `cases-expected.json`, every rule fires at least three times (minimum 4), the scripted change flips exactly 12 decisions, both asserted by the reference; case 17 is the demo case
- [x] `fixtures/policies/consumer-lending/change-request-1.json`: the scripted request in both languages, the five expected candidates, the replacement rules for R-170 and R-410 with `pending` provenance, the three untouched rules
- [x] `fixtures/conformance/C-01.json` to `C-31.json` from the Document 3 conformance table, run by the reference (31 of 31 pass)
- [x] `fixtures/conformance/invalid-*.json`, one per validator code and warning of the Document 3 static validation table (32 files), run by the reference, which now implements every code of the table with the three-layer stop
- [x] `fixtures/README.md` with the file shapes for the Java loaders
- [x] Evaluation set, `fixtures/eval/`: 18 labeled policies (12 Hebrew, 6 English) with expected rule sets, 10 to 30 labeled cases and 4 to 6 seeded defects each; 30 questions (6 refusals, 4 tool questions); 6 change requests with expected patch sets and flip counts; all admitted by the reference's `run_eval`
- [ ] Second-domain fixture for the encore, municipal tax discount (day 15): load `eval/policies/arnona-discount-seniors/` as the second protected policy (restored: rung 2)

## Day 0 (optional, Sunday Sep 20, one hour)

- [x] GitHub repository created, `main` protected (stages 1 to 6 required once CI exists)
- [x] Railway account and project; the pgvector template database (a `pgvector/pgvector:pg16` image service instead: the Railway templates run PostgreSQL 18)
- [x] Vercel account and project; DNS for `policypilot.liorshaya.com`
- [x] OpenAI API key with a monthly spending limit set to the demo budget (a 5 USD daily limit)
- [ ] `qwen3:14b` and `bge-m3` pulled into the local Ollama, before day 16 (restored: rung 3)
- [x] Interview date known, calendar re-mapped if it lands before day 20 (Monday Oct 5, 2026: the two-week version)

## Phase 0, Foundations (day 1, Tue Sep 22)

- [x] Repository layout, `backend` skeleton (Java 21, Spring Boot 4.0.x, Spring AI 2.0.x BOM, Flyway, Actuator, springdoc), the eleven packages, JaCoCo and PIT thresholds
- [x] `frontend` skeleton (React 19, TypeScript strict, Vite, TanStack Query, Vitest, Playwright, MSW, ESLint); `make up` as the one command after `docker compose up`
- [x] Tests: ArchUnit module rules, forbidden APIs, Spring AI only in `ai.adapter`; context loads per profile; Vitest smoke; Playwright gate page
- [x] Docker Compose: PostgreSQL 16 with pgvector, Ollama with the pull script, API image, web dev server; Flyway V1 with the `vector` extension
- [x] Tests: Testcontainers base class; Flyway and extension integration test; the compose run timed under 5 minutes (68 s on a clean runner)
- [x] CI: the eight stages with budgets, gitleaks, fixture privacy check, schema-copy check, generated-client check, JaCoCo and Vitest thresholds, PIT, Semgrep, ESLint, Dependency-Check, `npm audit`, ArchUnit report, Testcontainers stage, image build with Trivy and digest, Playwright on pull requests, reports and SBOM on tags, quarantine tag, branch protection
- [x] Test of the tests: a planted fake key on a throwaway branch fails stage 1 (pull request #14)
- [x] Fixtures committed as delivered (tree, conformance and invalid files, generator, change request, cases): `change-request-1.json`, `generate_cases.py` with `cases-200.json` and `cases-expected.json`; reference self-test and generator assertions in stage 2; CI re-runs the generator and diffs
- [x] Railway: API service from `backend/`, database, environment variables, health check, deploy only from green `main`
- [x] Vercel: project from `frontend/`, `VITE_API_BASE_URL`, the domain, the static access gate page
- [x] Proofs: health check from outside, gate page from a phone, deploy uses the stage 6 digest (the gate page on an emulated iPhone and confirmed by the owner; Railway runs the stage 6 digest since 2026-09-18)
- [x] `docs/worklog.md` started; gate G0 proof written in it

## Phase 1, Core (days 2 to 7)

**Day 2, Tue Sep 22: `rules`** (done early, on Sat Sep 19)

- [x] Conformance and invalid fixture files already committed and run by the reference; the Java validator and engine load the same files (the validator loads all 31 C files and 32 `invalid-*` files; the engine loads them on day 3)
- [x] DSL 1.0 model as records, strict Jackson, JSON Schema validation
- [x] Validator with the four contexts and every static check code; quote normalizer; case validator
- [x] Tests: invalid fixtures parameterized; normalizer with Hebrew, niqqud, U+202E, zero-width; context tests; one positive and one negative per static check
- [x] `rules` at 100% line; the lending rule set validates clean in PUBLISH; every invalid file fails with the Python code (PIT 990 of 990)

**Day 3, Wed Sep 23: `engine`** (done early, on Sat Sep 19)

- [x] Compiled rule set, `BigDecimal` expressions, operators with RE2J `matches`, combinators, priority order, terminal stop, derived order, EVAL\_DERIVED\_ABSENT, ERROR with partial trace, severity resolution, trace format, simulation
- [x] Tests: C-01 to C-31 parameterized; golden equality with `sample-decision.json` and `cases-expected.json`; jqwik properties; C-26 and 200 twice byte-identical; no `Clock` in `engine`; micro-benchmark; the ReDoS pattern timing
- [x] PIT on `engine` and `rules` at least 90%, hard gate from today (1207 of 1207 killed)

**Day 4, Thu Sep 24: front door, `policy`, `demo`** (done early, on Sat Sep 19)

- [x] `POST /auth/code`, signed cookie, constant-time compare, lockout; session filter on `/api/**`; custom header and Origin check; Bucket4j limits; body and upload limits; error envelope; JSON logging with redaction; security counters (the API on `api.policypilot.liorshaya.com`, cookie `SameSite=Lax`; the web app's gate exchanges the code)
- [x] `policy`: splitter, normalization, PDF in memory with limits, `POST /policies`, `GET /policies/{id}`, migrations
- [x] `demo`: `sandbox_id` from the cookie, protected rows with fork on write, lending policy fixture loaded (fork at the service level; the first route that writes to a protected row arrives on day 5)
- [x] Tests: OpenAPI authentication walk; wrong code, expired, tampered, missing header, foreign Origin, lockout timing; cookie units; `.exe`, 60-page and JavaScript PDFs; oversized bodies; limits with `Retry-After` and the SSE cap; cross-sandbox 404; fork on protected write; splitter and log encoder units; contract tests (the SSE cap as units until the first stream on day 7)

**Day 5, Sun Sep 27: versions, decisions, audit** (done early, on Sat Sep 20)

- [x] Rule set tables; `GET .../versions/{no}`; `PUT .../rules` with 422 pointers; `POST .../publish` transaction; immutability trigger; append-only audit grants; `audit` package (plus `GET /rulesets`, so the web app finds the seeded rule set, and the `ruleset` package that owns them)
- [x] `decision`: `POST .../decide` (case, list, `fixtureSet: "cases-200"`), persistence, `GET /decisions/{id}`, `GET .../stats`, `POST .../simulate`, JSON and CSV exports; version 1 and the 200 cases seeded as protected rows
- [x] Tests: contract tests per route; publish transaction and `UPDATE` refusal; append-only grant; replay equals stored; batch under 1 s, single under 50 ms; simulate stores nothing; CSV prefixing; cross-sandbox 404 per entity; publish on protected refused
- [x] The 200 cases decide through the API under 1 s with aggregates equal to `cases-expected.json` (106 ms locally, on Testcontainers)

**Day 6, Mon Sep 28: `frontend` screens** — done 2026-09-20 (#43 to #46)

- [x] Generated TypeScript client, `sse.ts`; policy screen; rule set screen with the editable decision table, cell grammar, JSON view, 422 pointers, publish; case runner; dashboard; decision list; trace view; RTL by policy language; MSW handlers
- [x] Tests: cell grammar round-trip with Hebrew labels; `sse.ts` at 100%; editing reducer; dashboard aggregation; RTL snapshots of the table and the trace view; Playwright step 2
- [x] Step 2 runs on the cloud site from a second browser (owner, 2026-09-20): the 200 cases decide with the aggregates of `cases-expected.json`, case 17 refers on R-330 with its trace, and a rule opens beside the paragraph it cites

**Day 7, Tue Sep 29: AI layer and `author`**

- [x] `ai` interfaces, prompt registry, `ai.adapter` with roles and strict schema, schema variant derivation, validation loop with `repair/v1`, token budget guard, `model_call` ledger, `model_response_cache`, `RecordedGateway` and recording mode
- [x] `author/v1` with examples; generation SSE (`parsing`, `authoring`, `validating`); generate button with progress; guided panel steps 1 and 2 (moved to day 11 by the restore)
- [x] Labeled policies 1 to 3 (committed before day 1; used for prompt tuning)
- [x] Tests: recorded author (valid, malformed, schema-invalid, repair twice then pass, three failures); timeout and 429 recordings; both profiles load with dimension check; RT-06, RT-09, RT-10; SSE sequence; ledger and budget units; ten live runs recorded; Playwright step 1 without flags
- [x] Gate G1

## Phase 2, Chat and RAG (days 8 to 11)

**Day 8, Wed Sep 30: `rag`**

- [x] Chunker; embedding job on publish with `embedding_status`; pgvector `VectorStore` through `EmbeddingGateway` with the dimension check; hybrid retrieval (vector plus `tsvector` `simple`, RRF k=60, top 8, minimum 0.35); citation building; the not-covered threshold
- [x] Questions 1 to 30 with expected chunks (committed before day 1)
- [x] A first live retrieval pass over the 30 questions, Hebrew misses noted
- [x] Tests: chunker units; embedding job with the fake gateway and every status transition; scoping by version and sandbox; `createNativeQuery` ArchUnit rule; RRF against a hand-computed example; the short-circuit test
- [x] Publishing version 1 embeds; a Hebrew question retrieves its paragraph on the cloud site

**Day 9, Thu Oct 1: `answer` and chat**

- [x] `answer/v1` with context assembly; `POST /chat/sessions`; messages SSE (`token`, `citations`, `usage`, `done`), not resumable (a retry instead, Document 2); the last 10 turns as history; marker parser and resolver; the four tools with validators, sandbox scope, 4 calls per turn and 1 simulate; the fixed not-covered sentence; chat screen with streaming, citation links, RTL (two-week version: the `decision` and `simulate` tools only; `stats` and `rules` are cut by rung 6)
- [x] Tests: chat SSE sequence; the error event in place of done; marker parser (unknown, malformed, duplicated); unresolved markers dropped; 12 turns keep 10; tool validators; recorded tool calls with foreign ids rejected; caps; recorded answers (valid, malformed, adversarial); RT-01, RT-02, RT-03, RT-07, RT-08; chat reducer and citation rendering; the three scripted questions recorded; Playwright step 3 (moved from day 11)
- [x] The three scripted questions answer with their markers on the cloud site; the guarantor question goes through `simulate`

**The v1.0.0 freeze, Tue Sep 22 (the two-week version's tenth day, not day 10)**

- [x] Scripted outputs warmed into `model_response_cache`; first token under 3 s for the cached questions; Hebrew and RTL check of the chat (2026-09-22: 0.64 to 0.7 s from the cache; RTL checked in Chromium on a desktop and an iPhone 13 viewport)
- [x] Short README: setup, architecture diagram, design principle, the three-step demo script with inputs, outputs and timings, known limitations (every struck-through box of this checklist), security and testing sections
- [x] The Brief's Definition of Done lines 1 to 6 walked on the cloud site, with evidence links in the README (line 3 not met, stated there)
- [ ] ~~Rehearsal from a second machine and from a phone on mobile data, timed per step; every fix ships with its test~~ (moved to days 17 and 18)
- [ ] ~~Pre-demo security checklist (Document 5) complete, the reset items excepted~~ (moved to days 17 and 18; Claude's items were done 2026-09-22)
- [ ] ~~The 2-minute fallback video recorded from a clean run; one screenshot per step; both linked from the README and copied to the phone~~ (moved to day 18)
- [ ] ~~Secrets rotated after the video: access code, cookie secret, provider key (old key deleted), admin code~~ (moved to day 18)
- [x] `main` tagged, CI green including the security gates, SBOM attached; freeze rule in force (`v1.0.0` at 7ad29de, 2026-09-22)
- [ ] ~~Interview kit on paper and on the phone: site URL, access code, the video file; contingencies rehearsed (the video offline, the screenshots without the site)~~ (moved to day 19)
- [ ] ~~Gate G4 (two-week version)~~ (moved to day 19)

**Day 10, Tue Sep 22 to Wed Sep 23: `review` and `explain`**

- [x] `review/v1` with the six kinds, anchors, severities; `reviewing` stage; warnings on decision table rows; gap acknowledgement before publish (restored: rung 9; #82, #85, #87)
- [x] `explain/v1`: `POST /decisions/{id}/explain`, trace as the only source, fired-rules filter, caching, two audiences; "Explain" in the trace view (restored: rungs 8 and 5; #83, #84)
- [x] Labeled policies 4 to 9 with seeded findings (committed before day 1)
- [x] Tests: anchor validation; review recording per kind including `injection`; RT-05; publish blocked by an unacknowledged gap; explanation contract; skipped-rule citation filtered; explain caching; Playwright step 1 complete; findings rendering (restored: rungs 9 and 8)
- [x] Step 1 shows the ambiguity and the conflict on the cloud site; "Explain" on case 17 cites R-330 and its paragraph (restored: rungs 9 and 8; the cloud check of 2026-09-22 in the worklog)

**Day 11, Wed Sep 23: `EvalRunner` and G2**

- [x] Runner: rule matching and normalization, author metrics, reviewer recall and precision, retrieval recall at 8, citation accuracy, refusal accuracy, confidence calibration; report in `docs/eval/` with a column per provider (restored: the evaluation runner)
- [x] Evaluation run 1: the live answer pass over all 30 questions, 58,714 tokens; the author and review passes scored from the recordings, so the 9-policy author run is what day 15 pays for with `author/v2` (restored: the evaluation runner)
- [ ] The local Ollama column of evaluation run 1, or with evaluation run 2 on day 15 if Ollama is not installed by day 11 (restored: rung 3) — **slipped to day 15 as the row allows: Ollama is not installed on this machine. The report carries the column, empty, and says why**
- [x] Guided panel steps 1 to 3 (steps 1 and 2 moved here from day 7) (restored: rung 7); step 4 is listed in the panel and marked Day 14
- [x] The `stats` and `rules` tools of day 9's row (restored: rung 6)
- [x] Scripted outputs warmed into `model_response_cache` again after any prompt change; Hebrew and RTL check of the chat (both first done at the v1.0.0 freeze) — no prompt changed, so nothing needed re-warming, and the three scripted questions still answer from the cache on the cloud site in 0.47 to 0.49 s; RTL is checked in the browser and in `panel.spec.ts`
- [x] Tests: runner units (normalization rules); runner on two labeled policies with recordings; both columns present; Playwright step 3; first token under 3 s for cached questions (restored: the evaluation runner)
- [x] Gate G2 (restored: gate G2)

## Phase 3, Agentic change (days 12 to 15)

**Day 12, Thu Sep 24: candidates and patches**

- [ ] Candidate selection; `change/v1` with the patches object; patched rules validated by `$ref`; CHANGE\_PROPOSAL context; proposal validator (no `remove` of a rule the request does not name, no patch outside the candidates, no `set_defaults`); `POST .../changes` SSE (`analyzing`, `proposing`, `validating`); `change_request` table (restored: rung 10)
- [x] Labeled policies 10 to 18 (committed before day 1)
- [ ] Tests: the scripted request yields the expected five candidates; recorded change (valid, malformed, RT-04); CHANGE\_PROPOSAL cases; `$ref` validation; SSE sequence; contract test (restored: rung 10)
- [ ] The scripted request proposes patches to R-170 and R-410 with `pending` provenance on the cloud site (restored: rung 10)

**Day 13, Thu Sep 24: regression, diff, approval**

- [ ] Regression run with flips by id; structural diff at `GET .../diff/{b}`; `regression` stage and report; `POST /changes/{id}/approve` transaction (pending to analyst, new version, audit entry) and `reject`; `GET /audit`, `GET /audit/export` (restored: rung 10)
- [x] `fixtures/eval/changes.json` with the six requests, expected patches and flip counts (committed before day 1)
- [ ] Change metric in the runner (restored: rung 10)
- [ ] Tests: exactly 12 flips; diff units; approve transaction with version 1 unchanged; approve on protected refused; reject publishes nothing; audit CSV prefixing; contract tests; change correctness on six requests (restored: rung 10)
- [ ] Version 2 on the cloud site with its audit entry; case 17 under version 1 unchanged (restored: rung 10)

**Day 14, Sun Sep 27: change screens**

- [ ] Change screen (request, four-stage progress, affected rules with rationale, side-by-side diff, regression report, approve or reject with note); audit log screen; version picker; guided panel step 4; RTL (restored: rung 10)
- [ ] Tests: diff renderer; regression report table; change reducer; RTL snapshots of diff and audit screens; Playwright step 4; RT-04 visible through the screen (restored: rung 10)
- [ ] Step 4 runs on the cloud site through the panel (restored: rung 10)

**Day 15, Sun Sep 27: buffer and G3**

- [ ] Fix list of days 12 to 14 (restored: rung 10)
- [ ] `demo`: nightly reset and re-seed, `POST /admin/reset` with the admin code header, RESET audit entry, stale sandbox deletion (restored: the reset)
- [ ] Second-domain fixture (municipal tax discount) loaded as a second protected policy (restored: rung 2)
- [ ] `author/v2` and `answer/v2` with evaluation run 2, in that one live run (decided on day 11 from
  `docs/eval/2026-09-23-authorv1-reviewv1-answerv1.md`): `author/v2` gives the model the field names the labeled
  cases use instead of asking it to invent them, which is the whole of the 0.12 rule recall; `answer/v2` says that
  a question about how many or about which rules is a tool call, which is why Q-08 and Q-14 were refused. Each
  changes a rendered prompt and so the response cache's key, so both re-warm the scripted outputs on the cloud
  site in the demo order, and step 1 and step 3 are walked again afterwards
- [ ] Evaluation run 2: 18 policies, 30 questions, 6 changes, both providers; report committed to `docs/eval/` (restored: rung 4 and the evaluation runner)
- [ ] Tests: reset tests; second-domain fixture through the reference and one recorded generation; traceability matrix has no empty row for FR-17 to FR-20 (restored: rungs 2 and 10, the reset)
- [ ] Gate G3 (restored: rung 10)

## Phase 4 and rehearsals (days 16 to 19)

**Day 16, Mon Sep 28: polish**

- [ ] `GET /system/provider` in the UI header; the `ollama` profile run locally with the chat step working, logged in the worklog (restored: the provider badge and rung 3)
- [ ] Hebrew and RTL pass: every screen in RTL and LTR, bidi isolation, Hebrew fixtures through every endpoint, the not-covered sentence in both languages; the last RTL snapshots (restored as a pass; RTL shipped with days 6 and 9 and was checked at the v1.0.0 freeze)
- [ ] README draft: setup, architecture diagram, design principle, demo script with inputs, outputs and timings, known limitations, security and testing sections, talking points, performance numbers (restored from the v1.0.0 freeze)
- [ ] README setup run on a clean clone under 5 minutes (restored from the v1.0.0 freeze)
- [ ] Definition of Done walk on the cloud site with evidence links; fix list for days 17 to 18; traceability matrix regenerated with no empty row (restored from the v1.0.0 freeze)
- [ ] Playwright: all four steps through the guided panel in one run (restored: rungs 7 and 10)

**Day 17, Wed Sep 30: rehearsal 1**

- [ ] Rehearsal 1 from a second machine, timed per step (0:45, 0:30, 0:45, 1:00); fix list ranked by demo impact (restored from the v1.0.0 freeze)
- [ ] Fixes, each with its test (restored from the v1.0.0 freeze)
- [ ] Pre-demo checklist, first half: security counters, protected checksum, nightly reset ran, Railway and Vercel variables, `SPRING_PROFILES_ACTIVE=openai,cloud` (restored from the v1.0.0 freeze)
- [ ] Test names of `engine`, `rules` and `web.security` read as the list of claims (restored from the v1.0.0 freeze)
- [ ] README corrections (restored from the v1.0.0 freeze)

**Day 18, Thu Oct 1: rehearsal 2 and the video**

- [ ] Rehearsal 2 from a phone on mobile data; rate limits and lockout confirmed not to hit the interviewers' network (restored from the v1.0.0 freeze)
- [ ] Fixes from rehearsal 2 (restored from the v1.0.0 freeze)
- [ ] The 2-minute fallback video recorded from a clean run; one screenshot per step; both linked from the README and copied to the phone (restored from the v1.0.0 freeze)
- [ ] Secrets rotated after the video: access code, cookie secret, provider key (old key deleted), admin code; OpenAI monthly limit set (restored from the v1.0.0 freeze)
- [ ] Manual reset tested with the admin code, then the code rotated again (restored: the reset)

**Day 19, Sun Oct 4: rehearsal 3 and freeze**

- [ ] Reset, full script with cached outputs warm, encore on the second domain (restored: rung 2 for the encore)
- [ ] Contingencies rehearsed: video offline on the phone; screenshots without the site (restored from the v1.0.0 freeze)
- [ ] `main` tagged, CI green including security gates, SBOM attached (restored from the v1.0.0 freeze)
- [ ] Definition of Done ticked in the README with evidence links (restored from the v1.0.0 freeze)
- [ ] Interview kit on paper and on the phone: site URL, access code, admin code, video file (restored from the v1.0.0 freeze)
- [ ] Gate G4; freeze rule in force (restored from the v1.0.0 freeze)

## Gates

- [x] G0, day 1: compose plus one command under 5 minutes; CI stages 1, 2, 4 and 6 green; health check on Railway; gate page on Vercel; fixtures committed with the reference self-test in stage 2
- [x] G1, day 7: C-01 to C-31 pass in Java and Python; `engine` and `rules` at 100% line and PIT at least 90%; 200 cases under 1 s and twice byte-identical; decisions persisted; author schema-valid in at least 9 of 10 recorded runs; steps 1 (without flags) and 2 through the panel on the cloud site
- [x] G2, day 11: step 1 complete and step 3 on the cloud site; retrieval recall at 8, citation and refusal accuracy at target; RT-01 to RT-03 and RT-05 to RT-10 pass; first token under 3 s for cached questions (restored: gate G2)
- [ ] G3, day 15: step 4 end to end with exactly 12 flips and version 1 unchanged; RT-04; change correctness 5 of 6; second-domain fixture loaded; report with both provider columns (restored: rung 10)
- [ ] ~~G4, two-week version: the Brief's Definition of Done lines 1 to 6 true; the pre-demo checklist complete, the reset items excepted; the video plays; `main` frozen at a tagged build with its SBOM~~ (moved to day 19)
- [ ] G4, day 19: every line of the Brief's Definition of Done true; the pre-demo checklist complete; the traceability matrix has no empty row; the video plays; `main` frozen at a tagged build with its SBOM

## Definition of Done (Brief) and pre-demo security checklist (Document 5)

**Definition of Done**

- [x] `docker compose up` plus one command starts the system on a clean machine in under 5 minutes (93 s, 2026-09-22)
- [x] The lending policy converts to a schema-valid rule set on the first or second attempt in at least 9 of 10 runs
- [ ] At least 90% of the generated rules match the labeled expected rules for the lending policy (measured by the evaluation runner from day 11; not met at the v1.0.0 freeze, 78% on the canonical run and 67% at the median by the manual checklist, docs/eval/rule-match-lending.md)
- [x] The 200 cases decide in under 1 second and rerunning yields byte-identical results (in the test environment; 1.3 to 1.7 s over the network on the live site)
- [x] Every one of the 200 decisions has a trace naming each fired rule and the compared values
- [x] The three scripted chat questions return cited answers; the out-of-scope question returns "not covered by the documents"
- [ ] The scripted change request produces a diff, a regression report and version 2 with an audit entry; version 1 decisions unchanged (restored: rung 10)
- [ ] Switching the profile from `openai` to `ollama` needs no code change and the chat step still works (both profiles load in tests; the chat step on Ollama runs on day 16) (restored: rung 3)
- [ ] Unit tests cover the engine and the DSL validator at 100% line coverage with a mutation score of at least 90%; integration tests cover every use case with a recorded model; the traceability matrix has no empty row
- [ ] The README has the architecture diagram, the design principle, the demo script, known limitations and the 2-minute recorded run
- [ ] The live demo on Railway and Vercel runs the four steps with the access code, and a request without the code is rejected (three steps and the access code met at the v1.0.0 freeze; step 4 comes with the change flow) (restored: rung 10)

**Pre-demo security checklist**

- [ ] Secrets rotated within the last 7 days: access code, cookie secret, provider key; old key deleted in the OpenAI dashboard
- [ ] OpenAI monthly limit set to the demo budget; ledger counter near zero
- [x] Security counters for the last 7 days reviewed; no protected-row write attempts, no denylist hits (2026-09-22, the logs of all 20 deployments; repeated the morning of 5.10)
- [ ] Protected rule set checksum equals the fixture; 200 cases present; nightly reset ran last night (the checksum and the 200 cases checked at the v1.0.0 freeze) (restored: the reset)
- [x] CI green on `main` including the security gates; SBOM attached to the tagged build (`v1.0.0`)
- [ ] Manual reset endpoint tested with the admin code, then the admin code rotated (restored: the reset)
- [ ] Vercel and Railway environment variables reviewed; no unused variables; `SPRING_PROFILES_ACTIVE` is `openai,cloud` (Railway reviewed 2026-09-22: `openai,cloud`; `POLICYPILOT_ADMIN_CODE` stays for the reset of day 15; Vercel is the owner's, RUNBOOK 12.1)
- [ ] Rate limits tested from a phone on mobile data
- [x] The README's security section and known limitations reviewed against Document 5
