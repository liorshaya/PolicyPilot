package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.FieldType;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Evaluates the expressions of Document 3 on {@link BigDecimal}: sums, differences and products are exact,
 * division and negative powers round to 12 places with HALF_EVEN, {@code round} uses HALF_EVEN, and
 * {@code months_between} counts whole calendar months. The validator has already checked types, arity and depth.
 */
final class ExpressionEvaluator {

    /** Document 3, Decimal semantics: division uses 12 decimal places. */
    static final int SCALE = 12;

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final Map<String, Field> fields;

    ExpressionEvaluator(Map<String, Field> fields) {
        this.fields = fields;
    }

    /**
     * The value of an expression over the current values: a number, or the ISO text of a date field that only
     * {@code months_between} reads.
     */
    Object evaluate(Expression expression, Map<String, Object> values) {
        return switch (expression) {
            case NumberLiteral number -> number.value();
            case FieldRef ref -> read(ref.field(), values);
            case Call call -> call(call, values);
        };
    }

    /** The expression as the trace shows it: {@code (78 - (term_months / 12))}, or {@code round(x, 2)}. */
    static String text(Expression expression) {
        return switch (expression) {
            case NumberLiteral number -> number.value().toPlainString();
            case FieldRef ref -> ref.field();
            case Call call -> {
                String operator = switch (call.fn()) {
                    case ADD -> "+";
                    case SUB -> "-";
                    case MUL -> "*";
                    case DIV -> "/";
                    default -> null;
                };
                List<String> args = call.args().stream().map(ExpressionEvaluator::text).toList();
                yield operator == null
                        ? call.fn().json() + "(" + String.join(", ", args) + ")"
                        : "(" + String.join(" " + operator + " ", args) + ")";
            }
        };
    }

    private Object read(String name, Map<String, Object> values) {
        Object value = values.get(name);
        if (value != null) {
            return value;
        }
        Field field = fields.get(name);
        EvaluationError error = field.isDerived() ? EvaluationError.EVAL_DERIVED_ABSENT
                : field.type() == FieldType.DATE ? EvaluationError.EVAL_ABSENT_DATE
                : EvaluationError.EVAL_ABSENT_FIELD;
        throw new EvaluationException(error, name);
    }

    private BigDecimal call(Call call, Map<String, Object> values) {
        List<Object> args = new ArrayList<>(call.args().size());
        for (Expression arg : call.args()) {
            args.add(evaluate(arg, values));
        }
        return switch (call.fn()) {
            case ADD -> numbers(args).reduce(BigDecimal.ZERO, BigDecimal::add);
            case SUB -> number(args, 0).subtract(number(args, 1));
            case MUL -> numbers(args).reduce(BigDecimal.ONE, BigDecimal::multiply);
            case DIV -> divide(number(args, 0), number(args, 1));
            case MIN -> numbers(args).reduce((a, b) -> b.compareTo(a) < 0 ? b : a).orElseThrow();
            case MAX -> numbers(args).reduce((a, b) -> b.compareTo(a) > 0 ? b : a).orElseThrow();
            case ABS -> number(args, 0).abs();
            case ROUND -> number(args, 0).setScale(number(args, 1).intValue(), ROUNDING);
            case POW -> power(number(args, 0), number(args, 1));
            case MONTHS_BETWEEN -> monthsBetween((String) args.get(0), (String) args.get(1));
        };
    }

    private static BigDecimal number(List<Object> args, int index) {
        return (BigDecimal) args.get(index);
    }

    private static Stream<BigDecimal> numbers(List<Object> args) {
        return args.stream().map(BigDecimal.class::cast);
    }

    private static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new EvaluationException(EvaluationError.EVAL_DIV_ZERO, dividend.toPlainString() + " / 0");
        }
        return dividend.divide(divisor, SCALE, ROUNDING);
    }

    /** Document 3: a whole exponent only; a negative one is a division at 12 places; 0^0 and 0^-n have no value. */
    private static BigDecimal power(BigDecimal base, BigDecimal exponent) {
        try {
            int n = exponent.intValueExact();
            if (base.signum() == 0 && n <= 0) {
                throw new EvaluationException(EvaluationError.EVAL_NON_FINITE, "zero to the power " + n);
            }
            return n >= 0 ? base.pow(n) : divide(BigDecimal.ONE, base.pow(-n));
        } catch (ArithmeticException e) {
            // intValueExact refuses a fractional exponent and one outside the int range
            throw new EvaluationException(EvaluationError.EVAL_NON_FINITE, "the exponent is not a whole number in range");
        }
    }

    /** Whole calendar months from the first date to the second; negative when the second comes first. */
    private static BigDecimal monthsBetween(String from, String to) {
        return BigDecimal.valueOf(ChronoUnit.MONTHS.between(LocalDate.parse(from), LocalDate.parse(to)));
    }
}
