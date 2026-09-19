package com.liorshaya.policypilot.rules.validation;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One reason a case is not a case (Document 3, Evaluation Semantics, step 1). {@code value} is the supplied value
 * for a type mismatch or an out-of-range value, and {@code null} for a missing or a derived field.
 */
public record CaseProblem(Code code, String field, @Nullable JsonNode value) {

    /** The problem codes a case error lists under CASE_INVALID. */
    public enum Code {
        CASE_REQUIRED_MISSING,
        CASE_TYPE_MISMATCH,
        CASE_OUT_OF_RANGE,
        CASE_DERIVED_SUPPLIED
    }
}
