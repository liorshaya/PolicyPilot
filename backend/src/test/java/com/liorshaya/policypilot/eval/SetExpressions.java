package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.engine.CompiledRuleSet;
import com.liorshaya.policypilot.engine.Decision;
import com.liorshaya.policypilot.engine.Evaluation;
import com.liorshaya.policypilot.engine.RuleEngine;
import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Document 4, "Rule matching": a {@code set} action matches when it writes "the same {@code set} target and an
 * expression that evaluates equal on the policy's cases". The comparison runs through the real engine, never
 * through arithmetic of this code's own: the expected rule set's own {@code set} rules are kept, the one that
 * writes the target is replaced by the expression under test, and every labeled case is evaluated. What is
 * compared is the derived value the engine wrote, case by case, including where it wrote none.
 *
 * <p>Only the expression is swapped; the expected rule's condition is used for both, so a difference here is a
 * difference of arithmetic and the condition is compared separately, the way Document 4 separates the two.
 */
final class SetExpressions {

    private static final RuleEngine ENGINE = new RuleEngine();
    /** Stands for a case the expression could not be evaluated on; never equal to a value the engine wrote. */
    private static final Object UNEVALUABLE = new Object();

    private SetExpressions() {}

    /** The derived value of {@code target} on each case, as the engine writes it with this expression. */
    static List<@Nullable Object> valuesOf(RuleSet expected, String target, Value expression,
            List<ObjectNode> cases) {
        CompiledRuleSet compiled = CompiledRuleSet.compile(derivationsOnly(expected, target, expression));
        List<@Nullable Object> values = new ArrayList<>();
        for (ObjectNode input : cases) {
            try {
                Evaluation evaluation = ENGINE.evaluate(compiled, input.deepCopy());
                values.add(evaluation instanceof Decision decision ? decision.derived().get(target) : null);
            } catch (RuntimeException e) {
                // an expression the labeled fields cannot satisfy writes no value here, and agrees with none
                values.add(UNEVALUABLE);
            }
        }
        return values;
    }

    /** True when the two expressions write the same value on every labeled case, the cases where neither does. */
    static boolean agree(RuleSet expected, String target, Value written, Value candidate, List<ObjectNode> cases) {
        List<@Nullable Object> theirs = valuesOf(expected, target, candidate, cases);
        List<@Nullable Object> ours = valuesOf(expected, target, written, cases);
        return ours.equals(theirs);
    }

    /** How many cases the two disagree on, and the first one, for the report's mismatch line. */
    static String difference(RuleSet expected, String target, Value written, Value candidate,
            List<ObjectNode> cases) {
        List<@Nullable Object> ours = valuesOf(expected, target, written, cases);
        List<@Nullable Object> theirs = valuesOf(expected, target, candidate, cases);
        int differing = 0;
        String first = "";
        for (int at = 0; at < ours.size(); at++) {
            if (!Objects.equals(ours.get(at), theirs.get(at))) {
                differing++;
                if (first.isEmpty()) {
                    first = " first on case " + (at + 1) + " (expected " + ours.get(at) + ", generated "
                            + theirs.get(at) + ")";
                }
            }
        }
        return differing == 0 ? "equal on all " + ours.size() + " cases"
                : "differs on " + differing + " of " + ours.size() + " cases;" + first;
    }

    /**
     * The expected rule set reduced to the {@code set} rules that feed the target, with the target's own written
     * by the expression under test. Keeping the other derivations is what lets a ratio be compared: it reads the
     * installment the rule above it wrote.
     */
    private static RuleSet derivationsOnly(RuleSet expected, String target, Value expression) {
        List<Rule> rules = new ArrayList<>();
        for (Rule rule : expected.rules()) {
            List<Action> actions = rule.actions().stream().filter(Action.SetField.class::isInstance)
                    .map(action -> ((Action.SetField) action).field().equals(target)
                            ? new Action.SetField(target, expression) : action)
                    .toList();
            if (!actions.isEmpty()) {
                rules.add(new Rule(rule.id(), rule.label(), rule.priority(), rule.enabled(), rule.condition(),
                        actions, rule.provenance(), rule.tags()));
            }
        }
        return new RuleSet(expected.dslVersion(), expected.id(), expected.name(), expected.language(),
                expected.description(), expected.fields(), expected.defaults(), rules);
    }
}
