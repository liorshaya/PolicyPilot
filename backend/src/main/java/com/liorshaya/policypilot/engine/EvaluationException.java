package com.liorshaya.policypilot.engine;

/** Thrown inside the engine when a rule cannot be evaluated; the engine turns it into an ERROR decision. */
final class EvaluationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final EvaluationError error;
    private final String detail;

    EvaluationException(EvaluationError error, String detail) {
        super(error.name(), null, false, false);
        this.error = error;
        this.detail = detail;
    }

    EvaluationError error() {
        return error;
    }

    String detail() {
        return detail;
    }
}
