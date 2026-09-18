# PolicyPilot Progress Checklist

2026-09-17 · Lior Shaya

One box per task of the Work Plan (Document 7), ticked as it is done and never before its tests are green; a box that is skipped on purpose is struck through with the Scope ladder rung that cut it. Documents 1 to 7 are done, Document 8 and everything after day 0 are open.

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
- [ ] Second-domain fixture for the encore, municipal tax discount (day 15): load `eval/policies/arnona-discount-seniors/` as the second protected policy

## Day 0 (optional, Sunday Sep 20, one hour)

- [ ] GitHub repository created, `main` protected (stages 1 to 6 required once CI exists)
- [ ] Railway account and project; the pgvector template database
- [ ] Vercel account and project; DNS for `policypilot.liorshaya.com`
- [ ] OpenAI API key with a monthly spending limit set to the demo budget
- [ ] `qwen3:14b` and `bge-m3` pulled into the local Ollama
- [ ] Interview date known, calendar re-mapped if it lands before day 20

## Phase 0, Foundations (day 1, Tue Sep 22)

- [ ] Repository layout, `api` skeleton (Java 21, Spring Boot 4.0.x, Spring AI 2.0.x BOM, Flyway, Actuator, springdoc), the eleven packages, JaCoCo and PIT thresholds
- [ ] `web` skeleton (React 19, TypeScript strict, Vite, TanStack Query, Vitest, Playwright, MSW, ESLint); `make up` as the one command after `docker compose up`
- [ ] Tests: ArchUnit module rules, forbidden APIs, Spring AI only in `ai.adapter`; context loads per profile; Vitest smoke; Playwright gate page
- [ ] Docker Compose: PostgreSQL 16 with pgvector, Ollama with the pull script, API image, web dev server; Flyway V1 with the `vector` extension
- [ ] Tests: Testcontainers base class; Flyway and extension integration test; the compose run timed under 5 minutes
- [ ] CI: the eight stages with budgets, gitleaks, fixture privacy check, schema-copy check, generated-client check, JaCoCo and Vitest thresholds, PIT, Semgrep, ESLint, Dependency-Check, `npm audit`, ArchUnit report, Testcontainers stage, image build with Trivy and digest, Playwright on pull requests, reports and SBOM on tags, quarantine tag, branch protection
- [ ] Test of the tests: a planted fake key on a throwaway branch fails stage 1
- [ ] Fixtures committed as delivered (tree, conformance and invalid files, generator, change request, cases): `change-request-1.json`, `generate_cases.py` with `cases-200.json` and `cases-expected.json`; reference self-test and generator assertions in stage 2; CI re-runs the generator and diffs
- [ ] Railway: API service from `api/`, database, environment variables, health check, deploy only from green `main`
- [ ] Vercel: project from `web/`, `VITE_API_BASE_URL`, the domain, the static access gate page
- [ ] Proofs: health check from outside, gate page from a phone, deploy uses the stage 6 digest
- [ ] `docs/worklog.md` started; gate G0 proof written in it

## Phase 1, Core (days 2 to 7)

**Day 2, Wed Sep 23: `rules`**

- [ ] Conformance and invalid fixture files already committed and run by the reference; the Java validator and engine load the same files
- [ ] DSL 1.0 model as records, strict Jackson, JSON Schema validation
- [ ] Validator with the four contexts and every static check code; quote normalizer; case validator
- [ ] Tests: invalid fixtures parameterized; normalizer with Hebrew, niqqud, U+202E, zero-width; context tests; one positive and one negative per static check
- [ ] `rules` at 100% line; the lending rule set validates clean in PUBLISH; every invalid file fails with the Python code

**Day 3, Thu Sep 24: `engine`**

- [ ] Compiled rule set, `BigDecimal` expressions, operators with RE2J `matches`, combinators, priority order, terminal stop, derived order, EVAL\_DERIVED\_ABSENT, ERROR with partial trace, severity resolution, trace format, simulation
- [ ] Tests: C-01 to C-31 parameterized; golden equality with `sample-decision.json` and `cases-expected.json`; jqwik properties; C-26 and 200 twice byte-identical; no `Clock` in `engine`; micro-benchmark; the ReDoS pattern timing
- [ ] PIT on `engine` and `rules` at least 90%, hard gate from today

**Day 4, Sun Sep 27: front door, `policy`, `demo`**

- [ ] `POST /auth/code`, signed cookie, constant-time compare, lockout; session filter on `/api/**`; custom header and Origin check; Bucket4j limits; body and upload limits; error envelope; JSON logging with redaction; security counters
- [ ] `policy`: splitter, normalization, PDF in memory with limits, `POST /policies`, `GET /policies/{id}`, migrations
- [ ] `demo`: `sandbox_id` from the cookie, protected rows with fork on write, lending policy fixture loaded
- [ ] Tests: OpenAPI authentication walk; wrong code, expired, tampered, missing header, foreign Origin, lockout timing; cookie units; `.exe`, 60-page and JavaScript PDFs; oversized bodies; limits with `Retry-After` and the SSE cap; cross-sandbox 404; fork on protected write; splitter and log encoder units; contract tests

**Day 5, Mon Sep 28: versions, decisions, audit**

- [ ] Rule set tables; `GET .../versions/{no}`; `PUT .../rules` with 422 pointers; `POST .../publish` transaction; immutability trigger; append-only audit grants; `audit` package
- [ ] `decision`: `POST .../decide` (case, list, `fixtureSet: "cases-200"`), persistence, `GET /decisions/{id}`, `GET .../stats`, `POST .../simulate`, JSON and CSV exports; version 1 and the 200 cases seeded as protected rows
- [ ] Tests: contract tests per route; publish transaction and `UPDATE` refusal; append-only grant; replay equals stored; batch under 1 s, single under 50 ms; simulate stores nothing; CSV prefixing; cross-sandbox 404 per entity; publish on protected refused
- [ ] The 200 cases decide through the API under 1 s with aggregates equal to `cases-expected.json`

**Day 6, Tue Sep 29: `web` screens**

- [ ] Generated TypeScript client, `sse.ts`; policy screen; rule set screen with the editable decision table, cell grammar, JSON view, 422 pointers, publish; case runner; dashboard; decision list; trace view; RTL by policy language; MSW handlers
- [ ] Tests: cell grammar round-trip with Hebrew labels; `sse.ts` at 100%; editing reducer; dashboard aggregation; RTL snapshots of the table and the trace view; Playwright step 2
- [ ] Step 2 runs on the cloud site from a second browser

**Day 7, Wed Sep 30: AI layer and `author`**

- [ ] `ai` interfaces, prompt registry, `ai.adapter` with roles and strict schema, schema variant derivation, validation loop with `repair/v1`, token budget guard, `model_call` ledger, `model_response_cache`, `RecordedGateway` and recording mode
- [ ] `author/v1` with examples; generation SSE (`parsing`, `authoring`, `validating`); generate button with progress; guided panel steps 1 and 2
- [x] Labeled policies 1 to 3 (committed before day 1; used for prompt tuning)
- [ ] Tests: recorded author (valid, malformed, schema-invalid, repair twice then pass, three failures); timeout and 429 recordings; both profiles load with dimension check; RT-06, RT-09, RT-10; SSE sequence; ledger and budget units; ten live runs recorded; Playwright step 1 without flags
- [ ] Gate G1

## Phase 2, Chat and RAG (days 8 to 11)

**Day 8, Thu Oct 1: `rag`**

- [ ] Chunker; embedding job on publish with `embedding_status`; pgvector `VectorStore` through `EmbeddingGateway` with the dimension check; hybrid retrieval (vector plus `tsvector` `simple`, RRF k=60, top 8, minimum 0.35); citation building; the not-covered threshold
- [x] Questions 1 to 30 with expected chunks (committed before day 1)
- [ ] A first live retrieval pass over the 30 questions, Hebrew misses noted
- [ ] Tests: chunker units; embedding job with the fake gateway and every status transition; scoping by version and sandbox; `createNativeQuery` ArchUnit rule; RRF against a hand-computed example; the short-circuit test
- [ ] Publishing version 1 embeds; a Hebrew question retrieves its paragraph on the cloud site

**Day 9, Sun Oct 4: `answer` and chat**

- [ ] `answer/v1` with context assembly; `POST /chat/sessions`; messages SSE (`token`, `citations`, `usage`, `done`) with resume; memory of the last 10 turns; marker parser and resolver; the four tools with validators, sandbox scope, 4 calls per turn and 1 simulate; the fixed not-covered sentence; chat screen with streaming, citation links, RTL
- [ ] Tests: chat SSE sequence; resume; marker parser (unknown, malformed, duplicated); unresolved markers dropped; 12 turns keep 10; tool validators; recorded tool calls with foreign ids rejected; caps; recorded answers (valid, malformed, adversarial); RT-01, RT-02, RT-03, RT-07, RT-08; chat reducer and citation rendering; the three scripted questions recorded
- [ ] The three scripted questions answer with their markers on the cloud site; the guarantor question goes through `simulate`

**Day 10, Mon Oct 5: `review` and `explain`**

- [ ] `review/v1` with the six kinds, anchors, severities; `reviewing` stage; warnings on decision table rows; gap acknowledgement before publish
- [ ] `explain/v1`: `POST /decisions/{id}/explain`, trace as the only source, fired-rules filter, caching, two audiences; "Explain" in the trace view
- [x] Labeled policies 4 to 9 with seeded findings (committed before day 1)
- [ ] Tests: anchor validation; review recording per kind including `injection`; RT-05; publish blocked by an unacknowledged gap; explanation contract; skipped-rule citation filtered; explain caching; Playwright step 1 complete; findings rendering
- [ ] Step 1 shows the ambiguity and the conflict on the cloud site; "Explain" on case 17 cites R-330 and its paragraph

**Day 11, Tue Oct 6: `EvalRunner` and G2**

- [ ] Runner: rule matching and normalization, author metrics, reviewer recall and precision, retrieval recall at 8, citation accuracy, refusal accuracy, confidence calibration; report in `docs/eval/` with a column per provider
- [ ] Evaluation run 1: strong model on 9 policies and 30 questions; local Ollama column; a v2 prompt only if a target is missed (CHANGELOG, one live run)
- [ ] Guided panel step 3; scripted outputs warmed into `model_response_cache`; Hebrew and RTL check of the chat
- [ ] Tests: runner units (normalization rules); runner on two labeled policies with recordings; both columns present; Playwright step 3; first token under 3 s for cached questions
- [ ] Gate G2

## Phase 3, Agentic change (days 12 to 15)

**Day 12, Wed Oct 7: candidates and patches**

- [ ] Candidate selection; `change/v1` with the patches object; patched rules validated by `$ref`; CHANGE\_PROPOSAL context; proposal validator (no unmentioned `remove`, no unrequested `set_defaults`); `POST .../changes` SSE (`analyzing`, `proposing`, `validating`); `change_request` table
- [x] Labeled policies 10 to 18 (committed before day 1)
- [ ] Tests: the scripted request yields the expected five candidates; recorded change (valid, malformed, RT-04); CHANGE\_PROPOSAL cases; `$ref` validation; SSE sequence; contract test
- [ ] The scripted request proposes patches to R-170 and R-410 with `pending` provenance on the cloud site

**Day 13, Thu Oct 8: regression, diff, approval**

- [ ] Regression run with flips by id; structural diff at `GET .../diff/{b}`; `regression` stage and report; `POST /changes/{id}/approve` transaction (pending to analyst, new version, audit entry) and `reject`; `GET /audit`, `GET /audit/export`
- [x] `fixtures/eval/changes.json` with the six requests, expected patches and flip counts (committed before day 1)
- [ ] Change metric in the runner
- [ ] Tests: exactly 12 flips; diff units; approve transaction with version 1 unchanged; approve on protected refused; reject publishes nothing; audit CSV prefixing; contract tests; change correctness on six requests
- [ ] Version 2 on the cloud site with its audit entry; case 17 under version 1 unchanged

**Day 14, Sun Oct 11: change screens**

- [ ] Change screen (request, four-stage progress, affected rules with rationale, side-by-side diff, regression report, approve or reject with note); audit log screen; version picker; guided panel step 4; RTL
- [ ] Tests: diff renderer; regression report table; change reducer; RTL snapshots of diff and audit screens; Playwright step 4; RT-04 visible through the screen
- [ ] Step 4 runs on the cloud site through the panel

**Day 15, Mon Oct 12: buffer and G3**

- [ ] Fix list of days 12 to 14
- [ ] `demo`: nightly reset and re-seed, `POST /admin/reset` with the admin code header, RESET audit entry, stale sandbox deletion
- [ ] Second-domain fixture (municipal tax discount) loaded as a second protected policy
- [ ] Evaluation run 2: 18 policies, 30 questions, 6 changes, both providers; report committed to `docs/eval/`
- [ ] Tests: reset tests; second-domain fixture through the reference and one recorded generation; traceability matrix has no empty row for FR-17 to FR-20
- [ ] Gate G3

## Phase 4 and rehearsals (days 16 to 19)

**Day 16, Tue Oct 13: polish**

- [ ] `GET /system/provider` in the UI header; the `ollama` profile run locally with the chat step working, logged in the worklog
- [ ] Hebrew and RTL pass: every screen in RTL and LTR, bidi isolation, Hebrew fixtures through every endpoint, the not-covered sentence in both languages; the last RTL snapshots
- [ ] README draft: setup, architecture diagram, design principle, demo script with inputs, outputs and timings, known limitations, security and testing sections, talking points, performance numbers
- [ ] README setup run on a clean clone under 5 minutes
- [ ] Definition of Done walk on the cloud site with evidence links; fix list for days 17 to 18; traceability matrix regenerated with no empty row
- [ ] Playwright: all four steps through the guided panel in one run

**Day 17, Wed Oct 14: rehearsal 1**

- [ ] Rehearsal 1 from a second machine, timed per step (0:45, 0:30, 0:45, 1:00); fix list ranked by demo impact
- [ ] Fixes, each with its test
- [ ] Pre-demo checklist, first half: security counters, protected checksum, nightly reset ran, Railway and Vercel variables, `SPRING_PROFILES_ACTIVE=openai,cloud`
- [ ] Test names of `engine`, `rules` and `web.security` read as the list of claims
- [ ] README corrections

**Day 18, Thu Oct 15: rehearsal 2 and the video**

- [ ] Rehearsal 2 from a phone on mobile data; rate limits and lockout confirmed not to hit the interviewers' network
- [ ] Fixes from rehearsal 2
- [ ] The 2-minute fallback video recorded from a clean run; one screenshot per step; both linked from the README and copied to the phone
- [ ] Secrets rotated after the video: access code, cookie secret, provider key (old key deleted), admin code; OpenAI monthly limit set
- [ ] Manual reset tested with the admin code, then the code rotated again

**Day 19, Sun Oct 18: rehearsal 3 and freeze**

- [ ] Reset, full script with cached outputs warm, encore on the second domain
- [ ] Contingencies rehearsed: video offline on the phone; screenshots without the site
- [ ] `main` tagged, CI green including security gates, SBOM attached
- [ ] Definition of Done ticked in the README with evidence links
- [ ] Interview kit on paper and on the phone: site URL, access code, admin code, video file
- [ ] Gate G4; freeze rule in force

## Gates

- [ ] G0, day 1: compose plus one command under 5 minutes; CI stages 1, 2, 4 and 6 green; health check on Railway; gate page on Vercel; fixtures committed with the reference self-test in stage 2
- [ ] G1, day 7: C-01 to C-31 pass in Java and Python; `engine` and `rules` at 100% line and PIT at least 90%; 200 cases under 1 s and twice byte-identical; decisions persisted; author schema-valid in at least 9 of 10 recorded runs; steps 1 (without flags) and 2 through the panel on the cloud site
- [ ] G2, day 11: step 1 complete and step 3 on the cloud site; retrieval recall at 8, citation and refusal accuracy at target; RT-01 to RT-03 and RT-05 to RT-10 pass; first token under 3 s for cached questions
- [ ] G3, day 15: step 4 end to end with exactly 12 flips and version 1 unchanged; RT-04; change correctness 5 of 6; second-domain fixture loaded; report with both provider columns
- [ ] G4, day 19: the Brief's Definition of Done true; pre-demo checklist complete; matrix with no empty row; video plays; `main` frozen at a tagged build with SBOM

## Definition of Done (Brief) and pre-demo security checklist (Document 5)

**Definition of Done**

- [ ] `docker compose up` plus one command starts the system on a clean machine in under 5 minutes
- [ ] The lending policy converts to a schema-valid rule set on the first or second attempt in at least 9 of 10 runs
- [ ] At least 90% of the generated rules match the labeled expected rules for the lending policy
- [ ] The 200 cases decide in under 1 second and rerunning yields byte-identical results
- [ ] Every one of the 200 decisions has a trace naming each fired rule and the compared values
- [ ] The three scripted chat questions return cited answers; the out-of-scope question returns "not covered by the documents"
- [ ] The scripted change request produces a diff, a regression report and version 2 with an audit entry; version 1 decisions unchanged
- [ ] Switching the profile from `openai` to `ollama` needs no code change and the chat step still works
- [ ] Unit tests cover the engine and the DSL validator at 100% line coverage with a mutation score of at least 90%; integration tests cover every use case with a recorded model; the traceability matrix has no empty row
- [ ] The README has the architecture diagram, the design principle, the demo script, known limitations and the 2-minute recorded run
- [ ] The live demo on Railway and Vercel runs the four steps with the access code, and a request without the code is rejected

**Pre-demo security checklist**

- [ ] Secrets rotated within the last 7 days: access code, cookie secret, provider key; old key deleted in the OpenAI dashboard
- [ ] OpenAI monthly limit set to the demo budget; ledger counter near zero
- [ ] Security counters for the last 7 days reviewed; no protected-row write attempts, no denylist hits
- [ ] Protected rule set checksum equals the fixture; 200 cases present; nightly reset ran last night
- [ ] CI green on `main` including the security gates; SBOM attached to the tagged build
- [ ] Manual reset endpoint tested with the admin code, then the admin code rotated
- [ ] Vercel and Railway environment variables reviewed; no unused variables; `SPRING_PROFILES_ACTIVE` is `openai,cloud`
- [ ] Rate limits tested from a phone on mobile data
- [ ] The README's security section and known limitations reviewed against Document 5
