package com.liorshaya.policypilot.ai;

/** What one model call cost, as the provider reported it (Document 4, Logging for every call). */
public record TokenUsage(int inputTokens, int outputTokens) {

    public static final TokenUsage NONE = new TokenUsage(0, 0);

    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts cannot be negative");
        }
    }

    /** What the daily ledger counts. */
    public long total() {
        return (long) inputTokens + outputTokens;
    }
}
