package com.liorshaya.policypilot.rules.json;

/**
 * A rule set that is not well-formed JSON, breaks a Document 5 limit, or does not have the shape of the DSL.
 * The message names the problem and never repeats the input.
 */
public final class RuleSetFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String pointer;

    RuleSetFormatException(String pointer, String message) {
        super(message);
        this.pointer = pointer;
    }

    /** JSON pointer to the offending node; the empty string is the whole document. */
    public String pointer() {
        return pointer;
    }
}
