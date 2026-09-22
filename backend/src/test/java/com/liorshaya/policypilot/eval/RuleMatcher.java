package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Document 4, "Rule matching": "a generated rule matches an expected rule when their actions are identical
 * (outcome and terminal flag, or the same {@code set} target and an expression that evaluates equal on the
 * policy's cases, or the same flag code) and their conditions are logically equivalent after normalization ...;
 * ids, labels, priorities and reasons are not compared". Each expected rule takes at most one generated rule and
 * the reverse, so the matching is a maximum bipartite one; where a rule could match two, the one citing the
 * expected paragraph is preferred, so provenance accuracy measures the model and not the order of the list.
 *
 * <p>Fields are compared by name. Document 4 assumes the generated names are the labeled ones --- its {@code set}
 * clause evaluates the generated expression on the labeled cases --- and nothing in {@code author/v1} makes that
 * so, which is why {@link FieldNames} exists and why an unmatched rule reading a field with no counterpart is
 * reported as its own reason rather than being hidden among the rest.
 */
final class RuleMatcher {

    private RuleMatcher() {}

    /** One expected rule and what became of it. */
    record Match(String expectedId, @Nullable String generatedId, boolean provenanceCorrect, String reason) {

        boolean matched() {
            return generatedId != null;
        }
    }

    /** What one policy's run scored, with everything the report's mismatch list needs. */
    record Result(List<Match> matches, int expectedRules, int generatedRules, List<String> unmatchedGenerated) {

        int matched() {
            return (int) matches.stream().filter(Match::matched).count();
        }

        double recall() {
            return expectedRules == 0 ? 1 : (double) matched() / expectedRules;
        }

        double precision() {
            return generatedRules == 0 ? 0 : (double) matched() / generatedRules;
        }

        /** Document 4: "Matched rules whose paragraph is the expected one", over the matched rules. */
        double provenanceAccuracy() {
            long correct = matches.stream().filter(Match::provenanceCorrect).count();
            return matched() == 0 ? 1 : (double) correct / matched();
        }
    }

    static Result match(RuleSet expected, RuleSet generated, List<ObjectNode> cases) {
        Map<String, Field> fields = expected.fields().stream()
                .collect(Collectors.toMap(Field::name, Function.identity(), (first, second) -> first));
        Map<String, Rule> generatedById = generated.rules().stream()
                .collect(Collectors.toMap(Rule::id, Function.identity(), (first, second) -> first));
        Map<String, List<String>> candidates = new LinkedHashMap<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        for (Rule expectedRule : expected.rules()) {
            List<String> fit = new ArrayList<>();
            Map<String, String> misses = new LinkedHashMap<>();
            for (Rule generatedRule : generated.rules()) {
                String reason = whyNot(expectedRule, generatedRule, expected, fields, cases);
                if (reason == null) {
                    fit.add(generatedRule.id());
                } else {
                    misses.put(generatedRule.id(), reason);
                }
            }
            String why = nearestMiss(expectedRule.id(), misses);
            fit.sort(Comparator.comparing(id -> !samePlace(expectedRule, generatedById.get(id))));
            candidates.put(expectedRule.id(), fit);
            reasons.put(expectedRule.id(), why);
        }

        Map<String, String> chosen = greedyMatching(expected.rules(), candidates);
        List<Match> matches = new ArrayList<>();
        for (Rule expectedRule : expected.rules()) {
            String generatedId = chosen.get(expectedRule.id());
            matches.add(new Match(expectedRule.id(), generatedId,
                    generatedId != null && samePlace(expectedRule, generatedById.get(generatedId)),
                    generatedId != null ? "matched " + generatedId : reasons.get(expectedRule.id())));
        }
        List<String> spare = generated.rules().stream().map(Rule::id)
                .filter(id -> !chosen.containsValue(id)).toList();
        return new Result(matches, expected.rules().size(), generated.rules().size(), spare);
    }

    /** Why these two rules are not one rule, or {@code null} when they are. */
    private static @Nullable String whyNot(Rule expected, Rule generated, RuleSet labeled,
            Map<String, Field> fields, List<ObjectNode> cases) {
        if (expected.actions().size() != generated.actions().size()) {
            return "the generated rule has " + generated.actions().size() + " actions, not "
                    + expected.actions().size();
        }
        Action expectedAction = expected.actions().getFirst();
        Action generatedAction = generated.actions().getFirst();
        String action = sameAction(expectedAction, generatedAction, labeled, cases);
        if (action != null) {
            return action;
        }
        if (!ConditionNormalizer.key(expected.condition(), fields)
                .equals(ConditionNormalizer.key(generated.condition(), fields))) {
            Set<String> unknown = FieldNames.of(generated.condition()).stream()
                    .filter(name -> !fields.containsKey(name)).collect(Collectors.toCollection(TreeSet::new));
            return unknown.isEmpty() ? "the condition is not equivalent after normalization"
                    : "the condition reads " + String.join(", ", unknown)
                            + " (no field of that name is expected)";
        }
        return null;
    }

    /** Document 4: outcome and terminal flag, or the same set target and an equal expression, or the same code. */
    private static @Nullable String sameAction(Action expected, Action generated, RuleSet labeled,
            List<ObjectNode> cases) {
        return switch (expected) {
            case Action.Decide decide -> generated instanceof Action.Decide other
                    && other.outcome() == decide.outcome() && other.isTerminal() == decide.isTerminal()
                    ? null : "the action is " + describe(generated) + ", not " + describe(expected);
            case Action.Flag flag -> generated instanceof Action.Flag other && other.code().equals(flag.code())
                    ? null : "the action is " + describe(generated) + ", not " + describe(expected);
            case Action.SetField set -> {
                if (!(generated instanceof Action.SetField other)) {
                    yield "the action is " + describe(generated) + ", not " + describe(expected);
                }
                if (!other.field().equals(set.field())) {
                    yield "it sets " + other.field() + ", not " + set.field();
                }
                yield SetExpressions.agree(labeled, set.field(), set.value(), other.value(), cases) ? null
                        : "the set value " + SetExpressions.difference(labeled, set.field(), set.value(),
                                other.value(), cases);
            }
        };
    }

    private static String describe(Action action) {
        return switch (action) {
            case Action.Decide decide -> decide.outcome().json() + (decide.isTerminal() ? " (terminal)" : "");
            case Action.Flag flag -> "flag " + flag.code();
            case Action.SetField set -> "set " + set.field();
        };
    }

    /** Document 4: "Provenance is correct when the paragraph index matches the expected rule's". */
    private static boolean samePlace(Rule expected, @Nullable Rule generated) {
        return generated != null && paragraphOf(expected) != null
                && paragraphOf(expected).equals(paragraphOf(generated));
    }

    private static @Nullable Integer paragraphOf(Rule rule) {
        return rule.provenance() instanceof Provenance.Quoted quoted ? quoted.paragraph() : null;
    }

    /**
     * Which near miss the report shows for an expected rule nothing matched. A generated rule carrying the same id
     * is the one a reader will look at, so its reason wins; ids are not compared when matching, only here. Failing
     * that, the most informative reason: a field the label does not declare, then a condition that did not reduce
     * to the same key, then a wrong action.
     */
    private static String nearestMiss(String expectedId, Map<String, String> misses) {
        if (misses.isEmpty()) {
            return "the generated rule set has no rules";
        }
        String sameId = misses.get(expectedId);
        if (sameId != null) {
            return sameId;
        }
        return misses.values().stream().min(Comparator.comparingInt(RuleMatcher::informativeness))
                .orElseThrow();
    }

    private static int informativeness(String reason) {
        if (reason.startsWith("the condition reads")) {
            return 0;
        }
        return reason.startsWith("the condition") ? 1 : 2;
    }

    /**
     * Document 4: a match is one to one. A generated rule can satisfy at most one expected rule --- two expected
     * rules it both satisfied would have the same action and the same normalized condition, which is to say they
     * would be the same rule --- so the candidate lists are disjoint and taking each expected rule's first
     * unclaimed candidate is already the largest matching there is. The candidates are ordered so that a rule
     * citing the expected paragraph comes first, which is what keeps provenance accuracy a measure of the model.
     */
    private static Map<String, String> greedyMatching(List<Rule> expected, Map<String, List<String>> candidates) {
        Map<String, String> chosen = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        for (Rule rule : expected) {
            candidates.getOrDefault(rule.id(), List.of()).stream()
                    .filter(generatedId -> !taken.contains(generatedId))
                    .findFirst()
                    .ifPresent(generatedId -> {
                        taken.add(generatedId);
                        chosen.put(rule.id(), generatedId);
                    });
        }
        return chosen;
    }
}
