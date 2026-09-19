package com.liorshaya.policypilot.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Expression;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.FieldType;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Expressions on {@link BigDecimal} (Document 3, Types, Values and Arithmetic; Expressions). Every expected value is
 * what {@code ev_expr} and {@code expr_text} in {@code fixtures/reference/reference_check.py} return for the same
 * expression.
 */
@Requirement({"FR-8", "NFR-1"})
class ExpressionEvaluatorTest {

    private static final Map<String, Field> FIELDS = Map.of(
            "a", field("a", FieldType.NUMBER, false),
            "x", field("x", FieldType.NUMBER, true),
            "d", field("d", FieldType.DATE, false),
            "d1", field("d1", FieldType.DATE, false),
            "d2", field("d2", FieldType.DATE, false));

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(FIELDS);

    @Test
    void addSubMulAreExact() {
        assertThat(value(call(Function.ADD, num("0.1"), num("0.2")))).isEqualByComparingTo("0.3");
        assertThat(value(call(Function.MUL, num("123456789012345678901234567890"), num("10"))))
                .isEqualByComparingTo("1234567890123456789012345678900");
        assertThat(value(call(Function.SUB, num("1"), num("0.9999999999999")))).isEqualByComparingTo("1E-13");
    }

    @Test
    void divRoundsTo12PlacesHalfEven() {
        assertThat(value(call(Function.DIV, num("1"), num("3")))).isEqualByComparingTo("0.333333333333");
        assertThat(value(call(Function.DIV, num("2"), num("3")))).isEqualByComparingTo("0.666666666667");
        assertThat(value(call(Function.DIV, num("0.0000000000025"), num("1")))).isEqualByComparingTo("2E-12");
        assertThat(value(call(Function.DIV, num("0.0000000000035"), num("1")))).isEqualByComparingTo("4E-12");
    }

    @Test
    void divByZeroIsEvalDivZero() {
        assertThatThrownBy(() -> evaluator.evaluate(call(Function.DIV, num("1"), num("0")), Map.of()))
                .isInstanceOfSatisfying(EvaluationException.class,
                        e -> assertThat(e.error()).isEqualTo(EvaluationError.EVAL_DIV_ZERO));
    }

    @Test
    void roundUsesHalfEvenWithItsPlacesArgument() {
        assertThat(value(call(Function.ROUND, num("2.345"), num("2")))).isEqualByComparingTo("2.34");
        assertThat(value(call(Function.ROUND, num("2.355"), num("2")))).isEqualByComparingTo("2.36");
        assertThat(value(call(Function.ROUND, num("1234.5"), num("-2")))).isEqualByComparingTo("1.2E+3");
    }

    @Test
    void minMaxAbsAddAndMulTakeTheirArgumentsLeftToRight() {
        assertThat(value(call(Function.MIN, num("3"), num("1"), num("2")))).isEqualByComparingTo("1");
        assertThat(value(call(Function.MAX, num("3"), num("1"), num("2")))).isEqualByComparingTo("3");
        assertThat(value(call(Function.MAX, num("1"), num("3"), num("2")))).isEqualByComparingTo("3");
        assertThat(value(call(Function.ABS, num("-4.5")))).isEqualByComparingTo("4.5");
        assertThat(value(call(Function.ADD, num("1"), num("2"), num("3")))).isEqualByComparingTo("6");
        assertThat(value(call(Function.MUL, num("2"), num("3"), num("4")))).isEqualByComparingTo("24");
        // of equal values the first is kept, as Python's min and max do
        assertThat(value(call(Function.MIN, num("1.0"), num("1")))).hasToString("1.0");
        assertThat(value(call(Function.MAX, num("2.0"), num("2")))).hasToString("2.0");
    }

    @Test
    void powWithAWholeExponentIsExactAndANegativeOneIsADivision() {
        assertThat(value(call(Function.POW, num("1.0075"), num("-48")))).isEqualByComparingTo("0.698614135861");
        assertThat(value(call(Function.POW, num("2"), num("10")))).isEqualByComparingTo("1024");
        assertThat(value(call(Function.POW, num("1.5"), num("2")))).isEqualByComparingTo("2.25");
        assertThat(value(call(Function.POW, num("2"), num("3.0")))).isEqualByComparingTo("8");
        // a whole exponent, zero included, is an exact power, not a division rounded to 12 places
        assertThat(value(call(Function.POW, num("2"), num("0")))).hasToString("1");
    }

    @Test
    void powWithoutAFiniteDecimalResultIsEvalNonFinite() {
        assertError(call(Function.POW, num("1.0075"), num("0.5")), Map.of(), EvaluationError.EVAL_NON_FINITE);
        assertError(call(Function.POW, num("0"), num("0")), Map.of(), EvaluationError.EVAL_NON_FINITE);
        assertError(call(Function.POW, num("0"), num("-1")), Map.of(), EvaluationError.EVAL_NON_FINITE);
        assertError(call(Function.POW, num("2"), num("10000000000")), Map.of(), EvaluationError.EVAL_NON_FINITE);
        assertThat(value(call(Function.POW, num("0"), num("3")))).isEqualByComparingTo("0");
    }

    @Test
    void monthsBetweenCountsWholeCalendarMonths() {
        Call months = call(Function.MONTHS_BETWEEN, new FieldRef("d1"), new FieldRef("d2"));

        assertThat(evaluator.evaluate(months, dates("2025-01-15", "2026-03-10"))).isEqualTo(BigDecimal.valueOf(13));
        assertThat(evaluator.evaluate(months, dates("2026-03-10", "2025-01-15"))).isEqualTo(BigDecimal.valueOf(-13));
        assertThat(evaluator.evaluate(months, dates("2025-01-31", "2025-02-28"))).isEqualTo(BigDecimal.ZERO);
        assertThat(evaluator.evaluate(months, dates("2025-01-15", "2025-02-15"))).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void fieldReferenceReadsTheCaseAndAnAbsentValueIsAnError() {
        assertThat(evaluator.evaluate(new FieldRef("a"), Map.of("a", new BigDecimal("5"))))
                .isEqualTo(new BigDecimal("5"));
        assertError(new FieldRef("a"), Map.of(), EvaluationError.EVAL_ABSENT_FIELD);
        assertError(call(Function.MONTHS_BETWEEN, new FieldRef("d"), new FieldRef("d")), Map.of(),
                EvaluationError.EVAL_ABSENT_DATE);
        assertError(new FieldRef("x"), Map.of(), EvaluationError.EVAL_DERIVED_ABSENT);
        assertThatThrownBy(() -> evaluator.evaluate(new FieldRef("x"), Map.of()))
                .isInstanceOfSatisfying(EvaluationException.class, e -> assertThat(e.detail()).isEqualTo("x"));
    }

    @Test
    void expressionTextIsTheTraceRendering() {
        RuleSet lending = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());

        Expression r116 = (Expression) ((Condition.Comparison) ((Condition.All) rule(lending, "R-116").condition())
                .all().get(1)).value();
        assertThat(ExpressionEvaluator.text(r116)).isEqualTo("(78 - (term_months / 12))");
        assertThat(ExpressionEvaluator.text(setValue(lending, "R-010")))
                .isEqualTo("round(((requested_amount * 0.0075) / (1 - pow(1.0075, (0 - term_months)))), 2)");
        assertThat(ExpressionEvaluator.text(setValue(lending, "R-020")))
                .isEqualTo("round(((existing_monthly_debt + monthly_installment) / monthly_income), 4)");
    }

    private BigDecimal value(Expression expression) {
        return (BigDecimal) evaluator.evaluate(expression, Map.of());
    }

    private void assertError(Expression expression, Map<String, Object> values, EvaluationError error) {
        assertThatThrownBy(() -> evaluator.evaluate(expression, values))
                .isInstanceOfSatisfying(EvaluationException.class, e -> assertThat(e.error()).isEqualTo(error));
    }

    private static Map<String, Object> dates(String from, String to) {
        Map<String, Object> values = new HashMap<>();
        values.put("d1", from);
        values.put("d2", to);
        return values;
    }

    private static Call call(Function fn, Expression... args) {
        return new Call(fn, List.of(args));
    }

    private static NumberLiteral num(String value) {
        return new NumberLiteral(new BigDecimal(value));
    }

    private static Rule rule(RuleSet ruleSet, String id) {
        return ruleSet.rules().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static Expression setValue(RuleSet ruleSet, String id) {
        return (Expression) ((Action.SetField) rule(ruleSet, id).actions().getFirst()).value();
    }

    private static Field field(String name, FieldType type, boolean derived) {
        return new Field(name, type, null, null, null, derived, null, null, null, null, null, null, null);
    }
}
