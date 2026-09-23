package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A short text as the chat, retrieval and change requests take it (Document 5, Input limits: "Chat message, retrieval
 * question: 2 KB", with the same Unicode normalization as policy text, and "Change request: 2 KB, same as chat
 * message"): normalized, stripped, not empty, and at most 2 KB of UTF-8.
 */
public final class ShortText {

    /** Document 5: 2 KB, counted in UTF-8 bytes after normalization. */
    public static final int MAX_BYTES = 2048;

    private ShortText() {}

    /**
     * The text as it will be embedded, searched and sent to the model, or REQUEST_INVALID.
     *
     * @param pointer the JSON pointer of the text in the request body, which a refusal names
     */
    public static String of(@Nullable String text, String pointer) {
        String normalized;
        try {
            normalized = InputNormalizer.normalize(text == null ? "" : text).strip();
        } catch (InputRejectedException e) {
            throw invalid(pointer, "contains a control character");
        }
        if (normalized.isEmpty()) {
            throw invalid(pointer, "is empty");
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid(pointer, "is longer than 2 KB");
        }
        return normalized;
    }

    private static ApiException invalid(String pointer, String problem) {
        return new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail(pointer, problem)));
    }
}
