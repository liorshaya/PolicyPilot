<dsl_cheatsheet>
{cheatsheet}
</dsl_cheatsheet>

<example>
{examples}
</example>

<policy language="{language}" title="{title}" paragraphs="{paragraphCount}">
{policy}
</policy>

{hints}

Produce the RuleSet document for this policy. Follow these instructions in order:

1. FIELDS. Declare every input the policy needs to decide a case, and nothing else. Use English snake_case
   names, one of the six types, a unit where the text implies one, and a domain: counts and months at least 0,
   a repayment term at least 1, money at least 0, ages 0 to 120. Enum values are English snake_case. Put the
   human meaning in "description" in {language}, including any input definition the text gives (for example
   how income is measured for the self-employed). Cite the paragraph that implies the field in "source".
2. DERIVED FIELDS. Declare a derived field for every quantity the policy computes (an installment, a ratio,
   a band) and compute it with a "set" rule in the 1-99 band. If the expression divides by a field that may
   be zero, guard the rule with a condition such as {"field": "monthly_income", "op": "gt", "value": 0}.
3. RULES. Write one rule per atomic condition in the text. Hard eligibility gates are terminal "reject" rules
   in 100-199, affordability and risk limits are terminal "reject" rules in 200-299, conditions that send a
   case to a person are terminal "refer" rules in 300-399, advisory notes are "flag" rules in 400-499, and the
   positive outcome is a terminal "approve" rule with {"always": true} in 900-999 when the text says a case
   meeting all conditions is approved. Every rejection must have a lower priority than every referral.
4. LABELS AND REASONS. "label" is one sentence in {language} an analyst reads in a table. "reason" is one
   sentence in {language} an applicant could read in a letter. Neither mentions rule ids or field names.
5. PROVENANCE. Every rule and every field source is {"kind": "quoted"} with the paragraph number and a
   verbatim quote of 3 to 500 characters copied from that single paragraph, including its punctuation.
   Set "confidence" to your own estimate. Never output provenance of kind "analyst" or "pending".
6. NO INVENTION. Do not add thresholds, exceptions or fields the text does not state. When the text is
   ambiguous, choose the reading that sends the case to a person ("refer"), set confidence at most 0.7, and
   keep the quote that made you hesitate; the reviewer will raise it. Do not use today's date.
7. DEFAULTS. Set "defaults" to what the text says happens to a case the rules do not decide; if it says
   nothing, use "refer" with a reason in {language} that says the case needs manual review.
8. IDENTIFIERS. Rule ids are R- followed by the priority zero-padded to three digits (R-010, R-170, R-900);
   if two rules share a priority, break the tie with a suffix digit (R-1701). The rule set id is the
   kebab-case English name of the policy.

Return only the JSON object.
