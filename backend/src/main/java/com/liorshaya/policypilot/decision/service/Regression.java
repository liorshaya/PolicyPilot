package com.liorshaya.policypilot.decision.service;

import com.liorshaya.policypilot.engine.CompiledRuleSet;
import com.liorshaya.policypilot.engine.Decision;
import com.liorshaya.policypilot.engine.Evaluation;
import com.liorshaya.policypilot.engine.RuleEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * The regression report of a change (Document 3, Regression report): the decisions a sandbox made on the base version,
 * decided again by the patched copy. It lists every flipped outcome and counts each transition, so the analyst sees
 * what the change would change before anyone approves it.
 *
 * @param decisions how many decisions were decided again
 * @param flips the decisions whose outcome the copy changes, by case number, those not made from a stored case last
 * @param transitions how many decisions flip from one outcome to another, keyed {@code approve → reject}, in key
 *     order
 */
public record Regression(int decisions, List<Flip> flips, Map<String, Integer> transitions) {

    /** The outcome of a decision that could not decide its input: an invalid case, or an error while evaluating it. */
    public static final String ERROR = "error";

    public Regression {
        flips = List.copyOf(flips);
        transitions = Collections.unmodifiableSortedMap(new TreeMap<>(transitions));
    }

    /**
     * One flipped outcome.
     *
     * @param caseNo the stored case the decision was made from, or null for a case decided on its own
     */
    public record Flip(UUID decisionId, @Nullable Integer caseNo, String before, String after,
            @Nullable String decidingRuleBefore, @Nullable String decidingRuleAfter) {}

    /** A stored decision as the regression reads it: its outcome, or {@link #ERROR}, and the input it decided. */
    record Decided(UUID decisionId, @Nullable Integer caseNo, String outcome, @Nullable String decidingRuleId,
            ObjectNode input) {}

    /** Decides every stored decision's input again with the copy and keeps the ones whose outcome changed. */
    static Regression of(List<Decided> decided, CompiledRuleSet copy, RuleEngine engine) {
        List<Flip> flips = new ArrayList<>();
        for (Decided stored : decided) {
            Evaluation evaluation = engine.evaluate(copy, stored.input().deepCopy());
            String after = ERROR;
            String rule = null;
            if (evaluation instanceof Decision decision && decision.outcome() != null) {
                after = decision.outcome().json();
                rule = decision.decidingRuleId();
            }
            if (!after.equals(stored.outcome())) {
                flips.add(new Flip(stored.decisionId(), stored.caseNo(), stored.outcome(), after,
                        stored.decidingRuleId(), rule));
            }
        }
        flips.sort(Comparator.comparing(Flip::caseNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Flip::decisionId));
        Map<String, Integer> transitions = new TreeMap<>();
        flips.forEach(flip -> transitions.merge(flip.before() + " → " + flip.after(), 1, Integer::sum));
        return new Regression(decided.size(), flips, transitions);
    }
}
