# Evaluation run, 2026-09-26

Document 4, "Evaluation Set and Metrics". Prompt versions: `author` v2, `review` v1, `answer` v3, `change` v2. Targets are the strong model's; the Ollama column is reported without targets, to show the local path works and to quantify the gap.

| Metric | Target | openai (gpt-5.6-terra) | ollama (qwen3:30b-a3b-instruct-2507-q4_K_M) | Verdict |
| --- | --- | --- | --- | --- |
| Rule precision | 0.90 | 0.34 (73 of 217) | 0.31 (37 of 121) | FAIL |
| Rule recall | 0.90 | 0.43 (73 of 170) | 0.22 (37 of 170) | FAIL |
| Provenance accuracy | 0.95 | 0.97 (71 of 73) | 1.00 (37 of 37) | PASS |
| Schema-valid first try | 0.90 | 1.00 (18 of 18) | 0.61 (11 of 18) | PASS |
| Valid after repairs | 1.00 | 1.00 (18 of 18) | at least 0.06 (1 of 18) | PASS |
| Case agreement | 0.95 | 0.67 (144 of 216) | 0.08 (17 of 216) | FAIL |
| Reviewer recall | 0.80 | 0.87 (78 of 90) | 0.56 (50 of 90) | PASS |
| Reviewer precision | 0.70 | at least 0.46 (78 of 170) | at least 0.53 (50 of 94) | undecided (needs an analyst pass) |
| Retrieval recall at 8 | 0.90 | 0.91 (21 of 23) | 0.96 (22 of 23) | PASS |
| Citation accuracy | 0.90 | 0.96 (22 of 23) | 0.87 (20 of 23) | PASS |
| Refusal accuracy | 0.90 | 0.97 (29 of 30) | 0.93 (28 of 30) | PASS |
| Change correctness | 0.83 | 1.00 (6 of 6) | 0.50 (3 of 6) | PASS |
| Confidence calibration | reported | 1.00 (1 of 1) | 1.00 (1 of 1) | reported |

## openai (gpt-5.6-terra)

- Reviewer precision is the floor Document 4's definition allows a runner to compute: the findings answering a seeded defect over all of them. The other half, "confirmed real on inspection", needs a person, so a floor under the target settles nothing.
- Retrieval and refusal are measured on the vectors of text-embedding-3-small, recorded by a live retrieval pass; the rest of the column is the openai model's.
- Each prompt version's changelog under backend/src/main/resources/prompts/ says why it exists and compares it with the version before on the metrics it was written for.
- Author metrics cover 18 of the labeled policies, the ones whose authoring is recorded; reviewer metrics cover 18.
- Citation accuracy is scored over every one of the 23 questions that expect a marker, including any the Threshold stopped before the model: an answer that was never written did not cite its source. A marker counts as valid when the version can supply it: a paragraph the policy has, a rule the version has, or a decision or simulation a tool returned.
- Retrieval recall at 8 is counted as Document 4 words it: a question whose expected chunk is among the eight. Read strictly, as every expected chunk of a question, it is 16 of 23; counted chunk by chunk it is 48 expected chunks of which 38 were kept.
- Refusal accuracy counts both directions, on the answer rather than on how it was reached: 6 of 6 not-covered questions answered with the fixed sentence, and 23 of 24 covered questions not refused; the covered questions that were refused are Q-14.

### Per policy

| Policy | Rules | Matched | Recall | Precision | Provenance | Cases |
| --- | --- | --- | --- | --- | --- | --- |
| arnona-discount-income | 14 | 3 | 0.33 | 0.21 | 1.00 | 0.70 |
| arnona-discount-seniors | 11 | 4 | 0.44 | 0.36 | 0.75 | 0.80 |
| consumer-lending | 21 | 10 | 0.56 | 0.48 | 1.00 | 0.95 |
| consumer-lending-en | 20 | 8 | 0.53 | 0.40 | 1.00 | 0.93 |
| consumer-lending-guarantor | 17 | 7 | 0.50 | 0.41 | 1.00 | 0.00 |
| consumer-lending-strict | 16 | 8 | 0.53 | 0.50 | 1.00 | 0.94 |
| municipal-tax-discount-en | 12 | 3 | 0.33 | 0.25 | 1.00 | 0.80 |
| rental-deposit-en | 10 | 3 | 0.50 | 0.30 | 1.00 | 0.30 |
| rental-deposit-return | 15 | 2 | 0.29 | 0.13 | 1.00 | 0.10 |
| scholarship-en | 8 | 5 | 0.56 | 0.63 | 0.80 | 0.80 |
| scholarship-merit | 12 | 2 | 0.20 | 0.17 | 1.00 | 0.75 |
| scholarship-need | 9 | 3 | 0.33 | 0.33 | 1.00 | 0.82 |
| synthetic-derived-chain | 7 | 2 | 0.29 | 0.29 | 1.00 | 1.00 |
| synthetic-enums | 9 | 6 | 0.86 | 0.67 | 1.00 | 1.00 |
| synthetic-referral-first | 5 | 2 | 0.50 | 0.40 | 1.00 | 1.00 |
| synthetic-retirees-dates | 10 | 1 | 0.11 | 0.10 | 1.00 | 0.80 |
| warranty-claim-electronics | 11 | 1 | 0.14 | 0.09 | 1.00 | 0.00 |
| warranty-claim-en | 10 | 3 | 0.50 | 0.30 | 1.00 | 0.00 |

### Mismatches

- arnona-discount-income R-010: it sets effective_persons, not counted_persons
- arnona-discount-income R-020: it sets income_per_person, not income_per_capita
- arnona-discount-income R-030: the condition reads income_per_person (no field of that name is expected)
- arnona-discount-income R-040: the condition reads income_per_person (no field of that name is expected)
- arnona-discount-income R-050: the condition reads income_per_person (no field of that name is expected)
- arnona-discount-income R-120: the condition reads income_per_person (no field of that name is expected)
- arnona-discount-seniors R-010: it sets eligible_discount_area_sqm, not discount_rate
- arnona-discount-seniors R-020: it sets discount_percentage, not discount_rate
- arnona-discount-seniors R-100: the condition reads is_registered_residence (no field of that name is expected)
- arnona-discount-seniors R-120: the condition is not equivalent after normalization
- arnona-discount-seniors R-410: the action is set eligible_discount_area_sqm, not flag AREA_CAP_100
- consumer-lending R-010: the set value differs on 21 of 21 cases; first on case 1 (expected 2289.24, generated 2289.243220370523)
- consumer-lending R-020: it sets debt_to_income_ratio, not debt_to_income
- consumer-lending R-116: the condition reads stable_income (no field of that name is expected)
- consumer-lending R-120: the condition reads stable_income (no field of that name is expected)
- consumer-lending R-130: the condition reads stable_income (no field of that name is expected)
- consumer-lending R-200: the condition reads debt_to_income_ratio (no field of that name is expected)
- consumer-lending R-320: the condition reads debt_to_income_ratio (no field of that name is expected)
- consumer-lending R-420: the action is set monthly_installment, not flag STABLE_INCOME_MANUAL_CHECK
- consumer-lending-en R-010: the set value differs on 15 of 15 cases; first on case 1 (expected 1305.76, generated 1305.763427880000)
- consumer-lending-en R-020: it sets debt_to_income_ratio, not debt_to_income
- consumer-lending-en R-100: the condition is not equivalent after normalization
- consumer-lending-en R-120: the condition is not equivalent after normalization
- consumer-lending-en R-130: the condition is not equivalent after normalization
- consumer-lending-en R-200: the condition is not equivalent after normalization
- consumer-lending-en R-320: the condition reads guarantor_reasonable_repayment_history (no field of that name is expected)
- consumer-lending-guarantor R-010: the set value differs on 16 of 16 cases; first on case 1 (expected 1660.67, generated 1660.668418108321)
- consumer-lending-guarantor R-020: it sets debt_to_income_ratio, not debt_to_income
- consumer-lending-guarantor R-110: the condition is not equivalent after normalization
- consumer-lending-guarantor R-120: the condition is not equivalent after normalization
- consumer-lending-guarantor R-130: the condition is not equivalent after normalization
- consumer-lending-guarantor R-170: the condition reads guarantor_is_suitable (no field of that name is expected)
- consumer-lending-guarantor R-200: the condition reads guarantor_is_suitable (no field of that name is expected)
- consumer-lending-strict R-010: the set value differs on 16 of 16 cases; first on case 1 (expected 1442.35, generated 1442.348630400000)
- consumer-lending-strict R-020: it sets debt_to_income_ratio, not debt_to_income
- consumer-lending-strict R-100: the condition is not equivalent after normalization
- consumer-lending-strict R-110: the condition is not equivalent after normalization
- consumer-lending-strict R-140: the condition is not equivalent after normalization
- consumer-lending-strict R-200: the condition is not equivalent after normalization
- consumer-lending-strict R-320: the condition is not equivalent after normalization
- municipal-tax-discount-en R-005: it sets student_discount_rate_percent, not discount_rate
- municipal-tax-discount-en R-010: it sets student_discount_rate_percent, not discount_rate
- municipal-tax-discount-en R-020: it sets student_discount_rate_percent, not discount_rate
- municipal-tax-discount-en R-030: it sets reservist_discount_rate_percent, not discount_rate
- municipal-tax-discount-en R-100: the condition reads institution_recognized, is_full_time_student, lives_in_apartment (no field of that name is expected)
- municipal-tax-discount-en R-410: the action is set student_discount_rate_percent, not flag AREA_CAP_120
- rental-deposit-en R-010: it sets late_notice_forfeiture, not refund_amount
- rental-deposit-en R-020: it sets refund_timing_days, not refund_amount
- rental-deposit-en R-030: it sets refund_timing_days, not refund_amount
- rental-deposit-return R-010: it sets notice_deduction, not years_held
- rental-deposit-return R-020: it sets notice_deduction, not refund_amount
- rental-deposit-return R-030: it sets notice_deduction, not refund_amount
- rental-deposit-return R-300: the condition reads damage_review_threshold (no field of that name is expected)
- rental-deposit-return R-310: the condition reads damage_review_threshold (no field of that name is expected)
- scholarship-en R-010: it sets excellence_scholarship_amount, not scholarship_amount
- scholarship-en R-020: it sets need_supplement_amount, not scholarship_amount
- scholarship-en R-130: the condition is not equivalent after normalization
- scholarship-en R-140: the condition is not equivalent after normalization
- scholarship-merit R-010: it sets base_scholarship_amount, not scholarship_amount
- scholarship-merit R-020: it sets base_scholarship_amount, not scholarship_amount
- scholarship-merit R-030: it sets base_scholarship_amount, not scholarship_amount
- scholarship-merit R-100: the condition is not equivalent after normalization
- scholarship-merit R-120: the condition is not equivalent after normalization
- scholarship-merit R-130: the condition is not equivalent after normalization
- scholarship-merit R-140: the condition is not equivalent after normalization
- scholarship-merit R-300: the condition is not equivalent after normalization
- scholarship-need R-010: it sets per_capita_income, not income_per_capita
- scholarship-need R-020: the condition reads per_capita_income (no field of that name is expected)
- scholarship-need R-030: the condition reads per_capita_income (no field of that name is expected)
- scholarship-need R-040: the condition reads per_capita_income (no field of that name is expected)
- scholarship-need R-110: the condition reads per_capita_income (no field of that name is expected)
- scholarship-need R-120: the condition reads per_capita_income (no field of that name is expected)
- synthetic-derived-chain R-010: it sets base_commission, not base_fee
- synthetic-derived-chain R-020: it sets commission_after_discount, not discounted_fee
- synthetic-derived-chain R-030: it sets final_commission, not discounted_fee
- synthetic-derived-chain R-040: it sets base_commission, not final_fee
- synthetic-derived-chain R-300: the condition reads final_commission (no field of that name is expected)
- synthetic-enums R-120: the condition is not equivalent after normalization
- synthetic-referral-first R-220: the condition reads high_amount (no field of that name is expected)
- synthetic-referral-first R-230: the condition reads high_amount (no field of that name is expected)
- synthetic-retirees-dates R-010: it sets age_months, not age_years
- synthetic-retirees-dates R-020: it sets tenure_months, not seniority_months
- synthetic-retirees-dates R-030: it sets request_lead_months, not notice_months
- synthetic-retirees-dates R-100: the condition reads request_lead_months (no field of that name is expected)
- synthetic-retirees-dates R-110: the condition reads tenure_months (no field of that name is expected)
- synthetic-retirees-dates R-120: the condition reads age_months, tenure_months (no field of that name is expected)
- synthetic-retirees-dates R-130: the condition reads age_months, shift_worker, tenure_months (no field of that name is expected)
- synthetic-retirees-dates R-300: the condition reads age_months, tenure_months (no field of that name is expected)
- warranty-claim-electronics R-010: it sets warranty_months, not months_since_purchase
- warranty-claim-electronics R-020: it sets warranty_months, not cost_ratio
- warranty-claim-electronics R-100: the condition reads warranty_months (no field of that name is expected)
- warranty-claim-electronics R-110: the condition is not equivalent after normalization
- warranty-claim-electronics R-300: the condition reads repair_cost_threshold (no field of that name is expected)
- warranty-claim-electronics R-310: the condition is not equivalent after normalization
- warranty-claim-en R-010: it sets coverage_months, not months_since_delivery
- warranty-claim-en R-100: the condition reads coverage_months (no field of that name is expected)
- warranty-claim-en R-110: the condition is not equivalent after normalization
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

## ollama (qwen3:30b-a3b-instruct-2507-q4_K_M)

- Valid after repairs counts the runs valid on their first answer: the live pass records no repair, so the 17 that were not might still have ended valid within two.
- Reviewer precision is the floor Document 4's definition allows a runner to compute: the findings answering a seeded defect over all of them. The other half, "confirmed real on inspection", needs a person, so a floor under the target settles nothing.
- Retrieval and refusal are measured on the vectors of bge-m3, recorded by a live retrieval pass; the rest of the column is the ollama model's.
- Each prompt version's changelog under backend/src/main/resources/prompts/ says why it exists and compares it with the version before on the metrics it was written for.
- Author metrics cover 18 of the labeled policies, the ones whose authoring is recorded; reviewer metrics cover 18.
- Citation accuracy is scored over every one of the 23 questions that expect a marker, including any the Threshold stopped before the model: an answer that was never written did not cite its source. A marker counts as valid when the version can supply it: a paragraph the policy has, a rule the version has, or a decision or simulation a tool returned.
- Retrieval recall at 8 is counted as Document 4 words it: a question whose expected chunk is among the eight. Read strictly, as every expected chunk of a question, it is 19 of 23; counted chunk by chunk it is 48 expected chunks of which 42 were kept.
- Refusal accuracy counts both directions, on the answer rather than on how it was reached: 6 of 6 not-covered questions answered with the fixed sentence, and 22 of 24 covered questions not refused; the covered questions that were refused are Q-08, Q-14.

### Per policy

| Policy | Rules | Matched | Recall | Precision | Provenance | Cases |
| --- | --- | --- | --- | --- | --- | --- |
| arnona-discount-income | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| arnona-discount-seniors | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| consumer-lending | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| consumer-lending-en | 12 | 7 | 0.47 | 0.58 | 1.00 | 0.00 |
| consumer-lending-guarantor | 13 | 7 | 0.50 | 0.54 | 1.00 | 0.06 |
| consumer-lending-strict | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| municipal-tax-discount-en | 13 | 3 | 0.33 | 0.23 | 1.00 | 0.00 |
| rental-deposit-en | 14 | 3 | 0.50 | 0.21 | 1.00 | 0.00 |
| rental-deposit-return | 11 | 1 | 0.14 | 0.09 | 1.00 | 0.00 |
| scholarship-en | 7 | 3 | 0.33 | 0.43 | 1.00 | 0.00 |
| scholarship-merit | 14 | 2 | 0.20 | 0.14 | 1.00 | 0.00 |
| scholarship-need | 9 | 3 | 0.33 | 0.33 | 1.00 | 0.00 |
| synthetic-derived-chain | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| synthetic-enums | 13 | 1 | 0.14 | 0.08 | 1.00 | 0.57 |
| synthetic-referral-first | 5 | 4 | 1.00 | 0.80 | 1.00 | 0.80 |
| synthetic-retirees-dates | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| warranty-claim-electronics | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| warranty-claim-en | 10 | 3 | 0.50 | 0.30 | 1.00 | 0.00 |

### Mismatches

- arnona-discount-income: the first answer fails the schema, DSL_SCHEMA at /fields/0/values
- arnona-discount-seniors: the first answer fails the schema, DSL_SCHEMA at /id
- consumer-lending: the first answer fails the schema, DSL_SCHEMA at /id
- consumer-lending-en R-010: the set value reads monthly_interest_rate (no field of that name is expected)
- consumer-lending-en R-020: the generated rule has 2 actions, not 1
- consumer-lending-en R-100: the condition is not equivalent after normalization
- consumer-lending-en R-110: the condition is not equivalent after normalization
- consumer-lending-en R-120: the condition is not equivalent after normalization
- consumer-lending-en R-130: the condition is not equivalent after normalization
- consumer-lending-en R-200: the condition is not equivalent after normalization
- consumer-lending-en R-320: the condition is not equivalent after normalization
- consumer-lending-guarantor R-010: the action is reject (terminal), not set monthly_installment
- consumer-lending-guarantor R-020: the action is reject (terminal), not set debt_to_income
- consumer-lending-guarantor R-100: the condition is not equivalent after normalization
- consumer-lending-guarantor R-110: the condition reads debt_to_income_ratio (no field of that name is expected)
- consumer-lending-guarantor R-170: the condition reads debt_to_income_ratio (no field of that name is expected)
- consumer-lending-guarantor R-200: the condition reads debt_to_income_ratio (no field of that name is expected)
- consumer-lending-guarantor R-900: the action is refer (terminal), not approve (terminal)
- consumer-lending-strict: the first answer fails the schema, DSL_SCHEMA at /rules/6/condition/not
- municipal-tax-discount-en R-010: the set value differs on 10 of 10 cases; first on case 1 (expected null, generated 0)
- municipal-tax-discount-en R-020: the set value differs on 10 of 10 cases; first on case 1 (expected null, generated 0)
- municipal-tax-discount-en R-030: the set value differs on 10 of 10 cases; first on case 1 (expected null, generated 0)
- municipal-tax-discount-en R-100: the condition is not equivalent after normalization
- municipal-tax-discount-en R-300: the action is reject (terminal), not refer (terminal)
- municipal-tax-discount-en R-410: the action is set discount_rate, not flag AREA_CAP_120
- rental-deposit-en R-010: it sets damage_cost, not refund_amount
- rental-deposit-en R-020: it sets unpaid_amount, not refund_amount
- rental-deposit-en R-030: it sets deposit, not refund_amount
- rental-deposit-return R-010: it sets years_since_deposit, not years_held
- rental-deposit-return R-020: it sets days_since_deposit, not refund_amount
- rental-deposit-return R-030: it sets deposit_older_than_5_years, not refund_amount
- rental-deposit-return R-100: the condition is not equivalent after normalization
- rental-deposit-return R-300: the condition reads damage_exceeds_half_deposit (no field of that name is expected)
- rental-deposit-return R-310: the condition reads deposit_older_than_5_years (no field of that name is expected)
- scholarship-en R-010: it sets gpa, not scholarship_amount
- scholarship-en R-020: it sets credits, not scholarship_amount
- scholarship-en R-120: the condition is not equivalent after normalization
- scholarship-en R-130: the condition is not equivalent after normalization
- scholarship-en R-140: the condition is not equivalent after normalization
- scholarship-en R-300: the condition is not equivalent after normalization
- scholarship-merit R-010: it sets gpa, not scholarship_amount
- scholarship-merit R-020: it sets credits, not scholarship_amount
- scholarship-merit R-030: it sets gpa, not scholarship_amount
- scholarship-merit R-100: the condition is not equivalent after normalization
- scholarship-merit R-120: the condition is not equivalent after normalization
- scholarship-merit R-130: the condition is not equivalent after normalization
- scholarship-merit R-140: the condition is not equivalent after normalization
- scholarship-merit R-300: the condition is not equivalent after normalization
- scholarship-need R-010: it sets per_capita_income, not income_per_capita
- scholarship-need R-020: it sets eligible_by_special_category, not scholarship_amount
- scholarship-need R-030: it sets eligible_by_income, not scholarship_amount
- scholarship-need R-040: it sets eligible_by_other_scholarship, not scholarship_amount
- scholarship-need R-110: the condition is not equivalent after normalization
- scholarship-need R-120: the condition reads per_capita_income (no field of that name is expected)
- synthetic-derived-chain: the first answer fails the schema, DERIVED_REQUIRED at /fields/1
- synthetic-enums R-100: the condition is not equivalent after normalization
- synthetic-enums R-110: the condition is not equivalent after normalization
- synthetic-enums R-120: the condition is not equivalent after normalization
- synthetic-enums R-130: the condition is not equivalent after normalization
- synthetic-enums R-140: the condition is not equivalent after normalization
- synthetic-enums R-300: the condition is not equivalent after normalization
- synthetic-retirees-dates: the first answer fails the schema, DSL_SCHEMA at /id
- warranty-claim-electronics: the first answer fails the schema, DSL_SCHEMA at /rules/5/condition
- warranty-claim-en R-010: it sets coverage_period_months, not months_since_delivery
- warranty-claim-en R-100: the condition reads coverage_end_date (no field of that name is expected)
- warranty-claim-en R-110: the condition is not equivalent after normalization
- arnona-discount-income SF-4: no finding of kind gap on paragraphs [6] or rules [R-110]
- arnona-discount-seniors SF-4: no finding of kind gap on paragraphs [4] or rules [R-300]
- consumer-lending SF-1: no finding of kind ambiguity on paragraphs [4] or rules [R-420]
- consumer-lending SF-2: no finding of kind conflict on paragraphs [1, 8] or rules [R-110, R-115]
- consumer-lending SF-3: no finding of kind unsupported on paragraphs [4] or rules [R-170]
- consumer-lending SF-4: no finding of kind gap on paragraphs [3] or rules [R-160]
- consumer-lending SF-5: no finding of kind duplicate on paragraphs [1] or rules [R-100, R-101]
- consumer-lending-en SF-5: no finding of kind duplicate on paragraphs [1] or rules [R-100, R-101]
- consumer-lending-guarantor SF-1: no finding of kind ambiguity on paragraphs [4] or rules [R-170]
- consumer-lending-guarantor SF-2: no finding of kind conflict on paragraphs [1, 3] or rules [R-110]
- consumer-lending-guarantor SF-3: no finding of kind unsupported on paragraphs [5] or rules [R-180]
- consumer-lending-guarantor SF-4: no finding of kind gap on paragraphs [5] or rules [R-180]
- consumer-lending-strict SF-4: no finding of kind gap on paragraphs [8] or rules [R-330]
- municipal-tax-discount-en SF-1: no finding of kind ambiguity on paragraphs [1] or rules [R-010]
- municipal-tax-discount-en SF-3: no finding of kind unsupported on paragraphs [2] or rules [R-030]
- municipal-tax-discount-en SF-4: no finding of kind gap on paragraphs [6] or rules [R-310]
- rental-deposit-en SF-2: no finding of kind conflict on paragraphs [2, 6] or rules [R-020]
- rental-deposit-en SF-3: no finding of kind unsupported on paragraphs [5] or rules [R-030]
- rental-deposit-en SF-5: no finding of kind duplicate on paragraphs [4] or rules [R-100, R-101]
- rental-deposit-return SF-2: no finding of kind conflict on paragraphs [1, 6] or rules [R-900]
- rental-deposit-return SF-4: no finding of kind gap on paragraphs [5] or rules [R-310]
- scholarship-en SF-1: no finding of kind ambiguity on paragraphs [4] or rules [R-110]
- scholarship-en SF-3: no finding of kind unsupported on paragraphs [3] or rules [R-020]
- scholarship-en SF-4: no finding of kind gap on paragraphs [5] or rules [R-300]
- scholarship-merit SF-1: no finding of kind ambiguity on paragraphs [4] or rules [R-110]
- scholarship-merit SF-2: no finding of kind conflict on paragraphs [1, 7] or rules [R-100]
- scholarship-merit SF-4: no finding of kind gap on paragraphs [6] or rules [R-030]
- scholarship-need SF-4: no finding of kind gap on paragraphs [6] or rules [R-300]
- synthetic-derived-chain SF-2: no finding of kind unsupported on paragraphs [1] or rules [R-010]
- synthetic-enums SF-2: no finding of kind conflict on paragraphs [2, 7] or rules [R-120]
- synthetic-enums SF-4: no finding of kind unsupported on paragraphs [2] or rules [R-130]
- synthetic-enums SF-5: no finding of kind gap on paragraphs [5] or rules [R-140]
- synthetic-enums SF-6: no finding of kind duplicate on paragraphs [3] or rules [R-100, R-101]
- synthetic-retirees-dates SF-1: no finding of kind ambiguity on paragraphs [1] or rules [R-100]
- synthetic-retirees-dates SF-4: no finding of kind gap on paragraphs [4] or rules [R-110]
- warranty-claim-electronics SF-1: no finding of kind ambiguity on paragraphs [3] or rules [R-110]
- warranty-claim-electronics SF-4: no finding of kind gap on paragraphs [5] or rules [R-310]
- warranty-claim-en SF-1: no finding of kind ambiguity on paragraphs [3] or rules [R-110]
- warranty-claim-en SF-4: no finding of kind gap on paragraphs [5] or rules [R-310]
- warranty-claim-en SF-5: no finding of kind duplicate on paragraphs [2] or rules [R-100, R-101]
- CR-3: not valid after 2 repairs
- CR-5: replace [R-140] where [] is expected
- CR-6: 1 patches for a request the rule set cannot express
- Q-14 (citations): no answer was written; the retrieval threshold stopped the question
- Q-23 (citations): did not cite [[p:2]]
- Q-27 (citations): did not cite [[p:2]]
- Q-01 (consumer-lending): missed r:R-330; the question's own tool is getDecision
- Q-07 (consumer-lending): missed p:6
- Q-14 (consumer-lending): missed r:R-310, r:R-320, r:R-330, stopped by the threshold; the question's own tool is listRules
- Q-29 (synthetic-enums): missed r:R-100
