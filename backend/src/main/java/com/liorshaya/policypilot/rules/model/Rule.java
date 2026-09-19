package com.liorshaya.policypilot.rules.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A rule: one condition, one to five actions, and its provenance (Document 3, Actions and Rules). */
public record Rule(
        String id,
        String label,
        int priority,
        @Nullable Boolean enabled,
        Condition condition,
        List<Action> actions,
        Provenance provenance,
        @Nullable List<String> tags) {

    public Rule {
        actions = List.copyOf(actions);
        tags = tags == null ? null : List.copyOf(tags);
    }

    /** {@code enabled}, which defaults to {@code true}. */
    public boolean isEnabled() {
        return !Boolean.FALSE.equals(enabled);
    }
}
