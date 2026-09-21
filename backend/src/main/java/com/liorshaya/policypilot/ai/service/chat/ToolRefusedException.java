package com.liorshaya.policypilot.ai.service.chat;

/**
 * A tool that will not answer this call: the arguments are wrong, the application is not in this session, or the
 * engine refused the overrides. The model reads the refusal as the tool's result; it is never an error it cannot see.
 */
public class ToolRefusedException extends RuntimeException {

    private final String reason;

    public ToolRefusedException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** The reason the refusal is counted under: {@code not_found}, {@code invalid_arguments} or {@code limit}. */
    public String reason() {
        return reason;
    }
}
