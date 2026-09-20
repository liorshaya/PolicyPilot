package com.liorshaya.policypilot.ai;

/**
 * The provider answered, but not with JSON the contract accepts (Document 4, Output discipline). This is a
 * validation failure like any other: it enters the repair loop where the prompt has one.
 */
public class LlmMalformedOutputException extends RuntimeException {

    private final String raw;

    public LlmMalformedOutputException(String message, String raw, Throwable cause) {
        super(message, cause);
        this.raw = raw;
    }

    /** What the provider actually sent, for the error list the analyst sees and for the log. */
    public String raw() {
        return raw;
    }
}
