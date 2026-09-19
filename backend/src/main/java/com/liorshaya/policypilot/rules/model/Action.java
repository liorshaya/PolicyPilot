package com.liorshaya.policypilot.rules.model;

import org.jspecify.annotations.Nullable;

/** What a rule does when its condition is true (Document 3, Actions and Rules). */
public sealed interface Action {

    /** Records a decision; terminal unless {@code terminal} is {@code false}. */
    record Decide(Outcome outcome, @Nullable Boolean terminal, String reason) implements Action {

        /** {@code terminal}, which defaults to {@code true}. */
        public boolean isTerminal() {
            return !Boolean.FALSE.equals(terminal);
        }
    }

    /** Writes a derived field. */
    record SetField(String field, Value value) implements Action {}

    /** Attaches a note to the decision without affecting the outcome. */
    record Flag(String code, String message) implements Action {}
}
