package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import com.liorshaya.policypilot.rules.model.Value;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * A rule as retrieval reads it (Document 4, Retrieval Pipeline, Corpus): its id and label, its condition in the
 * decision-table cell grammar of Document 3, one line per action with its reason, and the passage it quotes. The
 * vocabulary is the one the decision table shows, so a chunk and the screen say a rule the same way.
 */
final class RuleText {

    private final Map<String, Field> fields;

    RuleText(List<Field> fields) {
        this.fields = fields.stream().collect(Collectors.toMap(Field::name, field -> field));
    }

    String render(Rule rule) {
        List<String> lines = new ArrayList<>();
        lines.add(rule.id() + " · " + rule.label() + (rule.isEnabled() ? "" : " (disabled)"));
        lines.add("When: " + condition(rule.condition()));
        rule.actions().forEach(action -> lines.add("Then: " + action(action)));
        if (rule.provenance() instanceof Provenance.Quoted quoted) {
            lines.add("Source, paragraph " + quoted.paragraph() + ": \"" + quoted.quote() + "\"");
        }
        return String.join("\n", lines);
    }

    /** Leaves in the cell grammar; {@code all} as AND, {@code any} as OR, {@code not} as {@code NOT [...]}. */
    private String condition(Condition condition) {
        return switch (condition) {
            case Condition.Always ignored -> "always";
            case Condition.Comparison comparison -> comparison.field() + " " + cell(comparison);
            case Condition.All all -> joined(all.all(), " AND ");
            case Condition.Any any -> joined(any.any(), " OR ");
            case Condition.Not not -> "NOT [" + condition(not.not()) + "]";
        };
    }

    private String joined(List<Condition> children, String separator) {
        return children.stream().map(this::nested).collect(Collectors.joining(separator));
    }

    /** A combinator inside another is bracketed, so AND and OR never mix without saying which binds. */
    private String nested(Condition child) {
        return child instanceof Condition.All || child instanceof Condition.Any ? "[" + condition(child) + "]"
                : condition(child);
    }

    /** Document 3, Cell grammar: the operator and the operand of one leaf. */
    private String cell(Condition.Comparison comparison) {
        @Nullable Field field = fields.get(comparison.field());
        @Nullable Operand value = comparison.value();
        return switch (comparison.op()) {
            case PRESENT -> "present";
            case ABSENT -> "absent";
            case EQ -> "= " + operand(value, field);
            case NE -> "≠ " + operand(value, field);
            case LT -> "< " + operand(value, field);
            case LTE -> "≤ " + operand(value, field);
            case GT -> "> " + operand(value, field);
            case GTE -> "≥ " + operand(value, field);
            case BETWEEN -> "[" + literals(value, field, " .. ") + "]";
            case IN -> "∈ {" + literals(value, field, ", ") + "}";
            case NOT_IN -> "∉ {" + literals(value, field, ", ") + "}";
            case MATCHES -> "~ /" + operand(value, null) + "/";
        };
    }

    private static String literals(@Nullable Operand value, @Nullable Field field, String separator) {
        if (!(value instanceof LiteralList list)) {
            throw new IllegalArgumentException("a list operator without a list: " + value);
        }
        return list.values().stream().map(literal -> literal(literal, field)).collect(Collectors.joining(separator));
    }

    private static String operand(@Nullable Operand value, @Nullable Field field) {
        return switch (value) {
            case Literal literal -> literal(literal, field);
            case FieldRef ref -> ref.field();
            case Call call -> expression(call, 0);
            case null, default -> throw new IllegalArgumentException("a comparison without its operand: " + value);
        };
    }

    /** A literal as a cell writes it: numbers grouped and followed by the field's unit, the rest as they are. */
    private static String literal(Literal literal, @Nullable Field field) {
        return switch (literal) {
            case NumberLiteral number -> field != null && field.unit() != null
                    ? number(number.value()) + " " + field.unit()
                    : number(number.value());
            case StringLiteral string -> string.value();
            case BooleanLiteral bool -> Boolean.toString(bool.value());
        };
    }

    private String action(Action action) {
        return switch (action) {
            case Action.Decide decide -> decide.outcome().json() + (decide.isTerminal() ? "" : " (not terminal)")
                    + ": " + decide.reason();
            case Action.Flag flag -> "flag " + flag.code() + ": " + flag.message();
            case Action.SetField set -> "set " + set.field() + " = " + value(set.value());
        };
    }

    private static String value(Value value) {
        return switch (value) {
            case Literal literal -> literal(literal, null);
            case FieldRef ref -> ref.field();
            case Call call -> expression(call, 0);
        };
    }

    /**
     * The infix text of an expression (Document 3: {@code 78 − term_months / 12}), with parentheses only where the
     * precedence needs them; the functions that have no sign are written as calls.
     */
    private static String expression(Expression expression, int outer) {
        return switch (expression) {
            case NumberLiteral number -> number(number.value());
            case FieldRef ref -> ref.field();
            case Call call -> call(call, outer);
        };
    }

    private static String call(Call call, int outer) {
        @Nullable Infix infix = Infix.of(call.fn());
        if (infix == null) {
            return call.fn().json() + "("
                    + call.args().stream().map(arg -> expression(arg, 0)).collect(Collectors.joining(", ")) + ")";
        }
        List<String> operands = new ArrayList<>();
        for (int i = 0; i < call.args().size(); i++) {
            // the right operand of a subtraction or a division binds tighter than an equal operator beside it
            boolean right = i > 0 && !infix.associative();
            operands.add(expression(call.args().get(i), right ? infix.precedence() + 1 : infix.precedence()));
        }
        String text = String.join(infix.sign(), operands);
        return infix.precedence() < outer ? "(" + text + ")" : text;
    }

    /** A number as the document wrote it, without trailing zeros, grouped by thousands. */
    static String number(BigDecimal value) {
        BigDecimal plain = value.stripTrailingZeros();
        if (plain.scale() < 0) {
            plain = plain.setScale(0);
        }
        String digits = plain.abs().toPlainString();
        int point = digits.indexOf('.');
        String whole = point < 0 ? digits : digits.substring(0, point);
        String fraction = point < 0 ? "" : digits.substring(point);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(whole.charAt(i));
        }
        return (plain.signum() < 0 ? "-" : "") + grouped + fraction;
    }

    /** The four functions written with a sign, and how tightly each binds. */
    private enum Infix {
        ADD(" + ", 1, true),
        SUB(" − ", 1, false),
        MUL(" × ", 2, true),
        DIV(" / ", 2, false);

        private final String sign;
        private final int precedence;
        private final boolean associative;

        Infix(String sign, int precedence, boolean associative) {
            this.sign = sign;
            this.precedence = precedence;
            this.associative = associative;
        }

        static @Nullable Infix of(Function fn) {
            return switch (fn) {
                case ADD -> ADD;
                case SUB -> SUB;
                case MUL -> MUL;
                case DIV -> DIV;
                default -> null;
            };
        }

        String sign() {
            return sign;
        }

        int precedence() {
            return precedence;
        }

        boolean associative() {
            return associative;
        }
    }
}
