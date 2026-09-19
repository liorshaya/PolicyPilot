package com.liorshaya.policypilot.rules.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A condition tree: comparison leaves under {@code all}, {@code any} and {@code not} (Document 3, Conditions). */
public sealed interface Condition {

    /** {@code { "field", "op", "value" }}; {@code value} is absent for {@code present} and {@code absent}. */
    record Comparison(String field, Operator op, @Nullable Operand value) implements Condition {}

    /** True when every child is true. */
    record All(List<Condition> all) implements Condition {
        public All {
            all = List.copyOf(all);
        }
    }

    /** True when at least one child is true. */
    record Any(List<Condition> any) implements Condition {
        public Any {
            any = List.copyOf(any);
        }
    }

    /** Negation. */
    record Not(Condition not) implements Condition {}

    /** The constant condition {@code { "always": true }}. */
    record Always() implements Condition {}
}
