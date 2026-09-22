package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.ruleset.service.GapResolution;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import com.liorshaya.policypilot.web.validation.InputNormalizer;
import com.liorshaya.policypilot.web.validation.InputRejectedException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /rulesets/{id}/versions/{no}/findings/{findingId}/acknowledge} (Document 2, API Surface):
 * the resolution a gap needs ({@code rule_added}, {@code flag_added} or {@code interpretation}) and the note an
 * error needs. The note is held to the chat message's limits (Document 5, Input limits), because it is text a person
 * types that is stored and shown again.
 */
public record AcknowledgeFindingRequest(@Nullable String resolution, @Nullable String note) {

    /** Document 5: 2 KB of UTF-8 after normalization, as for a chat message. */
    public static final int MAX_NOTE_BYTES = 2048;

    /** The resolution named, or null when the body names none; REQUEST_INVALID for a name that is not one. */
    public @Nullable GapResolution gapResolution() {
        if (resolution == null) {
            return null;
        }
        try {
            return GapResolution.of(resolution);
        } catch (IllegalArgumentException e) {
            throw invalid("/resolution", "is not rule_added, flag_added or interpretation");
        }
    }

    /** The note as it will be stored, or null when there is none; REQUEST_INVALID over the limits. */
    public @Nullable String normalizedNote() {
        if (note == null) {
            return null;
        }
        String normalized;
        try {
            normalized = InputNormalizer.normalize(note).strip();
        } catch (InputRejectedException e) {
            throw invalid("/note", "contains a control character");
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_NOTE_BYTES) {
            throw invalid("/note", "is longer than 2 KB");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static ApiException invalid(String path, String problem) {
        return new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail(path, problem)));
    }
}
