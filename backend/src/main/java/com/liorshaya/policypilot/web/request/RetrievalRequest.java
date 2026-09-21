package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import com.liorshaya.policypilot.web.validation.InputNormalizer;
import com.liorshaya.policypilot.web.validation.InputRejectedException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /rulesets/{id}/versions/{no}/retrieval} (Document 2, API Surface): one question, held to
 * the chat message's limits (Document 5, Input limits: 2 KB, the same Unicode normalization as policy text).
 */
public record RetrievalRequest(@Nullable String question) {

    /** Document 5: "Chat message, retrieval question: 2 KB", counted in UTF-8 bytes after normalization. */
    public static final int MAX_BYTES = 2048;

    /** The question as it will be embedded and searched, or REQUEST_INVALID. */
    public String normalizedQuestion() {
        String normalized;
        try {
            normalized = InputNormalizer.normalize(question == null ? "" : question).strip();
        } catch (InputRejectedException e) {
            throw invalid("contains a control character");
        }
        if (normalized.isEmpty()) {
            throw invalid("is empty");
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid("is longer than 2 KB");
        }
        return normalized;
    }

    private static ApiException invalid(String problem) {
        return new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail("/question", problem)));
    }
}
