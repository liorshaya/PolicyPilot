package com.liorshaya.policypilot.engine;

/** The evaluation errors of Document 3, step 7: each stops the evaluation with status ERROR. */
public enum EvaluationError {
    EVAL_DIV_ZERO,
    EVAL_NON_FINITE,
    EVAL_ABSENT_DATE,
    EVAL_ABSENT_FIELD,
    EVAL_DERIVED_ABSENT
}
