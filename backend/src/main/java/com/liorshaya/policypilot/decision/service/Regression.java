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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * The regression report of a change (Document 3, Regression report): the decisions a sandbox made on the base version,
 * decided again by the patched copy. It counts their outcomes before, lists every flipped outcome, counts each
 * transition and the decisions whose flags moved, so the analyst sees what the change would change before anyone
 * approves it.
 *
 * @param decisions how many decisions were decided again
 * @param before how many of them had each outcome on the base version, {@link #ERROR} included, in key order
 * @param flips the decisions whose outcome the copy changes, by case number, those not made from a stored case last
 * @param transitions how many decisions flip from one outcome to another, keyed {@code approve → reject}, in key
 *     order
 * @param flagsMoved the decisions whose flags the copy changes, whether or not their outcome flips
 */
public record Regression(int decisions, Map<String, Integer> before, List<Flip> flips, Map<String, Integer> transitions,
        FlagsMoved flagsMoved) {

    /** The outcome of a decision that could not decide its input: an invalid case, or an error while evaluating it. */
    public static final String ERROR = "error";

    public Regression {
        before = Collections.unmodifiableSortedMap(new TreeMap<>(before));
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

    /**
     * The decisions whose flags the copy changes (Document 3, Regression report: added 2026-09-28, Register phase 4):
     * a decision has moved when it gains or loses a flag, a flag being its code and the rule that raised it.
     *
     * @param decisions how many decisions gained or lost a flag
     * @param byRule how many of them gained or lost a flag each rule raises, by rule id in id order
     */
    public record FlagsMoved(int decisions, Map<String, Integer> byRule) {

        public FlagsMoved {
            byRule = Collections.unmodifiableSortedMap(new TreeMap<>(byRule));
        }
    }

    /** A flag as the regression compares it: its code and the rule that raised it. */
    record Raised(String code, String ruleId) {}

    /**
     * A stored decision as the regression reads it: its outcome, or {@link #ERROR}, its flags and the input it decided.
     */
    record Decided(UUID decisionId, @Nullable Integer caseNo, String outcome, @Nullable String decidingRuleId,
            List<Raised> flags, ObjectNode input) {

        Decided {
            flags = List.copyOf(flags);
        }
    }

    /**
     * Decides every stored decision's input again with the copy: counts the outcomes before, keeps the ones whose
     * outcome changed and counts the ones whose flags did.
     */
    static Regression of(List<Decided> decided, CompiledRuleSet copy, RuleEngine engine) {
        Map<String, Integer> before = new TreeMap<>();
        List<Flip> flips = new ArrayList<>();
        int flagsMoved = 0;
        Map<String, Integer> flagsMovedByRule = new TreeMap<>();
        for (Decided stored : decided) {
            before.merge(stored.outcome(), 1, Integer::sum);
            Evaluation evaluation = engine.evaluate(copy, stored.input().deepCopy());
            String after = ERROR;
            String rule = null;
            Set<Raised> flags = Set.of();
            if (evaluation instanceof Decision decision && decision.outcome() != null) {
                after = decision.outcome().json();
                rule = decision.decidingRuleId();
                flags = decision.flags().stream().map(flag -> new Raised(flag.code(), flag.ruleId()))
                        .collect(Collectors.toSet());
            }
            if (!after.equals(stored.outcome())) {
                flips.add(new Flip(stored.decisionId(), stored.caseNo(), stored.outcome(), after,
                        stored.decidingRuleId(), rule));
            }
            Set<String> moved = movedBy(Set.copyOf(stored.flags()), flags);
            if (!moved.isEmpty()) {
                flagsMoved++;
                moved.forEach(ruleId -> flagsMovedByRule.merge(ruleId, 1, Integer::sum));
            }
        }
        flips.sort(Comparator.comparing(Flip::caseNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Flip::decisionId));
        Map<String, Integer> transitions = new TreeMap<>();
        flips.forEach(flip -> transitions.merge(flip.before() + " → " + flip.after(), 1, Integer::sum));
        return new Regression(decided.size(), before, flips, transitions,
                new FlagsMoved(flagsMoved, flagsMovedByRule));
    }

    /** The rules that raise a flag one side has and the other does not. */
    private static Set<String> movedBy(Set<Raised> before, Set<Raised> after) {
        Set<String> rules = new TreeSet<>();
        before.stream().filter(flag -> !after.contains(flag)).forEach(flag -> rules.add(flag.ruleId()));
        after.stream().filter(flag -> !before.contains(flag)).forEach(flag -> rules.add(flag.ruleId()));
        return rules;
    }
}
