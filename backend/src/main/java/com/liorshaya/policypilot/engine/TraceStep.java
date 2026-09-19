package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.rules.model.Outcome;
import com.liorshaya.policypilot.rules.model.Provenance;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One rule in the trace (Document 3, TraceStep object). A skipped or disabled rule carries its identity and status
 * only; an evaluated rule carries its comparisons in tree order and its provenance; a fired rule also carries the
 * actions it applied; a rule that failed carries the error.
 */
public record TraceStep(
        String ruleId,
        String label,
        int priority,
        Status status,
        @Nullable List<Compared> comparisons,
        @Nullable List<Applied> actions,
        @Nullable Failure error,
        @Nullable Provenance provenance) {

    public TraceStep {
        comparisons = comparisons == null ? null : List.copyOf(comparisons);
        actions = actions == null ? null : List.copyOf(actions);
    }

    /** Step status, written in lower case. */
    public enum Status {
        FIRED("fired"),
        NOT_FIRED("not_fired"),
        SKIPPED("skipped"),
        DISABLED("disabled"),
        ERROR("error");

        private final String json;

        Status(String json) {
            this.json = json;
        }

        public String json() {
            return json;
        }
    }

    /**
     * One comparison leaf: the evaluated operand ({@code null} only for present and absent, a list for in, not_in and
     * between, an {@link ExpressionValue} for an expression or a field reference), the case value and the result.
     */
    public record Compared(String field, String op, @Nullable Object expected, @Nullable Object actual,
            boolean result) {}

    /** An operand given as an expression: its value and its text, {@code (78 - (term_months / 12))}. */
    public record ExpressionValue(@Nullable Object value, String text) {}

    /** An applied action. */
    public sealed interface Applied {

        /** A set, with the value before and after. */
        record Set(String field, @Nullable Object from, @Nullable Object to) implements Applied {}

        /** A flag. */
        record Flagged(String code) implements Applied {}

        /** A decide. */
        record Decided(Outcome outcome, boolean terminal) implements Applied {}
    }

    /** The error that stopped the evaluation at this rule. */
    public record Failure(EvaluationError code, String detail) {}
}
