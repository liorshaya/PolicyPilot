<dsl_cheatsheet>
{cheatsheet}
</dsl_cheatsheet>

<policy language="{language}" title="{title}" paragraphs="{paragraphCount}">
{policy}
</policy>

<fields>
{fields}
</fields>

<defaults>
{defaults}
</defaults>

<candidate_rules count="{candidateCount}" version="{versionNo}">
{candidates}
</candidate_rules>

<retired_rule_ids>
{retiredIds}
</retired_rule_ids>

<change_request id="{changeRequestId}">
{request}
</change_request>

Produce a Patches object for this request:

1. MINIMAL. Change only what the request requires. Prefer "replace" of an existing rule over "remove" plus
   "add", and replace or remove only rules listed in <candidate_rules>. Never change the defaults: only an
   analyst can. Never change fields or priorities unless the request says so. Remove a rule only when the
   request names it, by its id or by a value its condition tests.
2. CONSISTENCY. If a change makes another candidate rule inconsistent (an advisory band that no longer
   matches a new threshold, a derivation whose input changed), patch it too and say why in its rationale.
   List every candidate you looked at and left unchanged in "untouched".
3. IDENTITY. A replaced rule keeps its id. A new rule gets a fresh id in the band of its purpose that is not
   in <retired_rule_ids> and not in use. Keep every rejection before every referral.
4. PROVENANCE. If the changed rule is still what its quoted passage says, keep {"kind": "quoted"}. If the
   policy text no longer supports it, set {"kind": "pending", "changeRequestId": "{changeRequestId}",
   "rationale": ...} and explain in the rationale which passage it now diverges from. Never output
   provenance of kind "analyst".
5. TEXT. Update labels and reasons in {language} to match the new values. Write one "rationale" per patch
   in {language} that an approver can read in ten seconds, and a one-line "summary" of the whole change.
6. IMPOSSIBLE REQUESTS. If the request cannot be expressed in the DSL, contradicts the candidate rules in a
   way you cannot resolve, or is not a rule change at all, return an empty "patches" list and put the
   reason in "notes".

Return only the JSON object.
