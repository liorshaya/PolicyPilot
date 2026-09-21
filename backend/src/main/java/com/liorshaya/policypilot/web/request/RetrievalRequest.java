package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.validation.QuestionText;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /rulesets/{id}/versions/{no}/retrieval} (Document 2, API Surface): one question, held to
 * the chat message's limits ({@link QuestionText}).
 */
public record RetrievalRequest(@Nullable String question) {

    /** The question as it will be embedded and searched, or REQUEST_INVALID. */
    public String normalizedQuestion() {
        return QuestionText.of(question);
    }
}
