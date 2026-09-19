package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.engine.Decision.Candidate;
import com.liorshaya.policypilot.engine.Decision.Flag;
import com.liorshaya.policypilot.engine.TraceStep.Applied;
import com.liorshaya.policypilot.engine.TraceStep.Compared;
import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.Outcome;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.validation.CaseValidation;
import com.liorshaya.policypilot.rules.validation.CaseValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * The evaluation algorithm of Document 3 (Evaluation Semantics, steps 1 to 8): validate the case, apply defaults,
 * walk the rules in priority order, trace every rule, stop at the first terminal decision, otherwise resolve the
 * candidates by severity (reject over refer over approve) or fall back to the default. No clock, no randomness, no
 * I/O: the same case against the same compiled rule set always yields the same decision.
 */
public final class RuleEngine {

    private final CaseValidator cases = new CaseValidator();

    /** Decides one case, or returns the case error when the case is not valid. */
    public Evaluation evaluate(CompiledRuleSet compiled, ObjectNode input) {
        CaseValidation validation = cases.validate(compiled.ruleSet(), input);
        if (!validation.isValid()) {
            return new CaseError(validation.problems());
        }
        Map<String, Object> values = new HashMap<>();
        validation.values().forEach((name, literal) -> values.put(name, ConditionEvaluator.literal(literal)));
        return new Run(compiled, values).decide();
    }

    /**
     * What-if (Document 3, Simulation): the same rule set on the base input with some fields replaced. The result is
     * marked as a simulation and is never stored; the base input is not modified.
     */
    public Evaluation simulate(CompiledRuleSet compiled, ObjectNode base, ObjectNode overrides) {
        ObjectNode input = base.deepCopy();
        overrides.properties().forEach(entry -> input.set(entry.getKey(), entry.getValue().deepCopy()));
        Evaluation evaluation = evaluate(compiled, input);
        return evaluation instanceof Decision decision ? decision.asSimulation(overrides) : evaluation;
    }

    /** One evaluation: the values as they change, the trace, flags and candidates so far. */
    private static final class Run {

        private final CompiledRuleSet compiled;
        private final Map<String, Object> values;
        private final ExpressionEvaluator expressions;
        private final ConditionEvaluator conditions;
        private final List<TraceStep> trace = new ArrayList<>();
        private final List<Flag> flags = new ArrayList<>();
        private final List<Candidate> candidates = new ArrayList<>();
        private final Map<String, String> candidateReasons = new HashMap<>();
        private @Nullable Rule decidedBy;
        private Action.@Nullable Decide decision;

        Run(CompiledRuleSet compiled, Map<String, Object> values) {
            this.compiled = compiled;
            this.values = values;
            this.expressions = new ExpressionEvaluator(compiled.fields());
            this.conditions = new ConditionEvaluator(compiled, expressions);
        }

        Decision decide() {
            for (Rule rule : compiled.rules()) {
                if (!rule.isEnabled()) {
                    trace.add(identity(rule, TraceStep.Status.DISABLED));
                } else if (decidedBy != null) {
                    trace.add(identity(rule, TraceStep.Status.SKIPPED));
                } else {
                    List<Compared> compared = new ArrayList<>();
                    try {
                        evaluate(rule, compared);
                    } catch (EvaluationException e) {
                        trace.add(new TraceStep(rule.id(), rule.label(), rule.priority(), TraceStep.Status.ERROR,
                                compared, null, new TraceStep.Failure(e.error(), e.detail()), rule.provenance()));
                        return new Decision(Decision.Status.ERROR, null, null, null, false, derived(), List.of(),
                                List.of(), trace, e.error(), rule.id(), null);
                    }
                }
            }
            return resolve();
        }

        private void evaluate(Rule rule, List<Compared> compared) {
            boolean fired = conditions.evaluate(rule.condition(), values, compared);
            List<Applied> applied = fired ? apply(rule) : null;
            trace.add(new TraceStep(rule.id(), rule.label(), rule.priority(),
                    fired ? TraceStep.Status.FIRED : TraceStep.Status.NOT_FIRED, compared, applied, null,
                    rule.provenance()));
        }

        private List<Applied> apply(Rule rule) {
            List<Applied> applied = new ArrayList<>();
            for (Action action : rule.actions()) {
                switch (action) {
                    case Action.SetField set -> {
                        Object from = values.get(set.field());
                        Object to = set.value() instanceof Literal literal
                                ? ConditionEvaluator.literal(literal)
                                : expressions.evaluate((Expression) set.value(), values);
                        values.put(set.field(), to);
                        applied.add(new Applied.Set(set.field(), from, to));
                    }
                    case Action.Flag flag -> {
                        flags.add(new Flag(flag.code(), flag.message(), rule.id()));
                        applied.add(new Applied.Flagged(flag.code()));
                    }
                    case Action.Decide decide -> {
                        applied.add(new Applied.Decided(decide.outcome(), decide.isTerminal()));
                        if (decide.isTerminal()) {
                            decidedBy = rule;
                            decision = decide;
                        } else {
                            candidates.add(new Candidate(decide.outcome(), rule.id()));
                            candidateReasons.put(rule.id(), decide.reason());
                        }
                    }
                }
            }
            return applied;
        }

        /** Step 6: the terminal decision, else the most severe candidate (the first of equals), else the default. */
        private Decision resolve() {
            if (decidedBy != null) {
                return decision(decision.outcome(), decision.reason(), decidedBy.id(), true);
            }
            Candidate winner = null;
            for (Candidate candidate : candidates) {
                if (winner == null || severity(candidate.outcome()) > severity(winner.outcome())) {
                    winner = candidate;
                }
            }
            RuleSet ruleSet = compiled.ruleSet();
            return winner == null
                    ? decision(ruleSet.defaults().outcome(), ruleSet.defaults().reason(), null, false)
                    : decision(winner.outcome(), candidateReasons.get(winner.ruleId()), winner.ruleId(), false);
        }

        private Decision decision(Outcome outcome, String reason, @Nullable String ruleId, boolean terminal) {
            return new Decision(Decision.Status.OK, outcome, reason, ruleId, terminal, derived(), flags, candidates,
                    trace, null, null, null);
        }

        private static int severity(Outcome outcome) {
            return switch (outcome) {
                case REJECT -> 3;
                case REFER -> 2;
                case APPROVE -> 1;
            };
        }

        /** Every derived field in declaration order, with {@code null} when no rule set it. */
        private Map<String, @Nullable Object> derived() {
            Map<String, @Nullable Object> derived = new LinkedHashMap<>();
            for (Field field : compiled.ruleSet().fields()) {
                if (field.isDerived()) {
                    derived.put(field.name(), values.get(field.name()));
                }
            }
            return derived;
        }

        private static TraceStep identity(Rule rule, TraceStep.Status status) {
            return new TraceStep(rule.id(), rule.label(), rule.priority(), status, null, null, null, null);
        }
    }
}
