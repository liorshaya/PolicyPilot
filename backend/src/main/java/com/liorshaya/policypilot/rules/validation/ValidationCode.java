package com.liorshaya.policypilot.rules.validation;

/** The static validation table of Document 3: every code with its layer and severity. */
public enum ValidationCode {
    DSL_SCHEMA(Layer.SCHEMA, Severity.ERROR),
    DSL_VERSION_UNSUPPORTED(Layer.SCHEMA, Severity.ERROR),
    DERIVED_REQUIRED(Layer.SCHEMA, Severity.ERROR),
    FIELD_DUPLICATE(Layer.SEMANTIC, Severity.ERROR),
    FIELD_UNKNOWN(Layer.SEMANTIC, Severity.ERROR),
    FIELD_TYPE_MISMATCH(Layer.SEMANTIC, Severity.ERROR),
    FIELD_DOMAIN_INVALID(Layer.SEMANTIC, Severity.ERROR),
    ENUM_VALUE_UNKNOWN(Layer.SEMANTIC, Severity.ERROR),
    EXPR_TYPE_MISMATCH(Layer.SEMANTIC, Severity.ERROR),
    EXPR_ARITY(Layer.SEMANTIC, Severity.ERROR),
    BETWEEN_RANGE_INVALID(Layer.SEMANTIC, Severity.ERROR),
    REGEX_INVALID(Layer.SEMANTIC, Severity.ERROR),
    RESERVED_IDENTIFIER(Layer.SEMANTIC, Severity.ERROR),
    RULE_ID_DUPLICATE(Layer.SEMANTIC, Severity.ERROR),
    DERIVED_WRITE_ONLY(Layer.SEMANTIC, Severity.ERROR),
    PROVENANCE_PARAGRAPH_MISSING(Layer.SEMANTIC, Severity.ERROR),
    PROVENANCE_QUOTE_MISMATCH(Layer.SEMANTIC, Severity.ERROR),
    PROVENANCE_ANALYST_FROM_MODEL(Layer.SEMANTIC, Severity.ERROR),
    PROVENANCE_PENDING_FROM_MODEL(Layer.SEMANTIC, Severity.ERROR),
    PROVENANCE_PENDING_AT_PUBLISH(Layer.SEMANTIC, Severity.ERROR),
    DERIVED_CYCLE(Layer.STRUCTURAL, Severity.ERROR),
    DERIVED_ORDER(Layer.STRUCTURAL, Severity.ERROR),
    DERIVED_NEVER_SET(Layer.STRUCTURAL, Severity.WARNING),
    FIELD_UNUSED(Layer.STRUCTURAL, Severity.WARNING),
    RULE_UNREACHABLE(Layer.STRUCTURAL, Severity.WARNING),
    RULE_OVERLAP_CONFLICT(Layer.STRUCTURAL, Severity.WARNING),
    REFER_PRECEDES_REJECT(Layer.STRUCTURAL, Severity.WARNING),
    CANDIDATE_NEVER_WINS(Layer.STRUCTURAL, Severity.WARNING),
    DIVISION_BY_UNGUARDED_FIELD(Layer.STRUCTURAL, Severity.WARNING),
    MISSING_FIELD_UNDER_NOT(Layer.STRUCTURAL, Severity.WARNING),
    PRIORITY_BAND_UNUSUAL(Layer.STRUCTURAL, Severity.INFO),
    NO_TERMINAL_APPROVE(Layer.STRUCTURAL, Severity.INFO);

    private final Layer layer;
    private final Severity severity;

    ValidationCode(Layer layer, Severity severity) {
        this.layer = layer;
        this.severity = severity;
    }

    /** The layer that reports the code. */
    public Layer layer() {
        return layer;
    }

    /** The severity every finding with this code carries. */
    public Severity severity() {
        return severity;
    }
}
