# Rule match on the sample lending policy (Brief, Definition of Done line 3)

Run 1 (the canonical recording): 14 of 18 expected rules matched, 78%; over the ten runs: median 67%, lowest 56%. The line fails under both readings of "90%", for the canonical run and for the median run.

## Method

- **Inputs.** Policy: `fixtures/policies/consumer-lending/policy.he.md`, byte-identical to `fixtures/eval/policies/consumer-lending/policy.he.md`. Label: `fixtures/eval/policies/consumer-lending/expected.ruleset.json` (18 rules). Generated sets: `fixtures/eval/recordings/openai/author/v1/45e240b9…run1.json` to `…run10.json` (model `gpt-5.6-terra`, prompt `author` v1). The canonical file without a run suffix is byte-identical to `run1.json`. Cases for `set` expressions: `fixtures/eval/policies/consumer-lending/cases.json` (21 labeled cases).
- **Matching rule (Document 4, "Rule matching"), applied literally.** "A generated rule matches an expected rule when their actions are identical (outcome and terminal flag, or the same `set` target and an expression that evaluates equal on the policy's cases, or the same flag code) and their conditions are logically equivalent after normalization: combinators sorted, `not between` and two comparisons unified, `gte x` and `gt x-1` on integers unified, enum lists sorted." Ids, labels, priorities and reasons are ignored. Provenance counts as correct when the paragraph matches. Each expected rule can match at most one generated rule, and the reverse; the script finds a maximum bipartite matching.
- **Field mapping.** [`mappings.json`](rule-match-lending/mappings.json) maps each run's field names and enum values to the label's, with a one-line reason for each field. Examples: `loan_amount` to `requested_amount`, `employed`/`employee`/`salaried` to `salaried`, `retiree`/`pensioner`/`retired` to `retired`. Fields the label has no counterpart for map to nothing: stable income (p4), age at the end of the term (p8), `months_until_age_78` (p8), the debt-to-income band (p6). A condition or expression that reads such a field cannot match. The model's two tenure fields (salaried and self-employed) both map to the label's single `employment_months`.
- **Set expressions.** Each generated expression was renamed through the mapping and evaluated with the reference engine (`fixtures/reference/reference_check.py`, `ev_expr`, exact decimals) on all 21 labeled cases. The environment held the case inputs plus the label's own values for the other derived field, so each derivation was compared on its own. Equality had to be exact. An unrounded value against a rounded one is a mismatch, and the largest difference is reported.
- **What "match" required.** The same action (outcome and terminal flag, or the same set target with equal values), and conditions that are equal after mapping and the normalizations listed above. `between` counts as `gte` and `lte`. On an enum field, `eq`/`ne`/`in`/`not_in` count as the same sorted value set within the closed enum. Algebraic rearrangement and inlining a derived field were not accepted as normalization. Those two show up only in the sensitivity check at the end.

## Per run (strict reading, the result)

| Run | Rules generated | Expected matched | Recall | Precision (matched / generated) | Provenance correct among matched |
| --- | --- | --- | --- | --- | --- |
| 1 (canonical) | 19 | 14 of 18 | 77.8% | 73.7% | 14 of 14 |
| 2 | 21 | 12 of 18 | 66.7% | 57.1% | 12 of 12 |
| 3 | 18 | 14 of 18 | 77.8% | 77.8% | 14 of 14 |
| 4 | 17 | 11 of 18 | 61.1% | 64.7% | 11 of 11 |
| 5 | 22 | 12 of 18 | 66.7% | 54.5% | 12 of 12 |
| 6 | 21 | 12 of 18 | 66.7% | 57.1% | 12 of 12 |
| 7 | 21 | 11 of 18 | 61.1% | 52.4% | 11 of 11 |
| 8 | 21 | 12 of 18 | 66.7% | 57.1% | 12 of 12 |
| 9 | 24 | 10 of 18 | 55.6% | 41.7% | 10 of 10 |
| 10 | 21 | 12 of 18 | 66.7% | 57.1% | 12 of 12 |
| **Median / lowest** | | | **66.7% / 55.6%** | **57.1% / 41.7%** | 100% in every run |

## Canonical run (run 1), rule by rule

| Expected | What it says | Matched generated | Reason for a mismatch |
| --- | --- | --- | --- |
| R-010 | installment = Spitzer at 9%, rounded to 2 places | none | Generated R-010 has no `round`. It differs on all 21 cases, by up to 0.0047677 (case 8: 874.51 expected, 874.5147677 generated). |
| R-020 | debt-to-income = (existing + installment) / income, rounded to 4 places, if income > 0 | none | Generated R-030 has no `round`. It differs on all 21 cases, by up to 0.0000447 (case 3: 0.6766 expected, 0.6765553 generated). |
| R-100 | reject: age < 21 | R-100 | |
| R-110 | reject: age > 70 and not retired | R-110 | |
| R-115 | reject: retired and age > 75 | R-170 | |
| R-116 | reject: retired and age ≥ 78 − term/12 | none | Generated R-180 tests `loan_end_age ≥ 78`. That derived field has no counterpart in the label. The condition is equivalent only after inlining the derivation, which the listed normalizations do not cover. |
| R-120 | reject: amount outside 10,000 to 150,000 | R-120 | |
| R-130 | reject: term outside 12 to 84 months | R-130 | |
| R-140 | reject: unemployed | R-140 | |
| R-150 | reject: salaried with tenure < 6 months | R-150 | |
| R-160 | reject: self-employed active < 24 months | R-160 | |
| R-170 | reject: net income < 8,000 | R-210 | |
| R-200 | reject: debt-to-income > 40% | R-220 | |
| R-220 | reject: 2 or more credit events | R-230 | |
| R-320 | refer: debt-to-income between 35% and 40% | R-300 | |
| R-330 | refer: 1 credit event and no guarantor | R-310 | |
| R-420 | flag STABLE_INCOME_MANUAL_CHECK, always | none | No flag rule was generated. The model made stable income a boolean input instead and rejects on it (R-200). |
| R-900 | approve, always | R-900 | |

Generated rules left unmatched in run 1: R-010 and R-030 (the unrounded derivations), R-020 (sets `loan_end_age`), R-180 (reads `loan_end_age`), R-200 (rejects on `has_stable_income`, which the label has no rule for).

## Every mismatch, grouped by expected rule

- **R-010**, runs 1 to 10: no `round(…, 2)`; the value differs on all 21 cases, by up to 0.0047677. In runs 2 and 4 the condition is also `term > 0` instead of `always`, and in runs 3 and 8 it is `amount > 0 and term > 0`.
- **R-020**, runs 1 to 10: no `round(…, 4)`; the value differs on all 21 cases, by up to 0.0000447. The condition (`income > 0`) is identical.
- **R-116**, runs 1, 2, 5, 6, 7, 8, 9, 10: the condition reads a derived "age at end of term" field that has no counterpart (`loan_end_age`, `repayment_end_age` or `age_at_repayment_end`).
- **R-116**, run 3: the condition is `term ≥ months_until_age_78`, where `months_until_age_78` is an applicant-supplied input with no counterpart. This is not equivalent under any reading.
- **R-116**, run 4: the condition is `term ≥ (78 − age) × 12`. It is algebraically equivalent, but algebraic rearrangement is not one of the listed normalizations.
- **R-120**, runs 2, 6, 7, 8, 9, 10: split into two rules (`amount < 10,000` and `amount > 150,000`). Neither rule alone is equivalent, and a match is one to one.
- **R-130**, runs 2, 6, 7, 8, 9, 10: split into two rules (`term < 12` and `term > 84`), for the same reason.
- **R-100**, run 4: merged with R-110 into one rule, `not retired and (age < 21 or age > 70)`.
- **R-110**, run 4: the same merged rule; no generated rule is `age > 70 and not retired`.
- **R-115**, run 4: generated `retired and (age < 21 or age > 75)` adds the under-21 branch, so it is not equivalent.
- **R-200**, runs 5 and 9: the condition reads a derived band field (`debt_to_income_band = above_40_percent`) with no counterpart.
- **R-320**, runs 5 and 9: the same band field (`= from_35_to_40_percent`).
- **R-330**, run 7: the condition is `credit events = 1` only. The run has no guarantor field, so the "no guarantor" half is missing.
- **R-420**, runs 1 to 10: no run generates any flag rule. Every run instead declares a boolean stable-income input and rejects when it is false.

Generated rules that match nothing, in every run: the two unrounded derivations and the stable-income rejection. Most runs also have the end-of-term-age derivation and its rule (runs 5 and 9 also have three band-setting rules), and 6 of 10 runs have the split range rules.

## Which "90%" the pass/fail line uses

The Brief says "at least 90% of the rules in the generated set match the labeled expected rules". This has two readings, and Document 4 sets both at 0.90:

- **Recall** = expected rules matched / 18 (Document 4 "Rule recall").
- **Precision** = generated rules matched / generated rules (Document 4 "Rule precision"). This is the more literal reading of "of the rules in the generated set".

| | Recall | Passes (≥ 90%)? | Precision | Passes (≥ 90%)? |
| --- | --- | --- | --- | --- |
| Canonical run (run 1) | 77.8% | no | 73.7% | no |
| Median run | 66.7% | no | 57.1% | no |

**The line fails under both readings, for the canonical run and for the median run.**

## Sensitivity (not the result)

These numbers come from the same script. They show how much depends on the strict choices above.

| Reading | Run 1 recall / precision | Median recall / precision | Lowest recall |
| --- | --- | --- | --- |
| Strict (the result) | 77.8% / 73.7% | 66.7% / 57.1% | 55.6% |
| + semantic conditions: derived fields inlined; agreement checked exhaustively over integer and enum domains and at boundary values for numbers | 83.3% / 78.9% | 72.2% / 61.9% | 66.7% |
| + rounding ignored as well (`round` removed from both sides, relative tolerance 1e-9) | 94.4% / 89.5% | 83.3% / 71.4% | 77.8% |

Even the most lenient reading only gets run 1's recall over 90%. Its precision (89.5%) stays under, and the median run fails on both.

Reproduce with `python3 docs/eval/rule-match-lending/rulematch.py`, which reads the field mapping from [`mappings.json`](rule-match-lending/mappings.json) beside it; it takes about two minutes because of the grid.
