package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A question as the chat and retrieval take it (Document 5, Input limits: "Chat message, retrieval question: 2 KB",
 * with the same Unicode normalization as policy text): normalized, stripped, not empty, and at most 2 KB of UTF-8.
 */
public final class QuestionText {

    /** Document 5: 2 KB, counted in UTF-8 bytes after normalization. */
    public static final int MAX_BYTES = 2048;

    private QuestionText() {}

    /** The question as it will be embedded, searched and sent to the model, or REQUEST_INVALID at {@code /question}. */
    public static String of(@Nullable String question) {
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
