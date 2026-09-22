package com.liorshaya.policypilot.eval;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One row of Document 4's metric table: its name, how Document 4 defines it, the target for the strong model, and
 * what a provider scored. A provider that was not run scores {@code null}, which the report prints as "not run"
 * rather than as zero, because an absent column and a failing one must never read the same.
 *
 * <p>The Ollama column is "reported with the same metrics and no targets in the first version", so a target only
 * ever decides the {@code PASS}/{@code FAIL} of the strong model's column.
 */
record Metric(String name, String definition, @Nullable Double target, String counted) {

    /** Document 4, "Metrics and targets", in its order. */
    static List<Metric> table() {
        return List.of(
                new Metric("Rule precision", "Matched generated rules / generated rules", 0.90, "rules"),
                new Metric("Rule recall", "Matched expected rules / expected rules", 0.90, "rules"),
                new Metric("Provenance accuracy", "Matched rules whose paragraph is the expected one", 0.95,
                        "matched rules"),
                new Metric("Schema-valid first try", "Authoring runs whose first output passes the schema", 0.90,
                        "runs"),
                new Metric("Valid after repairs", "Authoring runs that end valid within 2 repairs", 1.00, "runs"),
                new Metric("Case agreement",
                        "Cases where the generated rule set's outcome equals the expected rule set's", 0.95,
                        "cases"),
                new Metric("Reviewer recall", "Seeded defects found with the right kind and an overlapping anchor",
                        0.80, "seeded defects"),
                new Metric("Reviewer precision", "Findings that are seeded or confirmed real / all findings", 0.70,
                        "findings"),
                new Metric("Retrieval recall at 8", "Questions whose expected chunk is among the 8 retrieved", 0.90,
                        "questions"),
                new Metric("Citation accuracy",
                        "Answers whose markers are all valid and include the expected source", 0.90, "answers"),
                new Metric("Refusal accuracy",
                        "Not-covered questions refused with the fixed sentence, and covered ones not refused", 0.90,
                        "questions"),
                new Metric("Change correctness", "Change requests whose patches match the expected patch set",
                        5.0 / 6, "requests"),
                new Metric("Confidence calibration",
                        "Mean confidence of wrong rules is lower than of right rules", null, "rules"));
    }

    /**
     * What one provider scored on one metric: the ratio, what it was counted over, and whether the number is the
     * metric itself or a lower bound of it. Document 4 defines reviewer precision over "findings that are seeded
     * or confirmed real on inspection", and no runner can do the second half, so what it computes is a floor.
     */
    record Score(double value, int numerator, int denominator, boolean lowerBound, String note) {

        static Score of(int numerator, int denominator) {
            return new Score(denominator == 0 ? 0 : (double) numerator / denominator, numerator, denominator,
                    false, "");
        }

        Score with(String note) {
            return new Score(value, numerator, denominator, lowerBound, note);
        }

        /** The same number, marked as a floor: below its target it decides nothing, above it, it passes. */
        Score asLowerBound() {
            return new Score(value, numerator, denominator, true, note);
        }

        String asText() {
            return String.format("%s%.2f (%d of %d)", lowerBound ? "at least " : "", value, numerator, denominator);
        }
    }

    /** {@code PASS} or {@code FAIL} against this metric's target; a metric with no target is only reported. */
    String verdict(Metric.@Nullable Score score) {
        if (score == null) {
            return "not run";
        }
        if (target == null) {
            return "reported";
        }
        if (score.value() >= target) {
            return "PASS";
        }
        // a floor below its target has not failed: only an analyst's pass over the findings can settle it
        return score.lowerBound() ? "undecided (needs an analyst pass)" : "FAIL";
    }
}
