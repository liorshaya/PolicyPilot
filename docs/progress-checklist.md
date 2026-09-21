# PolicyPilot Progress Checklist

2026-09-17 · Lior Shaya

One box per task of the Work Plan (Document 7), ticked as it is done and never before its tests are green; a box that is skipped on purpose is struck through with the Scope ladder rung that cut it. Documents 1 to 7 are done, Document 8 and everything after day 0 are open.

## Two-week version (decided 2026-09-18)

The interview is on Monday, October 5, 2026, so the plan runs as the two-week version of Document 7 (Scope ladder):
ten working days, the interview on the eleventh. Day 1 was finished on Friday, September 18. Days 2 to 7 keep their
content; day 8 is retrieval with the not-covered threshold; day 9 is the answer prompt with the `decision` and
`simulate` tools and the chat screen; day 10 is rehearsal, the video, a short README and the freeze. Cut on entry:
rungs 2 to 10 of the Scope ladder and the evaluation runner. The demo is three steps (author without reviewer warnings,
decide, ask) and the change flow is the closing slide. Not scheduled by the two-week version, and therefore known
limitations: the nightly and manual resets (day 15) and the provider badge in the UI (day 16). Hol HaMoed Sukkot is
worked; Fridays and Saturdays are off. Cut boxes below are struck through with the rung that cut them.

| Day | Date | Day | Date |
| --- | --- | --- | --- |
| 1 | Fri Sep 18, done | 6 | Mon Sep 28 |
| 2 | Tue Sep 22 | 7 | Tue Sep 29, gate G1 |
| 3 | Wed Sep 23 | 8 | Wed Sep 30 |
| 4 | Thu Sep 24 | 9 | Thu Oct 1 |
| 5 | Sun Sep 27 | 10 | Sun Oct 4, freeze and gate G4 |

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
- [ ] ~~Second-domain fixture for the encore, municipal tax discount (day 15): load `eval/policies/arnona-discount-seniors/` as the second protected policy~~ (rung 2)

## Day 0 (optional, Sunday Sep 20, one hour)

- [x] GitHub repository created, `main` protected (stages 1 to 6 required once CI exists)
- [x] Railway account and project; the pgvector template database (a `pgvector/pgvector:pg16` image service instead: the Railway templates run PostgreSQL 18)
- [x] Vercel account and project; DNS for `policypilot.liorshaya.com`
- [x] OpenAI API key with a monthly spending limit set to the demo budget (a 5 USD daily limit)
- [ ] ~~`qwen3:14b` and `bge-m3` pulled into the local Ollama~~ (not needed in the two-week version: rung 3 cuts the Ollama column)
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
- [x] `author/v1` with examples; generation SSE (`parsing`, `authoring`, `validating`); generate button with progress; ~~guided panel steps 1 and 2~~ (rung 7)
- [x] Labeled policies 1 to 3 (committed before day 1; used for prompt tuning)
- [x] Tests: recorded author (valid, malformed, schema-invalid, repair twice then pass, three failures); timeout and 429 recordings; both profiles load with dimension check; RT-06, RT-09, RT-10; SSE sequence; ledger and budget units; ten live runs recorded; Playwright step 1 without flags
- [x] Gate G1

## Phase 2, Chat and RAG (days 8 to 11), days 8 and 9 in the two-week version

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

**Day 10, Sun Oct 4: rehearsal, video, README and freeze (two-week version)**

- [ ] Scripted outputs warmed into `model_response_cache`; first token under 3 s for the cached questions; Hebrew and RTL check of the chat
- [ ] Short README: setup, architecture diagram, design principle, the three-step demo script with inputs, outputs and timings, known limitations (every struck-through box of this checklist), security and testing sections
- [ ] The Brief's Definition of Done lines 1 to 6 walked on the cloud site, with evidence links in the README
- [ ] Rehearsal from a second machine and from a phone on mobile data, timed per step; every fix ships with its test
- [ ] Pre-demo security checklist (Document 5) complete, the reset items excepted
- [ ] The 2-minute fallback video recorded from a clean run; one screenshot per step; both linked from the README and copied to the phone
- [ ] Secrets rotated after the video: access code, cookie secret, provider key (old key deleted), admin code
- [ ] `main` tagged, CI green including the security gates, SBOM attached; freeze rule in force
- [ ] Interview kit on paper and on the phone: site URL, access code, the video file; contingencies rehearsed (the video offline, the screenshots without the site)
- [ ] Gate G4 (two-week version)


**Planned day 10, `review` and `explain`: cut in the two-week version**

- [ ] `review/v1` with the six kinds, anchors, severities; `reviewing` stage; warnings on decision table rows; gap acknowledgement before publish
- [ ] `explain/v1`: `POST /decisions/{id}/explain`, trace as the only source, fired-rules filter, caching, two audiences; "Explain" in the trace view
- [x] Labeled policies 4 to 9 with seeded findings (committed before day 1)
- [ ] Tests: anchor validation; review recording per kind including `injection`; RT-05; publish blocked by an unacknowledged gap; explanation contract; skipped-rule citation filtered; explain caching; Playwright step 1 complete; findings rendering
- [ ] Step 1 shows the ambiguity and the conflict on the cloud site; "Explain" on case 17 cites R-330 and its paragraph

**Planned day 11, `EvalRunner` and G2: cut in the two-week version**

- [ ] Runner: rule matching and normalization, author metrics, reviewer recall and precision, retrieval recall at 8, citation accuracy, refusal accuracy, confidence calibration; report in `docs/eval/` with a column per provider
- [ ] Evaluation run 1: strong model on 9 policies and 30 questions; local Ollama column; a v2 prompt only if a target is missed (CHANGELOG, one live run)
- [ ] Guided panel step 3; scripted outputs warmed into `model_response_cache`; Hebrew and RTL check of the chat
- [ ] Tests: runner units (normalization rules); runner on two labeled policies with recordings; both columns present; Playwright step 3; first token under 3 s for cached questions
- [ ] Gate G2

## Phase 3, Agentic change (days 12 to 15), cut in the two-week version (rung 10)

**Day 12, Wed Oct 7: candidates and patches**

- [ ] ~~Candidate selection; `change/v1` with the patches object; patched rules validated by `$ref`; CHANGE\_PROPOSAL context; proposal validator (no unmentioned `remove`, no unrequested `set_defaults`); `POST .../changes` SSE (`analyzing`, `proposing`, `validating`); `change_request` table~~ (rung 10)
- [x] Labeled policies 10 to 18 (committed before day 1)
- [ ] ~~Tests: the scripted request yields the expected five candidates; recorded change (valid, malformed, RT-04); CHANGE\_PROPOSAL cases; `$ref` validation; SSE sequence; contract test~~ (rung 10)
- [ ] ~~The scripted request proposes patches to R-170 and R-410 with `pending` provenance on the cloud site~~ (rung 10)

**Day 13, Thu Oct 8: regression, diff, approval**

- [ ] ~~Regression run with flips by id; structural diff at `GET .../diff/{b}`; `regression` stage and report; `POST /changes/{id}/approve` transaction (pending to analyst, new version, audit entry) and `reject`; `GET /audit`, `GET /audit/export`~~ (rung 10)
- [x] `fixtures/eval/changes.json` with the six requests, expected patches and flip counts (committed before day 1)
- [ ] ~~Change metric in the runner~~ (rung 10)
- [ ] ~~Tests: exactly 12 flips; diff units; approve transaction with version 1 unchanged; approve on protected refused; reject publishes nothing; audit CSV prefixing; contract tests; change correctness on six requests~~ (rung 10)
- [ ] ~~Version 2 on the cloud site with its audit entry; case 17 under version 1 unchanged~~ (rung 10)

**Day 14, Sun Oct 11: change screens**

- [ ] ~~Change screen (request, four-stage progress, affected rules with rationale, side-by-side diff, regression report, approve or reject with note); audit log screen; version picker; guided panel step 4; RTL~~ (rung 10)
- [ ] ~~Tests: diff renderer; regression report table; change reducer; RTL snapshots of diff and audit screens; Playwright step 4; RT-04 visible through the screen~~ (rung 10)
- [ ] ~~Step 4 runs on the cloud site through the panel~~ (rung 10)

**Day 15, Mon Oct 12: buffer and G3**

- [ ] ~~Fix list of days 12 to 14~~ (rung 10)
- [ ] ~~`demo`: nightly reset and re-seed, `POST /admin/reset` with the admin code header, RESET audit entry, stale sandbox deletion~~ (not scheduled in the two-week version: a known limitation)
- [ ] ~~Second-domain fixture (municipal tax discount) loaded as a second protected policy~~ (rung 2)
- [ ] ~~Evaluation run 2: 18 policies, 30 questions, 6 changes, both providers; report committed to `docs/eval/`~~ (rung 4 and the evaluation runner)
- [ ] ~~Tests: reset tests; second-domain fixture through the reference and one recorded generation; traceability matrix has no empty row for FR-17 to FR-20~~ (rungs 2 and 10; the reset is not built)
- [ ] ~~Gate G3~~ (rung 10)

## Phase 4 and rehearsals (days 16 to 19), folded into day 10 in the two-week version

**Day 16, Tue Oct 13: polish**

- [ ] ~~`GET /system/provider` in the UI header; the `ollama` profile run locally with the chat step working, logged in the worklog~~ (not scheduled in the two-week version: the provider badge is a known limitation; the profile switch stays in configuration)
- [ ] ~~Hebrew and RTL pass: every screen in RTL and LTR, bidi isolation, Hebrew fixtures through every endpoint, the not-covered sentence in both languages; the last RTL snapshots~~ (not scheduled as a pass: RTL ships with days 6 and 9 and is checked on day 10)
- [ ] ~~README draft: setup, architecture diagram, design principle, demo script with inputs, outputs and timings, known limitations, security and testing sections, talking points, performance numbers~~ (folded into day 10)
- [ ] ~~README setup run on a clean clone under 5 minutes~~ (folded into day 10)
- [ ] ~~Definition of Done walk on the cloud site with evidence links; fix list for days 17 to 18; traceability matrix regenerated with no empty row~~ (folded into day 10)
- [ ] ~~Playwright: all four steps through the guided panel in one run~~ (rungs 7 and 10)

**Day 17, Wed Oct 14: rehearsal 1**

- [ ] ~~Rehearsal 1 from a second machine, timed per step (0:45, 0:30, 0:45, 1:00); fix list ranked by demo impact~~ (folded into day 10)
- [ ] ~~Fixes, each with its test~~ (folded into day 10)
- [ ] ~~Pre-demo checklist, first half: security counters, protected checksum, nightly reset ran, Railway and Vercel variables, `SPRING_PROFILES_ACTIVE=openai,cloud`~~ (folded into day 10)
- [ ] ~~Test names of `engine`, `rules` and `web.security` read as the list of claims~~ (folded into day 10)
- [ ] ~~README corrections~~ (folded into day 10)

**Day 18, Thu Oct 15: rehearsal 2 and the video**

- [ ] ~~Rehearsal 2 from a phone on mobile data; rate limits and lockout confirmed not to hit the interviewers' network~~ (folded into day 10)
- [ ] ~~Fixes from rehearsal 2~~ (folded into day 10)
- [ ] ~~The 2-minute fallback video recorded from a clean run; one screenshot per step; both linked from the README and copied to the phone~~ (folded into day 10)
- [ ] ~~Secrets rotated after the video: access code, cookie secret, provider key (old key deleted), admin code; OpenAI monthly limit set~~ (folded into day 10)
- [ ] ~~Manual reset tested with the admin code, then the code rotated again~~ (the reset is not built in the two-week version)

**Day 19, Sun Oct 18: rehearsal 3 and freeze**

- [ ] ~~Reset, full script with cached outputs warm, encore on the second domain~~ (folded into day 10; the encore: rung 2)
- [ ] ~~Contingencies rehearsed: video offline on the phone; screenshots without the site~~ (folded into day 10)
- [ ] ~~`main` tagged, CI green including security gates, SBOM attached~~ (folded into day 10)
- [ ] ~~Definition of Done ticked in the README with evidence links~~ (folded into day 10)
- [ ] ~~Interview kit on paper and on the phone: site URL, access code, admin code, video file~~ (folded into day 10)
- [ ] ~~Gate G4; freeze rule in force~~ (folded into day 10)

## Gates

- [x] G0, day 1: compose plus one command under 5 minutes; CI stages 1, 2, 4 and 6 green; health check on Railway; gate page on Vercel; fixtures committed with the reference self-test in stage 2
- [x] G1, day 7: C-01 to C-31 pass in Java and Python; `engine` and `rules` at 100% line and PIT at least 90%; 200 cases under 1 s and twice byte-identical; decisions persisted; author schema-valid in at least 9 of 10 recorded runs; steps 1 (without flags) and 2 through the panel on the cloud site
- [ ] ~~G2, day 11: step 1 complete and step 3 on the cloud site; retrieval recall at 8, citation and refusal accuracy at target; RT-01 to RT-03 and RT-05 to RT-10 pass; first token under 3 s for cached questions~~ (not in the two-week version)
- [ ] ~~G3, day 15: step 4 end to end with exactly 12 flips and version 1 unchanged; RT-04; change correctness 5 of 6; second-domain fixture loaded; report with both provider columns~~ (rung 10)
- [ ] G4, day 10 (two-week version): the Brief's Definition of Done lines 1 to 6 true; the pre-demo checklist complete, the reset items excepted; the video plays; `main` frozen at a tagged build with its SBOM (was: day 19, the full Definition of Done and a matrix with no empty row)

## Definition of Done (Brief) and pre-demo security checklist (Document 5)

**Definition of Done**

- [ ] `docker compose up` plus one command starts the system on a clean machine in under 5 minutes
- [ ] The lending policy converts to a schema-valid rule set on the first or second attempt in at least 9 of 10 runs
- [ ] At least 90% of the generated rules match the labeled expected rules for the lending policy (two-week version: measured by the manual checklist on the lending policy, since the evaluation runner is cut)
- [ ] The 200 cases decide in under 1 second and rerunning yields byte-identical results
- [ ] Every one of the 200 decisions has a trace naming each fired rule and the compared values
- [ ] The three scripted chat questions return cited answers; the out-of-scope question returns "not covered by the documents"
- [ ] ~~The scripted change request produces a diff, a regression report and version 2 with an audit entry; version 1 decisions unchanged~~ (rung 10: the change flow is the closing slide)
- [ ] Switching the profile from `openai` to `ollama` needs no code change and the chat step still works (beyond the two-week minimum: the switch stays in configuration and both profiles load in tests; the chat step on Ollama is not scheduled)
- [ ] Unit tests cover the engine and the DSL validator at 100% line coverage with a mutation score of at least 90%; integration tests cover every use case with a recorded model; the traceability matrix has no empty row
- [ ] The README has the architecture diagram, the design principle, the demo script, known limitations and the 2-minute recorded run
- [ ] The live demo on Railway and Vercel runs the four steps with the access code, and a request without the code is rejected (two-week version: three steps)

**Pre-demo security checklist**

- [ ] Secrets rotated within the last 7 days: access code, cookie secret, provider key; old key deleted in the OpenAI dashboard
- [ ] OpenAI monthly limit set to the demo budget; ledger counter near zero
- [ ] Security counters for the last 7 days reviewed; no protected-row write attempts, no denylist hits
- [ ] Protected rule set checksum equals the fixture; 200 cases present; nightly reset ran last night (two-week version: no nightly reset; the checksum and the 200 cases are still checked)
- [ ] CI green on `main` including the security gates; SBOM attached to the tagged build
- [ ] ~~Manual reset endpoint tested with the admin code, then the admin code rotated~~ (the reset is not built in the two-week version)
- [ ] Vercel and Railway environment variables reviewed; no unused variables; `SPRING_PROFILES_ACTIVE` is `openai,cloud`
- [ ] Rate limits tested from a phone on mobile data
- [ ] The README's security section and known limitations reviewed against Document 5
