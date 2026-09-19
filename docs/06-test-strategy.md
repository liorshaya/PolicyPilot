# PolicyPilot Test Strategy

2026-09-19 · Lior Shaya

Document 6 of the PolicyPilot set. It turns the test plans scattered across the [Project Brief](01-project-brief.md), the [Architecture](02-architecture.md), the [Rules DSL Specification](03-rules-dsl-specification.md), the [AI Pipeline and Prompt Specification](04-ai-pipeline-and-prompts.md) and the [Security Specification](05-security-specification.md) into one discipline: what gets tested at which level, what coverage is required where, what a task must include before it counts as done, and how the tests are written alongside the code rather than after it. The work plan (Document 7) schedules every test named here next to its feature.

## Purpose and Coverage Philosophy

Tests are written with every task, not after the project, and the target is coverage by risk rather than a single high percentage: 100% with mutation testing on the code that decides cases, high line coverage on the code that validates and authorizes, and behavior tests on everything else.

A blanket "as high as possible" target has a known failure mode: it rewards tests that execute code without asserting anything, and an interviewer who reads the test suite sees that immediately. The rule here is different for each kind of code:

| Kind of code | Why it matters | Target | How it is measured |
| --- | --- | --- | --- |
| Decides cases: `engine`, `rules` (validator, DSL model) | A wrong branch is a wrong loan decision, and the whole pitch is determinism | 100% line and branch coverage, and a mutation score of at least 90% (a test must fail when an operator, a comparison or a boundary is mutated) | JaCoCo per package, PIT mutation testing |
| Protects: `web.security`, authorization checks, input normalization, marker resolution, provenance verification | A gap is a security finding | 95% line coverage, every control from Document 5 has a named test | JaCoCo, the security test inventory |
| Orchestrates: `policy`, `decision`, `change`, `audit`, `rag`, `ai` use cases | Bugs here are visible and recoverable, and behavior matters more than lines | 80% line coverage; every use case has an integration test with a real database and a recorded model | JaCoCo, Testcontainers |
| Adapts: `ai.adapter`, controllers, DTO mapping | Thin code whose failures surface immediately | 70%, contract tests over the OpenAPI document | JaCoCo, contract tests |
| Web app | The demo is what the interviewers see | 80% statement coverage on features, 100% on the SSE hook, the marker renderer and the decision-table cell grammar; the four demo steps end to end | Vitest coverage, Playwright |

Three consequences follow. First, a task is not done when its code compiles and the demo works; it is done when its tests exist, pass, and the coverage gates for the packages it touched still hold (Definition of Done). Second, every requirement in the Brief has at least one named test that proves it, so "does it work?" is answered by a matrix, not by a demo (Traceability Matrix). Third, the tests are part of the interview material: the engine's mutation report, the conformance suite from Document 3 and the evaluation report from Document 4 are the three artifacts that show the presenter tested the parts a rules-engine company cares about.

## Test Levels

Nine levels, each owning one question; a test lives at the lowest level that can answer its question, so most tests are fast, and the slow levels only prove what the fast ones cannot.

| Level | Question it answers | Scope and tools | Runs | Share of tests |
| --- | --- | --- | --- | --- |
| Unit | Does this class do what its contract says, for every branch and boundary? | JUnit 5, AssertJ, Mockito only for gateways; jqwik for property tests; Vitest for the web app | Every push, under 2 minutes | about 70% |
| Architecture | Are the package boundaries and forbidden APIs intact? | ArchUnit rules from Documents 2 and 5 | Every push | a dozen rules |
| Conformance | Does the engine implement Document 3 exactly? | The C-01 to C-31 fixtures and the validator fixtures, run by one parameterized test; the same fixtures run through the Python reference implementation | Every push | fixed set |
| Integration | Do the pieces work together against real infrastructure? | Spring Boot tests with Testcontainers PostgreSQL and pgvector; Flyway migrations; real repositories; the fake model gateway with recordings | Every push, under 6 minutes | about 15% |
| Contract | Does the API match its OpenAPI document and its error envelope? | MockMvc tests generated from the OpenAPI paths: status codes, envelope shape, authentication on every route | Every push | one per route |
| Recorded AI | Do the use cases handle every model output shape, including bad ones? | Fake `LlmGateway` replaying `fixtures/eval/recordings`, including malformed, adversarial and red-team outputs | Every push | one per use case per shape |
| Security | Is every control from Document 5 present? | Named tests across unit, integration and Playwright, inventoried in Document 5's test plan | Every push | inventory |
| End to end | Do the four demo steps work in a browser through the real stack? | Playwright against Docker Compose with the recorded gateway; RTL snapshots | Pull requests and before the rehearsal | four flows plus the gate |
| Live evaluation | Are the prompts good enough, on both providers? | `EvalRunner` on the labeled set, the red-team set and the demo questions; produces the report | On demand and before every prompt change | report |

**What each level must not do**: unit tests do not start Spring or touch a database; integration tests do not call a real model; contract tests do not assert business results; end-to-end tests do not test edge cases (those belong to unit and integration); the live evaluation is never a required CI check, because it needs a key and is non-deterministic by nature.

**Test doubles policy**: the only things ever faked are the model gateways (`LlmGateway`, `EmbeddingGateway`), the clock (there is none in the engine, and the API's clock is injectable for audit timestamps) and outbound HTTP; the database, the validator, the engine and the repositories are always real.

## Definition of Done

A task in the work plan is done when every line of this checklist is true; the pull request template carries the same list, and a task that cannot satisfy a line says why in the pull request rather than skipping it.

- [ ] The tests for the task were listed before the code was written, taken from the specification tables (the DSL conformance rows, the API table, the security control table, the requirement it implements), and each listed test exists.
- [ ] Every new public method of `engine`, `rules`, the validators and the security filters has unit tests for its normal path, its boundaries and its failure path.
- [ ] Every new endpoint has a contract test (status codes, envelope, authentication) and an integration test through the service layer.
- [ ] Every new use of the model has a recorded test with at least one malformed and one adversarial recording.
- [ ] Every new security control from Document 5 has its named test.
- [ ] Coverage gates for the touched packages pass (`./mvnw verify` fails otherwise), and the mutation score of `engine` and `rules` has not dropped.
- [ ] The Python reference implementation and its fixtures were updated when the DSL or the engine semantics changed, and both implementations still agree on the conformance suite.
- [ ] The fixtures the task adds are synthetic, deterministic and committed; no test depends on the network, the clock, the locale or test order.
- [ ] CI is green, including the security gates.
- [ ] The traceability matrix row for the requirement names the new tests.

The list is deliberately short enough to read in a minute; the discipline is in the first line, which turns "write tests after" into "list tests first", and the work plan allocates time for it inside every task instead of in a testing phase at the end.

## Coverage Targets and Enforcement

Coverage is enforced by the build, per package, with mutation testing where lines are not enough; a threshold that is not enforced by `./mvnw verify` does not exist.

**Backend (JaCoCo, `jacoco-maven-plugin` check goal, per-package rules)**

| Package | Line | Branch | Notes |
| --- | --- | --- | --- |
| `engine` | 100% | 100% | Excluding nothing; the engine is small on purpose |
| `rules` (model, validator, static checks, quote normalization) | 100% | 95% | Generated schema classes excluded |
| `web.security`, `web` filters and validators | 95% | 90% |  |
| `ai` (use cases, validation loop, marker resolver, tool argument validators) | 90% | 85% | The adapter is excluded from these rules and covered by contract tests |
| `rag` | 85% | 80% | Retrieval ranking is covered by integration tests on a fixed corpus |
| `policy`, `decision`, `change`, `audit`, `demo` | 80% | 75% |  |
| `ai.adapter`, controllers, configuration classes | 70% | 60% | Thin; contract tests carry the weight |
| Whole API | 85% | 80% | Trend reported, not a gate on its own |

**Mutation testing (PIT, `pitest-maven`)** on `engine` and `rules` only, with the default mutators plus boundary and negate-conditional mutators: a mutation score of at least 90%, run in CI on every push (the two packages are small enough for PIT to finish in under three minutes). The mutation report is committed to `docs/quality/` with each tag; a surviving mutant that is judged equivalent is listed with a reason.

**Web app (Vitest `coverage.thresholds`)**: 80% statements and branches on `src/features/**`; 100% on `src/api/sse.ts`, `src/features/chat/markers.ts` and `src/features/rules/cellGrammar.ts`; no threshold on `src/shared/ui` primitives. Playwright covers the four demo steps and the access gate and is not counted in coverage.

**Gates in CI** (order in the CI section): the JaCoCo check goal fails the build below any per-package threshold; PIT fails below 90% on the two core packages; Vitest fails below its thresholds; a pull request cannot merge without these and the security gates. A threshold is lowered only by a pull request that changes this document, with the reason in the changelog.

**What coverage does not measure** is stated so nobody games it: assertions (a test without assertions is caught by a Semgrep rule for `@Test` methods with no `assert`), correctness of expected values (the reference implementation and the labeled fixtures are the check), and the quality of the AI outputs (the evaluation report is the check).

## Backend Test Design

Tests mirror the package structure, are named after the behavior they prove, build their data with fixtures and builders rather than by hand, and never depend on the order in which they run.

**Layout and naming**

```
backend/src/test/java/com/liorshaya/policypilot/
  engine/            RuleEngineTest, TraceTest, ExpressionEvaluatorTest, EngineConformanceTest, EnginePropertiesTest
  rules/json/        RuleSetMapperTest
  rules/validation/  SchemaValidatorTest, SemanticValidatorTest, StructuralChecksTest, QuoteNormalizerTest, ProvenanceContextTest,
                     ConstraintTest, InvalidFixturesTest, FixtureRuleSetsTest, CaseValidatorTest
  ai/                AuthorUseCaseTest, ReviewUseCaseTest, ExplainUseCaseTest, AnswerUseCaseTest, ChangeUseCaseTest,
                     RepairLoopTest, MarkerResolverTest, ToolArgumentsTest, SchemaVariantTest
  rag/               ChunkerTest, HybridRetrievalIT, ThresholdTest
  web/               <Resource>ControllerContractIT, AccessCodeFilterTest, RateLimitFilterTest, CsrfDefensesIT, InputNormalizerTest
  decision/          DecisionServiceIT, BatchDecisionIT, SimulationIT
  change/            ChangeProposalIT, RegressionRunIT, ApprovalIT
  architecture/      PackageRulesTest, ForbiddenApisTest
  support/           Fixtures, RuleSetBuilder, CaseBuilder, RecordedGateway, PostgresContainerSupport, Requirement
```

Test method names read as sentences: `terminalRejectStopsEvaluationAndMarksLaterRulesSkipped`, `guardedDerivationSkippedWhenIncomeIsZeroThenR170Rejects`, `foreignSandboxDecisionReturns404`. A test proves one behavior; a behavior with several boundaries is a parameterized test with named cases.

**Fixtures and builders**: `Fixtures.lendingV1()` loads the published rule set from `fixtures/`; `RuleSetBuilder` and `CaseBuilder` start from it and change one thing (`RuleSetBuilder.lendingV1().rule("R-320", r -> r.put("priority", 210))`), so a test shows only what matters to it; the builder edits the JSON tree, so it can also build the documents the schema rejects; conformance fixtures are JSON files with `ruleset`, `case` and `expected` and are loaded by one parameterized test, the same files the Python reference runs; the invalid fixtures carry the expected code, context and severity, and their runner also asserts that an error stops the validator in its own layer (file shapes in fixtures/README.md).

**Integration tests** (`*IT`) use one shared Testcontainers PostgreSQL with pgvector per JVM (`@ServiceConnection`), Flyway migrations from the real `main` resources, and a transaction rollback per test where possible; tests that need committed data (the regression run, the nightly reset) clean up by sandbox id. The model gateway is always the `RecordedGateway`, which serves recordings by prompt name and input hash and fails loudly on a miss, so a test can never reach a real provider by accident.

**Determinism and isolation**: the engine has no clock; the API's clock is a `Clock` bean overridden with a fixed instant in tests; random data comes from a seeded generator; tests run in parallel by class and share no static state; any test that reads the locale or the time zone sets them explicitly. A flaky test is quarantined the same day (CI section).

**Property-based tests (jqwik) for the engine**: for random valid rule sets and cases, the trace has one step per enabled rule, no step follows a terminal decision except `skipped`, `derived` values equal a direct evaluation of their expressions, the outcome equals the severity resolution when no terminal fired, and evaluating twice yields byte-identical JSON; for random expressions, the evaluator agrees with `BigDecimal` arithmetic done by hand in the test.

**Streaming**: SSE endpoints are tested with `WebTestClient` consuming the event stream and asserting the event sequence (`token`\* then `citations` then `usage` then `done`; `parsing`, `authoring`, `validating`, `reviewing` for generation), the `Last-Event-ID` resume for generation, and the abort on a denylist hit.

**Database grants and triggers** are tested, not assumed: an integration test attempts `UPDATE` on a published `ruleset_version` and on `audit_entry` through the application role and expects a database error.

## Frontend Test Design

The web app is tested as the user sees it: components render from mocked API responses, the streaming hook is tested against a scripted stream, and the four demo steps run in a real browser against the real stack.

| Layer | Tool | What is tested | Notes |
| --- | --- | --- | --- |
| Component | Vitest, Testing Library, MSW for the API | Each feature's screens with realistic responses: decision table renders 20 rules with the right cells; trace view pins the deciding rule and greys not-fired steps; findings chips open the right rule; diff view shows field-level changes; audit list orders newest first | Queries by role and text, never by class names; RTL assertions check `dir` on Hebrew content blocks |
| Hook | Vitest with a fake `ReadableStream` | `useSse`: token accumulation, event ordering, `Last-Event-ID` resume, abort on unmount, error states, marker parsing while streaming | 100% coverage required |
| Pure logic | Vitest | Cell grammar render and parse round-trip for every operator; marker renderer; number and unit formatting; language direction detection | Property-style tests over generated conditions |
| Visual and RTL | Vitest snapshot of rendered DOM for the decision table and the chat with Hebrew content | A change in direction handling or fonts shows up as a snapshot diff | Snapshots are reviewed, not blindly updated |
| Accessibility | `axe-core` in component tests for the main screens | No serious or critical violations | Keeps the demo usable on any machine and screen |
| End to end | Playwright against Docker Compose with the recorded gateway | The access gate; the four scripted demo steps through the guided panel; a second browser context cannot see the first's sandbox; the cached scripted answers arrive within 3 seconds | Runs on pull requests and before the rehearsal; screenshots on failure attached to CI |

**API client**: the TypeScript client is generated from the OpenAPI document at build time, so a backend contract change breaks the web build before it breaks a test; MSW handlers are typed by the same generated types, which keeps mocks honest.

**What is not tested in the browser**: business rules (the engine's job), model behavior (the evaluation's job), and layout pixels (no visual regression service in v1); the snapshot tests cover the two screens where direction handling can silently break.

## Traceability Matrix

Every requirement in the Brief maps to the tests that prove it; a requirement with an empty row is not done, whatever the demo shows.

| Requirement | Unit and conformance | Integration and contract | Recorded AI, evaluation, red team | End to end |
| --- | --- | --- | --- | --- |
| FR-1 Upload or paste a policy, versioned | Paragraph splitter, Unicode normalizer, PDF limits | `POST /policies` with text, `.md`, `.pdf`, oversized and wrong-type files |  | Step 1 (paste) |
| FR-2 Generate a draft rule set via structured output | Schema variant derivation | Generation SSE sequence with the recorded author output | Author recordings: valid, malformed JSON, schema-invalid | Step 1 |
| FR-3 Reject or repair invalid generation, max 2 | Repair error formatting | Repair loop with recordings failing twice then passing; failing three times | Recordings per attempt |  |
| FR-4 Provenance on every rule | Quote normalizer, provenance checks (C fixtures, `invalid-*`) | Draft with a mismatching quote rejected | Provenance accuracy metric |  |
| FR-5 Reviewer pass with findings | Findings anchor validation | Review recording with each kind, including `injection` | Reviewer recall and precision on seeded defects | Step 1 warnings shown |
| FR-6 Decision table and JSON, validated alike | Cell grammar round-trip | `PUT .../rules` with a manual edit that breaks `DERIVED_ORDER` returns 422 with a pointer |  | Edit a cell in the table |
| FR-7 Immutable published versions |  | Publish transaction; `UPDATE` on a published version fails at the database |  |  |
| FR-8 Evaluate a case with a trace | Engine unit and conformance C-01 to C-31, property tests | `POST .../decide` single case |  | Step 2 open case 17 |
| FR-9 Batch of 200 with aggregates |  | Batch endpoint with the fixture set; aggregates match the labeled outcomes; under 1 s |  | Step 2 |
| FR-10 Persist decisions with version and snapshot |  | Decision row has version id, input snapshot, trace JSON; replay equals stored |  |  |
| FR-11 Explain a decision citing rules and passages | Explanation contract checks (fired rules only, no skipped) | Explain with a recorded output that cites a skipped rule is filtered | Explain recordings | Step 2 |
| FR-12 Chunk and embed on publish | Chunker (paragraphs, rule rendering) | Embedding job with the fake embedding gateway; `embedding_status` transitions; scoping by version |  |  |
| FR-13 Chat with retrieved context and citations, streamed | Marker parser and resolver | Chat SSE sequence; citations event resolves ids | Answer recordings; citation accuracy metric | Step 3 |
| FR-14 Tools: decision, stats, rules, simulate | Tool argument validators | Tool calls through the recorded gateway; foreign ids rejected | RT-03; simulation counterfactual | Step 3 guarantor question |
| FR-15 Not-covered answers | Threshold logic | Off-corpus question short-circuits without a model call | Refusal accuracy metric | Step 3 last question |
| FR-16 Conversation memory, last N turns |  | Session with 12 turns keeps the last 10 |  |  |
| FR-17 Change request: affected rules via embeddings plus the model | Candidate selection | Candidates for the scripted request are the expected five | Change recordings; change correctness metric | Step 4 |
| FR-18 Diff and regression before publish | Structural diff | Regression run flips exactly 12 fixture cases |  | Step 4 |
| FR-19 Approval creates version and audit entry; pending becomes analyst | Provenance context validator | Approve transaction; audit entry fields; version 1 decisions unchanged | RT-04 | Step 4 approve |
| FR-20 Side-by-side diff of two versions | Diff rendering | `GET .../diff` |  |  |
| FR-21 Provider switch by configuration | Role-to-model mapping | Context loads with `openai` and with `ollama` profiles; dimension check at startup | Evaluation report has both columns |  |
| FR-22 Evaluation harness with per-rule precision and recall | Rule matching and normalization | Runner on two labeled policies with recordings | The live report |  |
| FR-23 Guided demo panel | Panel actions dispatch the same API calls |  |  | All four steps through the panel |
| NFR-1 Determinism | C-26, property tests, no clock in `engine` (ArchUnit) | 200 cases twice, byte-identical |  |  |
| NFR-2 Explainability | Trace content tests |  | Explanation contract | Trace view |
| NFR-3 Auditability |  | Immutable versions, append-only audit grants, audit entries on publish and approval |  | Audit log screen |
| NFR-4 Provider independence | ArchUnit: Spring AI only in `ai.adapter` | Both profiles load | Both columns in the report |  |
| NFR-5 Hebrew support | Quote normalization with niqqud and punctuation; bidi stripping; cell grammar with Hebrew labels | Hebrew fixtures through every endpoint | Hebrew questions in the evaluation | RTL snapshots |
| NFR-6 Performance | Engine micro-benchmark | Batch under 1 s; single decision under 50 ms with persistence |  | Cached demo answers under 3 s |
| NFR-7 Robustness to model failure | Circuit breaker and retry policy | Provider timeout and 429 recordings degrade to defined errors; nothing stored | Malformed recordings for every prompt |  |
| Security controls (Document 5) | Named tests per control | Authentication walk, authorization across sandboxes, injection payloads, limits | RT-01 to RT-10 | Access gate, sandbox isolation |

The matrix is a Markdown file in `docs/quality/traceability.md`, regenerated by a script from test annotations (`@Requirement("FR-8")` on JUnit tests, a `// @requirement FR-13` comment in Vitest and Playwright tests), so a requirement without a test shows up as an empty row in CI rather than in an interview.

## AI Layer Testing

The model is non-deterministic and paid, so its tests are split into what runs on every push without a model (recordings) and what runs on demand with one (the evaluation), and the boundary between them is a recorded file.

**Recordings**: every live model call the evaluation runner makes is saved as `fixtures/eval/recordings/<provider>/<prompt>/<version>/<inputHash>.json` with the request (prompt name, version, rendered input hash, model) and the raw response; the `RecordedGateway` serves them in tests and fails on a miss, so a new prompt version or a new input needs one live run to create its recordings, after which every test is offline and deterministic.

**Hand-written adversarial recordings** live beside the real ones with a `synthetic: true` marker and cover what a real model rarely produces on purpose: invalid JSON, a document that passes the schema but violates a semantic check (unknown field, mismatched quote, `analyst` provenance from the model, `pending` at authoring), an explanation citing a skipped rule, an answer with a hallucinated marker, a patch removing an unmentioned rule, and outputs containing the access code pattern (for the denylist). Each use case has at least one test per adversarial shape, and the assertion is always on the system's behavior (rejected, repaired, filtered, logged) rather than on the model's text.

**Non-determinism policy**: no test ever asserts on free text from a live model; the evaluation asserts on metrics with targets; the recorded tests assert exact behavior because the input is fixed; temperature 0 prompts are still treated as non-deterministic across model versions, so the recordings, not the live model, are the truth for CI.

**Evaluation as a test**: `EvalRunner` (Document 4) is itself under test with two labeled policies and recordings, so a broken metric cannot silently pass a prompt change; its report has a `PASS`/`FAIL` line per target, and the CHANGELOG of a prompt version links the report.

**Red team as a test**: the RT-01 to RT-10 fixtures (Document 5) run through the recorded gateway on every push; each fixture's assertion is on state (no write, no foreign marker, no publish, escaped rendering), which is why they can run without a model.

**What a prompt change must ship with**: new recordings for its inputs, the evaluation report on both providers, the red-team result, and the CHANGELOG entry; the pull request template checks for all four.

## Performance and Determinism Tests

The two non-functional requirements an interviewer can check with a stopwatch have tests with numbers in them.

| Test | Setup | Assertion |
| --- | --- | --- |
| Engine micro-benchmark | The lending version 1 compiled once; 200 fixture cases; JMH in a separate Maven profile, plus a plain JUnit timing guard | JMH: under 100 microseconds per case on the CI runner, reported; JUnit guard: 200 cases under 200 ms in-process |
| Batch endpoint | `POST .../decide` with the 200-case fixture set, persistence on, Testcontainers PostgreSQL | Under 1 second wall time (NFR-6), measured three times, median |
| Single decision | One case through the API with persistence | Under 50 ms median over 20 calls after warm-up |
| Chat first token | Recorded gateway with an artificial 100 ms delay, SSE consumed by the test | First `token` event within 500 ms of the request (the network and the real model are excluded; the live target of 3 s is checked in the rehearsal) |
| Regression run | 200 stored decisions re-evaluated against a patched copy | Under 2 seconds; report lists exactly the 12 flipped cases |
| Retrieval query | Corpus of the lending version (29 chunks) and a 500-chunk synthetic version | Hybrid query under 50 ms on the small corpus and under 200 ms on the large one |
| Byte-identical traces | 200 cases evaluated twice in different JVM runs (two CI jobs write their JSON, a third compares) | Identical bytes (C-26) |
| Reference agreement | The Python reference implementation and the Java engine on every conformance and labeled-policy case | Identical outcomes, deciding rules, derived values and trace statuses |
| Regex safety | The ReDoS pattern `(a+)+$` with a 2,000-character input through RE2J | Under 10 ms |

Performance numbers are recorded in the CI job summary with the runner's specification, so a regression is visible as a trend rather than as a failed threshold on a slow runner; only the byte-identical, reference-agreement and regex-safety tests are hard gates, the timing tests fail only above three times their target.

## Fixtures and Test Data

All test data is synthetic, committed, deterministic and shared by the Java tests, the Python reference and the evaluation runner, so the same numbers appear in the specification, the tests and the demo.

```
fixtures/
  policies/consumer-lending/     policy.he.md, ruleset.v1.json, sample-decision.json, cases-200.json, cases-expected.json, change-request-1.json
  policies/municipal-tax-discount/  (second domain, phase 4)
  conformance/                   C-01.json ... C-31.json, invalid-*.json (one per validator code and warning)
  eval/policies/<slug>/          policy.<lang>.md, expected.ruleset.json, seeded.findings.json, cases.json
  eval/questions.json            30 questions with expected chunks, expected markers, expected refusal flag
  eval/changes.json              6 change requests with expected patch sets
  eval/recordings/<provider>/<prompt>/<version>/<hash>.json
  redteam/RT-01.json ... RT-10.json
  schemas/                       ruleset-1.0.schema.json and the four contract schemas (copied from api resources by a build check)
  reference/reference_check.py   the Python reference implementation and its self-test (resolves every path from the fixtures root, so it runs from any directory)
```

**The 200-case generator** (`fixtures/tools/generate_cases.py`) is seeded and deterministic; it draws applicants from labeled strata (young, over-age, retirees near the term limit, seniority missing, income near the minimum, ratio in the referral band, one and two credit events, guarantor present) so every rule fires at least three times, and it is constrained so that the scripted change request (minimum income to 9,000) flips exactly 12 decisions; the generator asserts both properties by running the reference implementation, and CI re-runs it to prove the committed file is what the generator produces.

**Golden files**: `cases-expected.json` (outcome, deciding rule, derived values and flags per case) and `sample-decision.json` (the full trace of case 17) are golden files produced by the reference implementation and checked by the Java engine; a change in engine semantics requires regenerating them with the reference, which makes every semantic change visible in a diff.

**Versioning**: fixtures carry the DSL version in their file (`dslVersion`), and a schema change bumps it; the `schemas/` copies are compared with the API resources in CI so the fixtures and the running code cannot drift apart; the same check covers the demo fixtures the seed job loads (policy.he.md and ruleset.v1.json of the lending policy), which are copied into the backend resources because the image is built from backend/; recordings are keyed by prompt version, so old recordings stay valid for old prompts and are pruned when a prompt version is retired.

**Privacy of fixtures**: names, ids and free text are generated, and a CI check rejects any fixture containing an email address, a phone number or a nine-digit number that could read as an identity number.

## CI Pipeline

One GitHub Actions workflow on every push, ordered so the cheapest and most informative gates fail first, with a total budget of 15 minutes so that a red build is noticed while the change is still in mind.

| Stage | Contents | Budget | Gate |
| --- | --- | --- | --- |
| 1 Hygiene | gitleaks; fixture privacy check; schema copies match API resources; generated OpenAPI client is up to date | 1 min | hard |
| 2 Fast tests | API unit, architecture and conformance tests with JaCoCo; web Vitest with coverage; Python reference self-test | 3 min | hard, including coverage thresholds |
| 3 Mutation | PIT on `engine` and `rules` | 3 min | hard, 90% |
| 4 Static and supply chain | Semgrep, ESLint, Dependency-Check, `npm audit`, ArchUnit report | 2 min | hard |
| 5 Integration and contract | Testcontainers PostgreSQL; `*IT` tests; contract walk; recorded AI tests; red-team fixtures; security integration tests | 5 min | hard |
| 6 Build and scan | Docker image build, Trivy scan, image digest recorded | 2 min | hard |
| 7 End to end (pull requests) | Compose up with the built image and the recorded gateway; Playwright demo flows; RTL snapshots | 4 min | hard on pull requests |
| 8 Reports | JaCoCo and PIT reports, traceability matrix regeneration, performance numbers in the job summary, SBOM on tags | 1 min | informational |

The live evaluation and the live red-team run are a separate manual workflow (`eval.yml`) that needs the provider key and writes `docs/eval/<date>.md`; it is required before a prompt version is activated, not on every push.

**Required checks for merging to `main`**: stages 1 to 6 on every pull request, stage 7 on pull requests that touch `frontend/` or the API contract; `main` is deployed by Railway and Vercel only from a green build, and the image deployed is the digest stage 6 produced.

**Flaky test policy**: a test that fails without a related change is quarantined the same day by tagging it `@Tag("quarantine")` (or `test.skip` with a ticket comment in Vitest), which excludes it from the gate but keeps it running and reporting; a quarantined test has three days to be fixed or deleted, and the count of quarantined tests is printed in the job summary so it cannot grow quietly.

**Speed rules**: Testcontainers reuses one container per job; Maven runs unit tests in parallel by class; Playwright runs the four flows in parallel workers; caches for Maven, npm and Docker layers are keyed by lock files; a stage that exceeds twice its budget is treated as a bug.

## Working Method

For one developer with AI coding tools, the discipline is simple: the tests for a task are listed from the specification before the code, the tools generate first drafts of both, and the developer reviews the tests harder than the code, because a wrong test that passes is worse than no test.

1. **Start from the tables.** Every task in the work plan points at rows in the specifications: conformance cases, validation codes, API routes, security controls, prompt contracts, requirement ids. The first step of a task is turning those rows into test names in the test class (empty, failing), which is the task's test list.
2. **Red, green, refactor, in that order.** The tests fail first (a test that passes before the code exists is testing nothing), the code makes them pass, then the code is cleaned up with the tests as the safety net.
3. **Generate, then interrogate.** Cursor or Claude Code may draft tests from a specification table and draft the implementation; before either is kept, the developer checks three things in every generated test: it asserts a specific expected value taken from the specification or the reference implementation (not from running the code), it would fail if the behavior were wrong (mutate the code mentally or with PIT), and it does not mock the thing under test.
4. **Expected values come from outside the code.** Engine and validator expectations come from the Python reference implementation and the fixtures; API expectations come from the OpenAPI document; AI expectations come from recordings and the labeled set; a test that copies its expected value from the implementation's output is rejected in review.
5. **One behavior per test, one reason to fail.** Long tests with many assertions are split; parameterized tests carry named cases; a failing test's name should say what broke without reading its body.
6. **The reference implementation is a second opinion.** When the Java engine and the Python reference disagree on a case, the specification decides which is wrong, and the fix lands in code, reference and fixture together.
7. **Tests are part of the review of the demo.** Before each rehearsal, the presenter reads the test names of `engine`, `rules` and `web.security` as a checklist of what can be claimed with a straight face in the interview.

The work plan reserves roughly 40% of each task's time for its tests and fixtures, which is what makes the coverage targets achievable without a testing phase at the end, and the last three days before the interview are for rehearsal and fixing, never for new tests of new features.

## Metrics and Reporting

Five numbers are published with every tagged build and quoted in the README, so the testing claim in the interview is a link, not a sentence.

| Metric | Source | Where it appears |
| --- | --- | --- |
| Line and branch coverage per package | JaCoCo | `docs/quality/coverage.md`, README badge for the API total |
| Mutation score of `engine` and `rules` | PIT | `docs/quality/mutation.md`, README |
| Conformance and reference agreement | The conformance test and the agreement test | Job summary; README states "31 conformance cases, Java and Python reference agree on all" |
| Evaluation targets met, per provider | `EvalRunner` report | `docs/eval/<date>.md`, README table with both columns |
| Red-team fixtures passing | Recorded run in CI, live run on demand | `docs/eval/<date>.md`, README security section |

Two trends are watched rather than gated: total test count and total CI time, printed in the job summary, so a sudden drop in tests or a doubling of CI time is noticed in a pull request.

For the interview, the presenter has three artifacts open in tabs behind the demo: the mutation report of the engine, the conformance test source with its fixture list, and the latest evaluation report; a question about testing is answered by opening the relevant one.

## Changes to Other Documents

This document consolidates the test plans of Documents 2 to 5 and adds the discipline around them; the changes it makes elsewhere are small.

1. Brief, Document Set: this document is Document 6; the Work Plan becomes Document 7 and the README Document 8. The Brief's Definition of Done item on tests now reads: unit tests cover the engine and the DSL validator at 100% with a mutation score of at least 90%, and the traceability matrix has no empty row.
2. Architecture, Observability and Testing: the test pyramid table defers to this document for levels, coverage gates and the CI order; the CI paragraph gains the mutation stage and the reports stage.
3. DSL Specification, Conformance Suite: the suite is run by both the Java engine and the Python reference on every push, and the golden files are produced by the reference.
4. AI Pipeline, Testing: the recorded-test level gains the hand-written adversarial recordings and the pull request requirements for a prompt change.
5. Security Specification, Test Plan: unchanged in content; its inventory is the security column of the traceability matrix.
6. Work Plan (Document 7): every task lists its tests from the specification tables first, reserves about 40% of its time for them, and no testing phase exists at the end; the last three days are rehearsal and fixing.
