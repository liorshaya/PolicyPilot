package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.FieldType;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Document 4, "Rule matching": two conditions are the same when their normalized keys are equal, where the
 * normalization is "combinators sorted, {@code not between} and two comparisons unified, {@code gte x} and
 * {@code gt x-1} on integers unified, enum lists sorted". Nothing beyond that sentence is normalized: algebraic
 * rearrangement and inlining a derived field are not, which is what {@code docs/eval/rule-match-lending.md}
 * measured and what the report's mismatch list names.
 *
 * <p>The condition is rewritten first --- the ranges into their comparisons, the integer bounds onto one side ---
 * and only then rendered, sorting and flattening each combinator as it goes. A field the rule set does not declare
 * keeps its name and its operator verbatim: it can then only equal a condition written the same way, which is the
 * behaviour a generated rule reading a field of its own must have.
 */
final class ConditionNormalizer {

    private ConditionNormalizer() {}

    /** The canonical form of one condition under the declared fields, as Document 4 defines equality. */
    static String key(Condition condition, Map<String, Field> fields) {
        return render(rewrite(condition, fields), fields);
    }

    /**
     * The normalizations that change the shape of the tree: {@code between} becomes its two comparisons,
     * {@code not between} becomes the two that negate it, and on an integer field {@code gt x} becomes
     * {@code gte x+1} and {@code lte x} becomes {@code lt x+1}, so the two ways of writing a bound meet.
     */
    private static Condition rewrite(Condition condition, Map<String, Field> fields) {
        return switch (condition) {
            case Condition.Always always -> always;
            case Condition.All all -> new Condition.All(rewriteEach(all.all(), fields));
            case Condition.Any any -> new Condition.Any(rewriteEach(any.any(), fields));
            case Condition.Not not -> {
                if (not.not() instanceof Condition.Comparison range && range.op() == Operator.BETWEEN) {
                    List<Operand> bounds = boundsOf(range);
                    yield rewrite(new Condition.Any(List.of(
                            new Condition.Comparison(range.field(), Operator.LT, bounds.get(0)),
                            new Condition.Comparison(range.field(), Operator.GT, bounds.get(1)))), fields);
                }
                yield new Condition.Not(rewrite(not.not(), fields));
            }
            case Condition.Comparison comparison -> {
                if (comparison.op() == Operator.BETWEEN) {
                    List<Operand> bounds = boundsOf(comparison);
                    yield rewrite(new Condition.All(List.of(
                            new Condition.Comparison(comparison.field(), Operator.GTE, bounds.get(0)),
                            new Condition.Comparison(comparison.field(), Operator.LTE, bounds.get(1)))), fields);
                }
                yield onIntegerBound(comparison, fields.get(comparison.field()));
            }
        };
    }

    private static List<Condition> rewriteEach(List<Condition> branches, Map<String, Field> fields) {
        return branches.stream().map(branch -> rewrite(branch, fields)).toList();
    }

    /** Document 4: "{@code gte x} and {@code gt x-1} on integers unified", in both directions. */
    private static Condition onIntegerBound(Condition.Comparison comparison, @Nullable Field field) {
        if (field == null || field.type() != FieldType.INTEGER
                || !(comparison.value() instanceof NumberLiteral number)
                || number.value().stripTrailingZeros().scale() > 0) {
            return comparison;
        }
        NumberLiteral above = new NumberLiteral(number.value().add(BigDecimal.ONE));
        return switch (comparison.op()) {
            case GT -> new Condition.Comparison(comparison.field(), Operator.GTE, above);
            case LTE -> new Condition.Comparison(comparison.field(), Operator.LT, above);
            default -> comparison;
        };
    }

    private static String render(Condition condition, Map<String, Field> fields) {
        return switch (condition) {
            case Condition.Always ignored -> "always";
            case Condition.Not not -> "not(" + render(not.not(), fields) + ")";
            case Condition.All all -> combinator("all", flatten(all, fields), fields);
            case Condition.Any any -> combinator("any", flatten(any, fields), fields);
            case Condition.Comparison comparison -> comparison(comparison, fields.get(comparison.field()));
        };
    }

    /** Every branch of a combinator, with the branches of any nested combinator of the same kind spliced in. */
    private static List<Condition> flatten(Condition combinator, Map<String, Field> fields) {
        List<Condition> branches = new ArrayList<>();
        List<Condition> children = combinator instanceof Condition.All all ? all.all()
                : ((Condition.Any) combinator).any();
        for (Condition child : children) {
            if (child.getClass() == combinator.getClass()) {
                branches.addAll(flatten(child, fields));
            } else {
                branches.add(child);
            }
        }
        return branches;
    }

    /** Document 4: "combinators sorted". A branch written twice counts once; one branch is that branch. */
    private static String combinator(String kind, List<Condition> branches, Map<String, Field> fields) {
        Set<String> keys = new TreeSet<>();
        branches.forEach(branch -> keys.add(render(branch, fields)));
        return keys.size() == 1 ? keys.iterator().next() : kind + "(" + String.join(",", keys) + ")";
    }

    private static String comparison(Condition.Comparison comparison, @Nullable Field field) {
        if (field != null && field.type() == FieldType.ENUM && field.values() != null) {
            String allowed = allowedValues(comparison, field.values());
            if (allowed != null) {
                return comparison.field() + " in " + allowed;
            }
        }
        return comparison.field() + " " + comparison.op().json()
                + (comparison.value() == null ? "" : " " + operand(comparison.value()));
    }

    /**
     * Document 4: "enum lists sorted". The enum is closed, so each of the four operators that name a set is read
     * as the set of values it allows; anything else ({@code present}, a {@code matches}) is {@code null} and is
     * rendered as it was written.
     */
    private static @Nullable String allowedValues(Condition.Comparison comparison, List<String> declared) {
        Set<String> named = new LinkedHashSet<>();
        switch (comparison.value()) {
            case StringLiteral one -> named.add(one.value());
            case LiteralList list -> list.values().forEach(value -> {
                if (value instanceof StringLiteral text) {
                    named.add(text.value());
                }
            });
            case null, default -> {
                return null;
            }
        }
        Set<String> allowed = new TreeSet<>();
        switch (comparison.op()) {
            case EQ, IN -> allowed.addAll(named);
            case NE, NOT_IN -> declared.stream().filter(value -> !named.contains(value)).forEach(allowed::add);
            default -> {
                return null;
            }
        }
        return String.join("|", allowed);
    }

    private static List<Operand> boundsOf(Condition.Comparison comparison) {
        if (comparison.value() instanceof LiteralList list && list.values().size() == 2) {
            return List.of(list.values().get(0), list.values().get(1));
        }
        throw new IllegalArgumentException("between must carry two bounds: " + comparison);
    }

    private static String operand(Operand operand) {
        return switch (operand) {
            case NumberLiteral number -> number.value().stripTrailingZeros().toPlainString();
            case StringLiteral text -> "\"" + text.value() + "\"";
            case BooleanLiteral bool -> String.valueOf(bool.value());
            case FieldRef ref -> ref.field();
            case Call call -> call.fn().json() + "("
                    + String.join("|", call.args().stream().map(ConditionNormalizer::expression).toList()) + ")";
            case LiteralList list -> String.join("|",
                    list.values().stream().map(ConditionNormalizer::operand).sorted().toList());
        };
    }

    private static String expression(Expression expression) {
        return operand((Operand) expression);
    }
}
