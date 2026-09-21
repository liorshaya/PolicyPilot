package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.validation.QuestionText;
import org.jspecify.annotations.Nullable;

/** The body of {@code POST /chat/sessions/{id}/messages} (Document 2, API Surface): the question, 2 KB at most. */
public record ChatMessageRequest(@Nullable String question) {

    /** The question as the chat will answer it, or REQUEST_INVALID. */
    public String normalizedQuestion() {
        return QuestionText.of(question);
    }
}
