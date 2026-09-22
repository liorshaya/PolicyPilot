# Evaluation run, 2026-09-23

Document 4, "Evaluation Set and Metrics". Prompt versions: `author` v1, `review` v1, `answer` v1. Targets are the strong model's; the Ollama column is reported without targets, to show the local path works and to quantify the gap.

| Metric | Target | openai (gpt-5.6-terra) | ollama | Verdict |
| --- | --- | --- | --- | --- |
| Rule precision | 0.90 | 0.10 (23 of 224) | not run | FAIL |
| Rule recall | 0.90 | 0.12 (23 of 198) | not run | FAIL |
| Provenance accuracy | 0.95 | 1.00 (23 of 23) | not run | PASS |
| Schema-valid first try | 0.90 | not run | not run | not run |
| Valid after repairs | 1.00 | not run | not run | not run |
| Case agreement | 0.95 | 0.00 (0 of 231) | not run | FAIL |
| Reviewer recall | 0.80 | 0.87 (78 of 90) | not run | PASS |
| Reviewer precision | 0.70 | at least 0.46 (78 of 170) | not run | undecided (needs an analyst pass) |
| Retrieval recall at 8 | 0.90 | 0.91 (21 of 23) | not run | PASS |
| Citation accuracy | 0.90 | 0.96 (22 of 23) | not run | PASS |
| Refusal accuracy | 0.90 | 0.93 (28 of 30) | not run | PASS |
| Change correctness | 0.83 | not run | not run | not run |
| Confidence calibration | reported | 1.00 (1 of 1) | not run | reported |

- Reviewer precision is the floor Document 4's definition allows a runner to compute: the findings answering a seeded defect over all of them. The other half, "confirmed real on inspection", needs a person, so a floor under the target settles nothing.
- Retrieval and refusal are measured on the vectors of text-embedding-3-small, recorded by the day 8 live pass; the rest of the column is the strong model's.
- Two prompt versions this run argues for, both held until evaluation run 2 on day 15: `author/v2` gives the model the field names instead of asking it to invent them, and `answer/v2` tells it that a question about how many or about which rules is a tool call and not a refusal. Each changes a rendered prompt, which is the response cache's key, so each costs the demo a re-warm; neither buys a demo moment, and gate G2 is due today.
- Author metrics cover 1 of the labeled policies, the ones whose authoring is recorded; reviewer metrics cover 18.
- Citation accuracy is scored over every one of the 23 questions that expect a marker, including any the Threshold stopped before the model: an answer that was never written did not cite its source. A marker counts as valid when the version can supply it: a paragraph the policy has, a rule the version has, or a decision or simulation a tool returned.
- Retrieval recall at 8 is counted as Document 4 words it: a question whose expected chunk is among the eight. Read strictly, as every expected chunk of a question, it is 16 of 23; counted chunk by chunk it is 48 expected chunks of which 38 were kept.
- Refusal accuracy counts both directions, on the answer rather than on how it was reached: 6 of 6 not-covered questions answered with the fixed sentence, and 22 of 24 covered questions not refused; the covered questions that were refused are Q-08, Q-14.

## Per policy

| Policy | Rules | Matched | Recall | Precision | Provenance | Cases |
| --- | --- | --- | --- | --- | --- | --- |
| consumer-lending | 19 | 2 | 0.11 | 0.11 | 1.00 | 0.00 |
| consumer-lending | 19 | 2 | 0.11 | 0.11 | 1.00 | 0.00 |
| consumer-lending | 21 | 2 | 0.11 | 0.10 | 1.00 | 0.00 |
| consumer-lending | 21 | 3 | 0.17 | 0.14 | 1.00 | 0.00 |
| consumer-lending | 18 | 2 | 0.11 | 0.11 | 1.00 | 0.00 |
| consumer-lending | 17 | 2 | 0.11 | 0.12 | 1.00 | 0.00 |
| consumer-lending | 22 | 3 | 0.17 | 0.14 | 1.00 | 0.00 |
| consumer-lending | 21 | 2 | 0.11 | 0.10 | 1.00 | 0.00 |
| consumer-lending | 21 | 2 | 0.11 | 0.10 | 1.00 | 0.00 |
| consumer-lending | 21 | 1 | 0.06 | 0.05 | 1.00 | 0.00 |
| consumer-lending | 24 | 2 | 0.11 | 0.08 | 1.00 | 0.00 |

## Mismatches

- consumer-lending R-010: the set value reads loan_amount, repayment_term_months (no field of that name is expected) _(11 runs)_
- consumer-lending R-020: it sets loan_end_age, not debt_to_income _(2 runs)_
- consumer-lending R-110: the condition reads employment_status (no field of that name is expected) _(8 runs)_
- consumer-lending R-115: the condition reads employment_status (no field of that name is expected) _(10 runs)_
- consumer-lending R-116: the condition reads employment_status (no field of that name is expected) _(10 runs)_
- consumer-lending R-120: the condition reads loan_amount (no field of that name is expected) _(4 runs)_
- consumer-lending R-130: the condition reads repayment_term_months (no field of that name is expected) _(4 runs)_
- consumer-lending R-140: the condition reads employment_status (no field of that name is expected) _(5 runs)_
- consumer-lending R-150: the condition reads current_employment_tenure_months, employment_status (no field of that name is expected) _(3 runs)_
- consumer-lending R-160: the condition reads employment_status, self_employment_tenure_months (no field of that name is expected) _(2 runs)_
- consumer-lending R-170: the condition reads employment_status (no field of that name is expected) _(6 runs)_
- consumer-lending R-200: the condition reads has_stable_income (no field of that name is expected) _(4 runs)_
- consumer-lending R-220: the condition reads debt_to_income_ratio (no field of that name is expected) _(4 runs)_
- consumer-lending R-320: the condition reads debt_to_income_ratio (no field of that name is expected) _(8 runs)_
- consumer-lending R-330: the condition reads debt_to_income_ratio (no field of that name is expected) _(8 runs)_
- consumer-lending R-420: the action is set monthly_installment, not flag STABLE_INCOME_MANUAL_CHECK _(11 runs)_
- consumer-lending R-020: it sets debt_to_income_ratio, not debt_to_income _(6 runs)_
- consumer-lending R-110: the condition reads loan_amount (no field of that name is expected) _(3 runs)_
- consumer-lending R-120: the condition reads employment_status (no field of that name is expected) _(6 runs)_
- consumer-lending R-130: the condition reads employment_status (no field of that name is expected) _(2 runs)_
- consumer-lending R-150: the condition reads employment_status (no field of that name is expected) _(4 runs)_
- consumer-lending R-160: the condition reads employment_status (no field of that name is expected) _(2 runs)_
- consumer-lending R-200: the condition reads monthly_net_income (no field of that name is expected) _(4 runs)_
- consumer-lending R-220: the condition reads negative_credit_events_last_24_months (no field of that name is expected) _(2 runs)_
- consumer-lending R-160: the condition reads employment_status, self_employment_duration_months (no field of that name is expected)
- consumer-lending R-330: the condition reads guarantor_provided, negative_credit_events_24_months (no field of that name is expected)
- consumer-lending R-130: the condition reads loan_amount (no field of that name is expected) _(4 runs)_
- consumer-lending R-140: the condition reads repayment_term_months (no field of that name is expected) _(2 runs)_
- consumer-lending R-160: the condition reads employee_tenure_months, employment_status (no field of that name is expected)
- consumer-lending R-170: the condition reads employment_status, self_employed_tenure_months (no field of that name is expected)
- consumer-lending R-100: the condition reads employment_status (no field of that name is expected)
- consumer-lending R-150: the condition reads employment_status, employment_tenure_months (no field of that name is expected)
- consumer-lending R-160: the condition reads employment_status, employment_tenure_months (no field of that name is expected) _(2 runs)_
- consumer-lending R-200: the condition is not equivalent after normalization _(2 runs)_
- consumer-lending R-220: the condition reads negative_credit_events_24_months (no field of that name is expected) _(4 runs)_
- consumer-lending R-320: the condition reads debt_to_income_band (no field of that name is expected) _(2 runs)_
- consumer-lending R-330: the condition reads debt_to_income_band (no field of that name is expected) _(2 runs)_
- consumer-lending R-020: it sets age_at_repayment_end, not debt_to_income _(2 runs)_
- consumer-lending R-120: the condition reads repayment_term_months (no field of that name is expected)
- consumer-lending R-140: the condition reads has_stable_income (no field of that name is expected)
- consumer-lending R-140: the condition reads loan_amount (no field of that name is expected) _(2 runs)_
- consumer-lending R-150: the condition reads repayment_term_months (no field of that name is expected) _(2 runs)_
- consumer-lending R-160: the condition reads repayment_term_months (no field of that name is expected) _(2 runs)_
- consumer-lending R-100: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-115: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-116: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-130: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-140: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-150: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-160: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-170: the condition reads applicant_age (no field of that name is expected)
- consumer-lending R-320: the condition reads guarantor_provided, negative_credit_events_24_months (no field of that name is expected)
- consumer-lending R-020: it sets repayment_end_age, not debt_to_income
- consumer-lending R-200: the condition reads stable_income (no field of that name is expected)
- consumer-lending R-220: the condition reads debt_to_income_band (no field of that name is expected)
- consumer-lending SF-3: no finding of kind unsupported on paragraphs [4] or rules [R-170]
- consumer-lending SF-4: no finding of kind gap on paragraphs [3] or rules [R-160]
- consumer-lending SF-5: no finding of kind duplicate on paragraphs [1] or rules [R-100, R-101]
- consumer-lending-guarantor SF-3: no finding of kind unsupported on paragraphs [5] or rules [R-180]
- rental-deposit-en SF-1: no finding of kind ambiguity on paragraphs [3] or rules [R-010]
- rental-deposit-en SF-2: no finding of kind conflict on paragraphs [2, 6] or rules [R-020]
- rental-deposit-en SF-3: no finding of kind unsupported on paragraphs [5] or rules [R-030]
- rental-deposit-return SF-1: no finding of kind ambiguity on paragraphs [3] or rules [R-020]
- rental-deposit-return SF-2: no finding of kind conflict on paragraphs [1, 6] or rules [R-900]
- scholarship-need SF-1: no finding of kind ambiguity on paragraphs [3] or rules [R-040]
- synthetic-referral-first SF-1: no finding of kind ambiguity on paragraphs [3] or rules [R-230]
- warranty-claim-electronics SF-1: no finding of kind ambiguity on paragraphs [3] or rules [R-110]
- Q-14 (citations): no answer was written; the retrieval threshold stopped the question
- Q-01 (consumer-lending): missed p:7, r:R-330; the question's own tool is getDecision
- Q-05 (consumer-lending): missed p:7
- Q-07 (consumer-lending): missed p:6
- Q-09 (consumer-lending): missed p:8
- Q-12 (consumer-lending): missed p:4
- Q-13 (consumer-lending): missed p:1
- Q-14 (consumer-lending): missed r:R-310, r:R-320, r:R-330, stopped by the threshold; the question's own tool is listRules
