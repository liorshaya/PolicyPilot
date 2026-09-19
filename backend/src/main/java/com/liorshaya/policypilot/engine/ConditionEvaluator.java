package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.engine.TraceStep.Compared;
import com.liorshaya.policypilot.engine.TraceStep.ExpressionValue;
import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates a condition tree (Document 3, Conditions and step 4): every leaf is evaluated, with no short circuit,
 * and recorded in tree order. A missing optional case field makes a comparison false, except {@code absent}; an unset
 * derived field is an error, except for {@code present} and {@code absent}.
 */
final class ConditionEvaluator {

    /** Document 3 and Document 5: a {@code matches} input is capped at 2,000 characters. */
    static final int MAX_MATCH_INPUT = 2000;

    private final CompiledRuleSet compiled;
    private final ExpressionEvaluator expressions;

    ConditionEvaluator(CompiledRuleSet compiled, ExpressionEvaluator expressions) {
        this.compiled = compiled;
        this.expressions = expressions;
    }

    boolean evaluate(Condition condition, Map<String, Object> values, List<Compared> out) {
        return switch (condition) {
            case Condition.Comparison comparison -> compare(comparison, values, out);
            case Condition.All all -> {
                boolean result = true;
                for (Condition child : all.all()) {
                    result &= evaluate(child, values, out);   // no short circuit: every leaf is traced
                }
                yield result;
            }
            case Condition.Any any -> {
                boolean result = false;
                for (Condition child : any.any()) {
                    result |= evaluate(child, values, out);
                }
                yield result;
            }
            case Condition.Not not -> !evaluate(not.not(), values, out);
            case Condition.Always always -> true;
        };
    }

    private boolean compare(Condition.Comparison comparison, Map<String, Object> values, List<Compared> out) {
        Field field = compiled.fields().get(comparison.field());
        Operator op = comparison.op();
        Object actual = values.get(comparison.field());
        boolean presence = op == Operator.PRESENT || op == Operator.ABSENT;
        if (actual == null && field.isDerived() && !presence) {
            throw new EvaluationException(EvaluationError.EVAL_DERIVED_ABSENT, field.name());
        }
        Object expected = presence ? null : operand(Objects.requireNonNull(comparison.value()), values);
        boolean result = switch (op) {
            case PRESENT -> actual != null;
            case ABSENT -> actual == null;
            default -> actual != null && test(op, actual, unwrap(expected), comparison);
        };
        out.add(new Compared(comparison.field(), op.json(), expected, actual, result));
        return result;
    }

    private boolean test(Operator op, Object actual, Object expected, Condition.Comparison comparison) {
        return switch (op) {
            case EQ -> same(actual, expected);
            case NE -> !same(actual, expected);
            case LT -> order(actual, expected) < 0;
            case LTE -> order(actual, expected) <= 0;
            case GT -> order(actual, expected) > 0;
            case GTE -> order(actual, expected) >= 0;
            case BETWEEN -> order(actual, list(expected).get(0)) >= 0 && order(actual, list(expected).get(1)) <= 0;
            case IN -> list(expected).stream().anyMatch(value -> same(actual, value));
            case NOT_IN -> list(expected).stream().noneMatch(value -> same(actual, value));
            // MATCHES; present and absent never reach here
            default -> matches((String) actual, ((StringLiteral) comparison.value()).value());
        };
    }

    private boolean matches(String actual, String regex) {
        int end = actual.offsetByCodePoints(0, Math.min(MAX_MATCH_INPUT, actual.codePointCount(0, actual.length())));
        return compiled.pattern(regex).matches(actual.substring(0, end));
    }

    /** The operand as the trace shows it: a literal, a list of literals, or an expression's value and text. */
    private Object operand(Operand operand, Map<String, Object> values) {
        return switch (operand) {
            case LiteralList list -> list.values().stream().map(ConditionEvaluator::literal).toList();
            case Literal literal -> literal(literal);
            case Expression expression -> new ExpressionValue(expressions.evaluate(expression, values),
                    ExpressionEvaluator.text(expression));
        };
    }

    static Object literal(Literal literal) {
        return switch (literal) {
            case NumberLiteral number -> number.value();
            case StringLiteral text -> text.value();
            case BooleanLiteral bool -> bool.value();
        };
    }

    private static Object unwrap(Object expected) {
        return expected instanceof ExpressionValue value ? value.value() : expected;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object expected) {
        return (List<Object>) expected;
    }

    /**
     * Equality by type: numbers by value (1.0 equals 1), strings and booleans exactly. The validator has checked
     * that both sides have the same type.
     */
    private static boolean same(Object a, Object b) {
        return a instanceof BigDecimal number ? number.compareTo((BigDecimal) b) == 0 : a.equals(b);
    }

    /** Numeric order for numbers, calendar order for ISO dates (their text sorts as the calendar does). */
    @SuppressWarnings("unchecked")
    private static int order(Object a, Object b) {
        return ((Comparable<Object>) a).compareTo(b);
    }
}
