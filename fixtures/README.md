# PolicyPilot fixtures

Test data shared by the Java engine, the Python reference implementation and the web app (Document 6,
Fixtures and Golden Files). Everything here is synthetic and deterministic; nothing depends on the network,
the clock or the locale.

```
fixtures/
  policies/consumer-lending/   policy.he.md, ruleset.v1.json, sample-decision.json, cases-200.json,
                               cases-expected.json, change-request-1.json
  conformance/                 C-01.json ... C-31.json (Document 3, Conformance Suite)
                               invalid-<CODE>.json, one per validator code and warning (Document 3, Static Validation)
  eval/policies/<slug>/        policy.<lang>.md, expected.ruleset.json, seeded.findings.json, cases.json (18 labeled policies)
  eval/questions.json          30 questions with expected chunks, markers, tool and refusal flag
  eval/changes.json            6 change requests with expected patch sets and regression flip counts
  eval/recordings/             created by live evaluation runs (Document 6); not committed until day 7
  schemas/                     ruleset-1.0.schema.json (+ the four contract schemas, copied from api resources by a build check)
  reference/reference_check.py the Python reference implementation and its self-test
  tools/generate_cases.py      the seeded 200-case generator
```

## Running

```
python3 fixtures/reference/reference_check.py      # self-test, conformance suite, invalid fixtures, evaluation-set admission: ends with ALL OK
python3 fixtures/tools/generate_cases.py           # rewrites cases-200.json and cases-expected.json; CI diffs them
```

## Evaluation set (Document 4)

Eighteen labeled policies, twelve in Hebrew and six in English: consumer lending (4, one of them the demo
policy), municipal tax discount (3), rental deposit (2), scholarship (3), warranty claim (2) and four
synthetic policies that each stress one feature (derived chains, dates and retirees, enums with many
values, a referral that precedes rejections on purpose). Per policy:

- `policy.<lang>.md`: the text, paragraphs separated by blank lines; the paragraph index is the position.
- `expected.ruleset.json`: the rules an expert writes from that text, in the DSL, with quoted provenance.
  It validates clean in the `PUBLISH` context (the reference asserts it). The demo policy's expected set is
  `ruleset.v1.json` without the two analyst rules (R-310, R-410), which the text does not contain.
- `cases.json`: 10 to 30 inputs with the outcome, deciding rule, derived values and flags the expected
  set produces; the reference reproduces every one, and the Java engine must too.
- `seeded.findings.json`: the defects the reviewer must find, each with `kind`, `paragraphIndexes`,
  `ruleIds` and `planted`. `text` means the defect is in the policy text (an undefined term, two
  paragraphs that contradict, an injected instruction). `ruleset` means the runner applies `mutation` to
  the expected set to build the draft the reviewer is given: `set` changes a threshold (an `unsupported`
  rule), `remove` drops a rule (a `gap`), `duplicate` adds a copy under a new id (a `duplicate`). Reviewer
  recall counts a seeded defect as found when a finding has the same kind and an overlapping anchor
  (a shared paragraph index or rule id).

`questions.json` entries carry the policy and the files the chat session runs on (`ruleset`,
`policyText`), `expectedChunks` (`p:<n>` and `r:<ruleId>` that retrieval must return; recall at 8),
`expectedMarkers` (citations the answer must carry; `[[sim:*]]` means any simulation marker),
`expectedTool` (one of `getDecision`, `getDecisionStats`, `listRules`, `getRule`, `simulate`, or null),
`refusal` (the fixed not-covered sentence is the right answer) and `answerContains` (words a correct
answer contains; a hint for human inspection, not an automatic metric). Questions on the demo policy
run against the published version 1, so they may cite R-310 and R-410.

`changes.json` entries carry the request text, the base `ruleset` and `policyText`, and the expected
candidates, `patches` (full replacement or new rules with `pending` provenance, or removals), `untouched`
list and `notes` (non-empty only for an impossible request, whose patch list is empty). `regression`
names the case file and the number of outcomes the patch flips; the reference computes the count, and
CR-1 is the scripted demo request with its 12 flips.

Both resolve paths from the fixtures root (the parent of their directory), so they run from any working
directory; an explicit root can be passed as the first argument. `pip install jsonschema` is the only dependency.

## File shapes

**Conformance case** (`conformance/C-nn.json`): `id`, `name`, `ruleset` (an inline rule set, or a path
relative to the fixtures root), optional `policyText` and `context` for validation, then either one check
at the top level or a `checks` list. A check has `case` (the input) and `expected`, which is matched as a
subset of the decision: every key listed must be present with the same value, lists must have the same
length and match element by element, so a partial trace step such as `{"ruleId": "R-100", "status":
"skipped"}` is enough. Three variants:

- `case` + `expected` with `status` `OK` or `ERROR`: the decision object of Document 3, or a case error
  as `{"status": "CASE_INVALID", "problems": [...]}`.
- `case` + `overrides` + `expected`: a simulation (`simulation: true`); the runner also proves the base
  evaluation is unchanged afterwards (C-31).
- `cases` (a path to a case file) + `expected: {"byteIdentical": true, "count": n}`: every case evaluated
  twice, serialized canonically, compared byte for byte (C-26).
- `traceStatus`, beside `expected`, checks the status of named steps when the whole trace is too long to list.

**Invalid rule set** (`conformance/invalid-<CODE>.json`): `code` (the expected validator code), `name`,
`context` (`AUTHORING`, `CHANGE_PROPOSAL`, `PUBLISH` or `ANALYST_EDIT`), optional `modelRuleIds`,
`policyText` and `alsoExpected`, and the `ruleset`. The runner asserts the code is reported with the severity
of Document 3's table and, for an error, that every reported finding belongs to the same layer: the validator
stops after the first layer that reports an error.

**Cases** (`cases-200.json`): `cases[]` with `id` (1 to 200), `stratum` and `input`; case 17 is the demo's
case (refer by R-330). **Expected** (`cases-expected.json`): per case `outcome`, `decidingRuleId`, `derived`
and `flags`, a `summary` with outcome counts and top deciding rules, and the `regression` block with the
12 decisions the scripted change request flips.

**Change request** (`change-request-1.json`): the scripted request text in both languages, the expected
candidates, the expected `patches` (full replacement rules with `pending` provenance) and `untouched` list,
and the expected flip count. The generator applies the patches with the reference implementation to prove the
count.
