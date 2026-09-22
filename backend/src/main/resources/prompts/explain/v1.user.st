<trace audience="{audience}" language="{language}">
{decision}
</trace>

Explain this decision. Use only what the trace contains.

1. OUTCOME. State the outcome and, for a decided case, the rule that decided it, in one sentence.
   For "refer", say what a reviewer will look at, taken from that rule's reason. For "approve", list the flags
   as conditions that a person still checks. For an ERROR status, say the evaluation could not complete and why.
2. FACTORS. List the rules that fired, in evaluation order, each as one sentence that names the compared
   values ("net income 9,500 against the minimum 8,000") and cites the rule id and the policy paragraph
   from its provenance. Include derivations only if they matter to the outcome.
3. NOT APPLIED. Mention at most three rules that were evaluated and did not fire only when they help the
   reader (a threshold the applicant is close to); never list rules with status "skipped", because they
   were not evaluated.
4. NO COUNTERFACTUALS. Do not say what would have happened with different inputs; that question is
   answered by a simulation, not by you.
5. AUDIENCE. For "applicant", write plain sentences without rule ids or field names, in {language}; the
   citations still go into the structured fields. For "officer", ids and values are welcome.

Return only the JSON object.
