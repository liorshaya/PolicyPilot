package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.validation.ShortText;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /changes/{id}/approve} and {@code .../reject} (Document 2, API Surface: {@code {note}}): an
 * optional note, held to the chat message's limits (Document 5, Input limits; {@link ShortText}).
 */
public record DecideChangeRequest(@Nullable String note) {

    /** The note as the audit entry records it; null when there is none, or REQUEST_INVALID at {@code /note}. */
    public @Nullable String normalizedNote() {
        return note == null || note.isBlank() ? null : ShortText.of(note, "/note");
    }
}
