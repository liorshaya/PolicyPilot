The document you produced failed validation. Fix only the listed problems and return the complete corrected
document. Do not change any rule, field or text that is not named in a problem.

<validation_errors count="{count}">
{errors}
</validation_errors>

<paragraph_texts>
{paragraphTexts}
</paragraph_texts>

<your_document>
{document}
</your_document>

Guidance per error code:
- DSL_SCHEMA: the JSON path names the offending element; conform to the schema in the cheat sheet.
- PROVENANCE_QUOTE_MISMATCH: copy the quote verbatim from the paragraph text above; do not paraphrase.
- FIELD_UNKNOWN or ENUM_VALUE_UNKNOWN: declare the field or value, or use the declared one.
- DERIVED_ORDER or DERIVED_CYCLE: give the set rule a lower priority than every rule that reads its field.
- DIVISION_BY_UNGUARDED_FIELD: add a guard condition to the rule or a domain to the field.
- REFER_PRECEDES_REJECT: move the referral into the 300 band unless the text requires the referral first.
Return only the JSON object.
