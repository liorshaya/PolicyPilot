package com.liorshaya.policypilot.ai;

/**
 * The provider did not answer: a timeout, a rate limit, a 5xx or an open circuit breaker (Document 4, Guardrails).
 * It is a defined, visible failure, never a silent fallback to a made-up answer.
 */
public class LlmUnavailableException extends RuntimeException {

    private final Reason reason;

    public LlmUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public LlmUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** What stopped the call, so the API can answer with the code of Document 2 that matches. */
    public enum Reason {
        TIMEOUT,
        RATE_LIMITED,
        PROVIDER_ERROR,
        BUDGET_EXHAUSTED
    }
}
