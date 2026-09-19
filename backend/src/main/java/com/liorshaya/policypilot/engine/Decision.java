package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.rules.model.Outcome;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * A decision (Document 3, Decision object). With status OK it carries the outcome, the reason and the deciding
 * rule ({@code null} when the default applied); with status ERROR it carries the error code and the failing rule, and
 * the trace stops at that rule. {@code derived} lists every derived field in declaration order, {@code null} when no
 * rule set it. A simulation also carries the overrides it applied. The version, the timing and the stored decision
 * id are the API's to add; the engine has no clock and no storage.
 */
public record Decision(
        Status status,
        @Nullable Outcome outcome,
        @Nullable String reason,
        @Nullable String decidingRuleId,
        boolean terminal,
        Map<String, @Nullable Object> derived,
        List<Flag> flags,
        List<Candidate> candidates,
        List<TraceStep> trace,
        @Nullable EvaluationError errorCode,
        @Nullable String errorRuleId,
        @Nullable ObjectNode overrides) implements Evaluation {

    public Decision {
        derived = Collections.unmodifiableMap(new LinkedHashMap<>(derived));
        flags = List.copyOf(flags);
        candidates = List.copyOf(candidates);
        trace = List.copyOf(trace);
    }

    /** Decision status. */
    public enum Status { OK, ERROR }

    /** A flag raised by a fired rule. */
    public record Flag(String code, String message, String ruleId) {}

    /** A non-terminal decide that fired. */
    public record Candidate(Outcome outcome, String ruleId) {}

    /** Whether this is a what-if evaluation, which is never stored (Document 3, Simulation). */
    public boolean simulation() {
        return overrides != null;
    }

    /** The same decision marked as a simulation of the given overrides. */
    Decision asSimulation(ObjectNode appliedOverrides) {
        return new Decision(status, outcome, reason, decidingRuleId, terminal, derived, flags, candidates, trace,
                errorCode, errorRuleId, appliedOverrides.deepCopy());
    }
}
