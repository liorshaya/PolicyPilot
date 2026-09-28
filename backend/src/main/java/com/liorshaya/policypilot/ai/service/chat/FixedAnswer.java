package com.liorshaya.policypilot.ai.service.chat;

import java.util.Locale;

/**
 * The fixed sentences an answer can be instead of what a model wrote (Document 4, Prompt 4): the not-covered sentence
 * of the Threshold, and the sentence that ends a turn past the tool caps. The done event names the one an answer is
 * (Document 2, added 2026-09-28 for Register phase 4), so the web app marks it as the system's, not the model's.
 */
public enum FixedAnswer {
    NOT_COVERED,
    TOOL_LIMIT;

    /** As the done event writes it: {@code not_covered} or {@code tool_limit}. */
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
