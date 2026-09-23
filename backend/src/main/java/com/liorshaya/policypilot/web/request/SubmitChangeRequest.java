package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.validation.ShortText;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /rulesets/{id}/versions/{no}/changes} (Document 2, API Surface: {@code {text}}): a change
 * request in natural language, held to the chat message's limits (Document 5, Input limits; {@link ShortText}).
 */
public record SubmitChangeRequest(@Nullable String text) {

    /** The request as the analysis and the model will read it, or REQUEST_INVALID at {@code /text}. */
    public String normalizedText() {
        return ShortText.of(text, "/text");
    }
}
