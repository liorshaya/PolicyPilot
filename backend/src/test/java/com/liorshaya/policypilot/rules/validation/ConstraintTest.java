package com.liorshaya.policypilot.rules.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.FieldType;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import com.liorshaya.policypilot.rules.validation.Constraint.EnumSet;
import com.liorshaya.policypilot.rules.validation.Constraint.Interval;
import com.liorshaya.policypilot.support.Requirement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The value sets behind RULE_UNREACHABLE and RULE_OVERLAP_CONFLICT (Document 3). Expected values follow the
 * {@code Interval} and {@code EnumSet} classes of {@code reference_check.py}: bounds are compared numerically, and
 * where two bounds are equal the open one is the tighter.
 */
@Requirement({"FR-3", "FR-6"})
class ConstraintTest {

    private static final Map<String, Field> FIELDS = Map.of(
            "x", field("x", FieldType.NUMBER, null),
            "e", field("e", FieldType.ENUM, List.of("a", "b", "c")),
            "flag", field("flag", FieldType.BOOLEAN, null));

    @Test
    void eachOperatorMapsToItsInterval() {
        assertThat(Interval.of(Operator.EQ, number(5))).isEqualTo(closed(5, 5));
        assertThat(Interval.of(Operator.LT, number(5))).isEqualTo(new Interval(null, false, dec(5), false));
        assertThat(Interval.of(Operator.LTE, number(5))).isEqualTo(new Interval(null, false, dec(5), true));
        assertThat(Interval.of(Operator.GT, number(5))).isEqualTo(new Interval(dec(5), false, null, false));
        assertThat(Interval.of(Operator.GTE, number(5))).isEqualTo(new Interval(dec(5), true, null, false));
        assertThat(Interval.of(Operator.BETWEEN, new LiteralList(List.of(number(1), number(2)))))
                .isEqualTo(closed(1, 2));
        assertThat(Interval.of(Operator.NE, number(5))).isNull();
        assertThat(Interval.of(Operator.LT, new FieldRef("x"))).isNull();
    }

    @Test
    void intersectionKeepsTheTighterBoundAndTheOpenOneOnATie() {
        Interval zeroToTen = closed(0, 10);
        Interval openZeroToFive = new Interval(dec(0), false, dec(5), false);

        assertThat(zeroToTen.intersect(openZeroToFive)).isEqualTo(openZeroToFive);
        assertThat(openZeroToFive.intersect(zeroToTen)).isEqualTo(openZeroToFive);
        assertThat(new Interval(null, false, null, false).intersect(zeroToTen)).isEqualTo(zeroToTen);
        assertThat(zeroToTen.intersect(new Interval(null, false, null, false))).isEqualTo(zeroToTen);
        assertThat(closed(0, 10).intersect(closed(3, 20))).isEqualTo(closed(3, 10));
        assertThat(closed(0, 10).intersect(new Interval(dec(0), true, dec(10), false)))
                .isEqualTo(new Interval(dec(0), true, dec(10), false));
        assertThat(new Interval(dec(0), true, dec(10), false).intersect(closed(0, 10)))
                .isEqualTo(new Interval(dec(0), true, dec(10), false));
    }

    @Test
    void anIntervalIsEmptyOnlyWhenItsBoundsLeaveNoValue() {
        assertThat(closed(5, 5).isEmpty()).isFalse();
        assertThat(new Interval(dec(5), true, dec(5), false).isEmpty()).isTrue();
        assertThat(new Interval(dec(5), false, dec(5), true).isEmpty()).isTrue();
        assertThat(closed(6, 5).isEmpty()).isTrue();
        assertThat(new Interval(dec(5), true, null, false).isEmpty()).isFalse();
        assertThat(new Interval(null, false, dec(5), true).isEmpty()).isFalse();
    }

    @Test
    void subsetRespectsOpenAndClosedBounds() {
        Interval below18 = new Interval(null, false, dec(18), false);
        Interval below21 = new Interval(null, false, dec(21), false);
        Interval upTo21 = new Interval(null, false, dec(21), true);
        Interval above0 = new Interval(dec(0), false, null, false);
        Interval from0 = new Interval(dec(0), true, null, false);

        assertThat(below18.isSubsetOf(below21)).isTrue();
        assertThat(below21.isSubsetOf(below18)).isFalse();
        assertThat(below21.isSubsetOf(upTo21)).isTrue();
        assertThat(upTo21.isSubsetOf(below21)).isFalse();
        assertThat(closed(21, 21).isSubsetOf(upTo21)).isTrue();
        assertThat(closed(21, 21).isSubsetOf(below21)).isFalse();
        assertThat(above0.isSubsetOf(from0)).isTrue();
        assertThat(from0.isSubsetOf(above0)).isFalse();
        assertThat(closed(-1, 5).isSubsetOf(from0)).isFalse();
        assertThat(below18.isSubsetOf(from0)).isFalse();
        assertThat(above0.isSubsetOf(below21)).isFalse();
        assertThat(closed(1, 30).isSubsetOf(below21)).isFalse();
        assertThat(closed(1, 20).isSubsetOf(new Interval(null, false, null, false))).isTrue();
    }

    @Test
    void touchingIntervalsOverlapOnlyWhenBothIncludeThePoint() {
        Interval below10 = new Interval(null, false, dec(10), false);
        Interval upTo10 = new Interval(null, false, dec(10), true);
        Interval from10 = new Interval(dec(10), true, null, false);

        assertThat(below10.overlaps(from10)).isFalse();
        assertThat(upTo10.overlaps(from10)).isTrue();
        assertThat(new Interval(dec(10), false, null, false).overlaps(closed(5, 20))).isTrue();
    }

    @Test
    void enumConstraintsFollowTheOperator() {
        List<String> values = List.of("a", "b", "c");

        assertThat(EnumSet.of(Operator.EQ, text("a"), values)).isEqualTo(new EnumSet(Set.of("a")));
        assertThat(EnumSet.of(Operator.EQ, text("z"), values)).isEqualTo(new EnumSet(Set.of()));
        assertThat(EnumSet.of(Operator.NE, text("a"), values)).isEqualTo(new EnumSet(Set.of("b", "c")));
        assertThat(EnumSet.of(Operator.IN, texts("a", "b"), values)).isEqualTo(new EnumSet(Set.of("a", "b")));
        assertThat(EnumSet.of(Operator.NOT_IN, texts("a", "b"), values)).isEqualTo(new EnumSet(Set.of("c")));
        assertThat(EnumSet.of(Operator.EQ, new FieldRef("other"), values)).isNull();
        assertThat(EnumSet.of(Operator.NE, new FieldRef("other"), values)).isNull();
        assertThat(EnumSet.of(Operator.PRESENT, null, values)).isNull();
        assertThat(new EnumSet(Set.of("a")).isSubsetOf(new EnumSet(Set.of("a", "b")))).isTrue();
        assertThat(new EnumSet(Set.of("a", "c")).isSubsetOf(new EnumSet(Set.of("a", "b")))).isFalse();
    }

    @Test
    void onlyASingleComparisonOrAnAllOfComparisonsHasAConstraint() {
        Condition xAbove0 = compare("x", Operator.GT, number(0));
        Condition xBelow10 = compare("x", Operator.LT, number(10));
        Condition eIn = compare("e", Operator.IN, texts("a", "b"));
        Condition eNotA = compare("e", Operator.NE, text("a"));

        assertThat(Constraint.of(xAbove0, FIELDS)).containsExactly(
                Map.entry("x", new Interval(dec(0), false, null, false)));
        assertThat(Constraint.of(new Condition.All(List.of(xAbove0, xBelow10, eIn, eNotA)), FIELDS))
                .containsExactly(Map.entry("x", new Interval(dec(0), false, dec(10), false)),
                        Map.entry("e", new EnumSet(Set.of("b"))));
        assertThat(Constraint.of(new Condition.Any(List.of(xAbove0)), FIELDS)).isNull();
        assertThat(Constraint.of(new Condition.All(List.of(xAbove0, new Condition.Any(List.of(xBelow10)))), FIELDS))
                .isNull();
        assertThat(Constraint.of(compare("x", Operator.NE, number(0)), FIELDS)).isNull();
        assertThat(Constraint.of(compare("flag", Operator.EQ, new BooleanLiteral(true)), FIELDS)).isNull();
    }

    private static Condition compare(String field, Operator op, Operand value) {
        return new Condition.Comparison(field, op, value);
    }

    private static Interval closed(int low, int high) {
        return new Interval(dec(low), true, dec(high), true);
    }

    private static BigDecimal dec(int value) {
        return BigDecimal.valueOf(value);
    }

    private static NumberLiteral number(int value) {
        return new NumberLiteral(dec(value));
    }

    private static StringLiteral text(String value) {
        return new StringLiteral(value);
    }

    private static LiteralList texts(String... values) {
        return new LiteralList(List.of(values).stream().map(v -> (Literal) new StringLiteral(v)).toList());
    }

    private static Field field(String name, FieldType type, List<String> values) {
        return new Field(name, type, null, values, true, null, null, null, null, null, null, null, null);
    }
}
