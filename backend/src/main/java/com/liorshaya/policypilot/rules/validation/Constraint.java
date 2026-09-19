package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.Literal;
import com.liorshaya.policypilot.rules.model.LiteralList;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The set of values one field may take under a simple condition, for the two structural checks that must be
 * certain (Document 3, "structural checks are deliberately conservative"): a numeric interval or a set of enum
 * values. Only a single comparison, or an {@code all} of single comparisons with literal operands, has one.
 */
sealed interface Constraint {

    /** Whether every value this constraint admits is admitted by the other, on the same field. */
    boolean isSubsetOf(Constraint other);

    /** Per field, the values the condition admits; {@code null} when the condition is not that simple. */
    static @Nullable Map<String, Constraint> of(Condition condition, Map<String, Field> fields) {
        List<Condition> leaves = switch (condition) {
            case Condition.Comparison comparison -> List.of(comparison);
            case Condition.All all -> all.all();
            default -> null;
        };
        if (leaves == null) {
            return null;
        }
        Map<String, Constraint> out = new LinkedHashMap<>();
        for (Condition leaf : leaves) {
            Constraint constraint = leaf instanceof Condition.Comparison comparison
                    ? of(comparison, fields.get(comparison.field()))
                    : null;
            if (constraint == null) {
                return null;
            }
            String field = ((Condition.Comparison) leaf).field();
            out.merge(field, constraint, Constraint::intersect);
        }
        return out;
    }

    private static @Nullable Constraint of(Condition.Comparison comparison, Field field) {
        if (field.type().isNumeric()) {
            return Interval.of(comparison.op(), comparison.value());
        }
        return field.values() == null ? null : EnumSet.of(comparison.op(), comparison.value(), field.values());
    }

    private static Constraint intersect(Constraint a, Constraint b) {
        return a instanceof Interval i ? i.intersect((Interval) b) : ((EnumSet) a).intersect((EnumSet) b);
    }

    /** A numeric interval; a {@code null} bound is unbounded. */
    record Interval(@Nullable BigDecimal low, boolean lowIncluded, @Nullable BigDecimal high, boolean highIncluded)
            implements Constraint {

        /** The semantic layer has checked that a numeric field's between bounds are numbers. */
        static @Nullable Interval of(Operator op, @Nullable Operand value) {
            if (op == Operator.BETWEEN) {
                List<Literal> bounds = ((LiteralList) value).values();
                return new Interval(((NumberLiteral) bounds.get(0)).value(), true,
                        ((NumberLiteral) bounds.get(1)).value(), true);
            }
            if (!(value instanceof NumberLiteral number)) {
                return null;
            }
            BigDecimal v = number.value();
            return switch (op) {
                case EQ -> new Interval(v, true, v, true);
                case LT -> new Interval(null, false, v, false);
                case LTE -> new Interval(null, false, v, true);
                case GT -> new Interval(v, false, null, false);
                case GTE -> new Interval(v, true, null, false);
                default -> null;
            };
        }

        Interval intersect(Interval other) {
            BigDecimal lo = low;
            boolean loIn = lowIncluded;
            if (other.low != null && (lo == null || other.low.compareTo(lo) > 0
                    || other.low.compareTo(lo) == 0 && !other.lowIncluded)) {
                lo = other.low;
                loIn = other.lowIncluded;
            }
            BigDecimal hi = high;
            boolean hiIn = highIncluded;
            if (other.high != null && (hi == null || other.high.compareTo(hi) < 0
                    || other.high.compareTo(hi) == 0 && !other.highIncluded)) {
                hi = other.high;
                hiIn = other.highIncluded;
            }
            return new Interval(lo, loIn, hi, hiIn);
        }

        boolean isEmpty() {
            return low != null && high != null
                    && (low.compareTo(high) > 0 || low.compareTo(high) == 0 && !(lowIncluded && highIncluded));
        }

        @Override
        public boolean isSubsetOf(Constraint other) {
            Interval o = (Interval) other;
            if (o.low != null && (low == null || low.compareTo(o.low) < 0
                    || low.compareTo(o.low) == 0 && lowIncluded && !o.lowIncluded)) {
                return false;
            }
            return o.high == null || high != null && (high.compareTo(o.high) < 0
                    || high.compareTo(o.high) == 0 && !(highIncluded && !o.highIncluded));
        }

        /** Whether some value lies in both intervals. */
        boolean overlaps(Interval other) {
            return !intersect(other).isEmpty();
        }
    }

    /** The enum values a condition admits. */
    record EnumSet(Set<String> allowed) implements Constraint {

        static @Nullable EnumSet of(Operator op, @Nullable Operand value, List<String> values) {
            Set<String> declared = Set.copyOf(values);
            if ((op == Operator.EQ || op == Operator.NE) && !(value instanceof StringLiteral)) {
                return null;
            }
            return switch (op) {
                case EQ -> new EnumSet(retain(declared, Set.of(((StringLiteral) value).value())));
                case NE -> new EnumSet(remove(declared, Set.of(((StringLiteral) value).value())));
                case IN -> new EnumSet(retain(declared, strings((LiteralList) value)));
                case NOT_IN -> new EnumSet(remove(declared, strings((LiteralList) value)));
                default -> null;
            };
        }

        EnumSet intersect(EnumSet other) {
            return new EnumSet(retain(allowed, other.allowed));
        }

        @Override
        public boolean isSubsetOf(Constraint other) {
            return ((EnumSet) other).allowed.containsAll(allowed);
        }

        private static Set<String> strings(LiteralList list) {
            Set<String> out = new HashSet<>();
            list.values().forEach(value -> out.add(((StringLiteral) value).value()));
            return out;
        }

        private static Set<String> retain(Set<String> a, Set<String> b) {
            Set<String> out = new HashSet<>(a);
            out.retainAll(b);
            return out;
        }

        private static Set<String> remove(Set<String> a, Set<String> b) {
            Set<String> out = new HashSet<>(a);
            out.removeAll(b);
            return out;
        }
    }
}
