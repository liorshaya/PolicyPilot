package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.model.Action;
import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.FieldReferences;
import com.liorshaya.policypilot.rules.model.Function;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operand;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.Outcome;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Layer 3 (Document 3, Static Validation): derived-field order and cycles, unused and never-set fields, and the
 * conservative reachability, overlap, precedence and band checks, run on a rule set with no semantic errors. The
 * checks, their order and their reach follow {@code structural_layer} in {@code fixtures/reference/reference_check.py};
 * disabled rules take no part.
 */
final class StructuralChecks {

    private static final String PRECEDENCE_INTENDED = "precedence_intended";

    List<Finding> check(RuleSet ruleSet) {
        return new Run(ruleSet).check();
    }

    /** Document 3, recommended priority bands: the action a rule in the band is expected to take. */
    private enum Band {
        SET(1, 99),
        REJECT(100, 299),
        REFER(300, 399),
        FLAG(400, 499),
        APPROVE(900, 999);

        private final int from;
        private final int to;

        Band(int from, int to) {
            this.from = from;
            this.to = to;
        }

        static @Nullable Band of(int priority) {
            for (Band band : values()) {
                if (priority >= band.from && priority <= band.to) {
                    return band;
                }
            }
            return null;
        }
    }

    /** An enabled rule with its position in the document, for pointers. */
    private record Placed(Rule rule, int index) {
        String path() {
            return "/rules/" + index;
        }

        String id() {
            return rule.id();
        }

        int priority() {
            return rule.priority();
        }
    }

    private static final class Run {

        private final RuleSet ruleSet;
        private final Map<String, Field> fields = new HashMap<>();
        private final List<Placed> rules = new ArrayList<>();
        private final Map<String, Integer> firstSetAt = new HashMap<>();
        private final Map<String, String> firstSetPath = new HashMap<>();
        private final Map<String, Set<String>> dependsOn = new TreeMap<>();
        private final Set<String> used = new HashSet<>();
        private final List<Finding> findings = new ArrayList<>();

        Run(RuleSet ruleSet) {
            this.ruleSet = ruleSet;
            ruleSet.fields().forEach(field -> fields.put(field.name(), field));
            for (int i = 0; i < ruleSet.rules().size(); i++) {
                if (ruleSet.rules().get(i).isEnabled()) {
                    rules.add(new Placed(ruleSet.rules().get(i), i));
                }
            }
            rules.sort(Comparator.comparingInt(Placed::priority).thenComparing(Placed::id));
        }

        List<Finding> check() {
            derivations();
            cycles();
            rules.forEach(this::perRule);
            unusedFields();
            unreachable();
            overlaps();
            referBeforeReject();
            candidates();
            if (rules.stream().noneMatch(r -> decides(r.rule(), Outcome.APPROVE, null))) {
                report(ValidationCode.NO_TERMINAL_APPROVE, "/rules", "no rule can produce approve", List.of(),
                        List.of());
            }
            return findings;
        }

        // ------------------------------------------------------------------ derived fields

        private void derivations() {
            for (Placed placed : rules) {
                List<Action> actions = placed.rule().actions();
                for (int j = 0; j < actions.size(); j++) {
                    if (actions.get(j) instanceof Action.SetField set) {
                        firstSetAt.putIfAbsent(set.field(), placed.priority());
                        firstSetPath.putIfAbsent(set.field(), placed.path() + "/actions/" + j);
                        used.add(set.field());
                        Set<String> reads = new TreeSet<>();
                        reads.addAll(FieldReferences.inValue(set.value()));
                        // a rule that reads the field it sets (x = max(x, 5)) is a read-modify-write, not a cycle
                        reads.removeIf(name -> !fields.get(name).isDerived() || name.equals(set.field()));
                        dependsOn.computeIfAbsent(set.field(), key -> new TreeSet<>()).addAll(reads);
                    }
                }
            }
        }

        private void cycles() {
            for (String name : dependsOn.keySet()) {
                if (reachesItself(name)) {
                    report(ValidationCode.DERIVED_CYCLE, firstSetPath.get(name), name + " depends on itself",
                            List.of(), List.of(name));
                }
            }
        }

        private boolean reachesItself(String start) {
            Set<String> seen = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>(List.of(start));
            while (!stack.isEmpty()) {
                for (String next : dependsOn.getOrDefault(stack.pop(), Set.of())) {
                    if (next.equals(start)) {
                        return true;
                    }
                    if (seen.add(next)) {
                        stack.push(next);
                    }
                }
            }
            return false;
        }

        // ------------------------------------------------------------------ per rule

        private void perRule(Placed placed) {
            Rule rule = placed.rule();
            Set<String> reads = new TreeSet<>();
            reads.addAll(FieldReferences.inCondition(rule.condition()));
            Map<String, String> divisors = new TreeMap<>();
            List<Action> actions = rule.actions();
            for (int j = 0; j < actions.size(); j++) {
                if (actions.get(j) instanceof Action.SetField set) {
                    reads.addAll(FieldReferences.inValue(set.value()));
                    divisors(set.value(), placed.path() + "/actions/" + j + "/value", divisors);
                }
            }
            used.addAll(reads);
            for (String name : reads) {
                Integer setAt = firstSetAt.get(name);
                if (fields.get(name).isDerived() && setAt != null && setAt >= rule.priority()) {
                    report(ValidationCode.DERIVED_ORDER, placed.path(), rule.id() + " reads " + name
                            + ", first set at priority " + setAt, List.of(rule.id()), List.of(name));
                }
            }
            divisors.forEach((name, path) -> {
                Field divisor = fields.get(name);
                boolean safeDomain = divisor.exclusiveMinimum() != null && divisor.exclusiveMinimum().signum() >= 0
                        || divisor.minimum() != null && divisor.minimum().signum() > 0;
                if (!safeDomain && !excludesZero(rule.condition(), name)) {
                    report(ValidationCode.DIVISION_BY_UNGUARDED_FIELD, path, rule.id() + " divides by " + name
                            + ", which may be 0 and is not excluded by the rule's condition",
                            List.of(rule.id()), List.of(name));
                }
            });
            Set<String> guarded = new HashSet<>();
            presentGuards(rule.condition(), guarded);
            Map<String, String> unguarded = new TreeMap<>();
            underNot(rule.condition(), placed.path() + "/condition", false, guarded, unguarded);
            unguarded.forEach((name, path) -> report(ValidationCode.MISSING_FIELD_UNDER_NOT, path,
                    rule.id() + " reads " + name + " under not without a present guard", List.of(rule.id()),
                    List.of(name)));
            Band band = Band.of(rule.priority());
            if (band != null && !fitsBand(rule, band)) {
                report(ValidationCode.PRIORITY_BAND_UNUSUAL, placed.path() + "/priority", rule.id() + " at "
                        + rule.priority() + " is in the " + band.name().toLowerCase(Locale.ROOT)
                        + " band", List.of(rule.id()), List.of());
            }
        }

        private static boolean fitsBand(Rule rule, Band band) {
            return switch (band) {
                case SET -> rule.actions().stream().anyMatch(Action.SetField.class::isInstance);
                case FLAG -> rule.actions().stream().anyMatch(Action.Flag.class::isInstance);
                case REJECT -> decides(rule, Outcome.REJECT, null);
                case REFER -> decides(rule, Outcome.REFER, null);
                case APPROVE -> decides(rule, Outcome.APPROVE, null);
            };
        }

        /** The condition, or a leaf of its top-level {@code all}, requires the field to be above zero. */
        private static boolean excludesZero(Condition condition, String field) {
            List<Condition> leaves = switch (condition) {
                case Condition.Comparison comparison -> List.of(comparison);
                case Condition.All all -> all.all();
                default -> List.of();
            };
            for (Condition leaf : leaves) {
                if (leaf instanceof Condition.Comparison c && c.field().equals(field)
                        && c.value() instanceof NumberLiteral number
                        && (c.op() == Operator.GT && number.value().signum() >= 0
                            || c.op() == Operator.GTE && number.value().signum() > 0)) {
                    return true;
                }
            }
            return false;
        }

        private void underNot(Condition condition, String path, boolean inside, Set<String> guarded,
                Map<String, String> unguarded) {
            switch (condition) {
                case Condition.Comparison c -> {
                    Field field = fields.get(c.field());
                    if (inside && c.op() != Operator.PRESENT && c.op() != Operator.ABSENT && !field.isDerived()
                            && !field.isRequired() && field.defaultValue() == null && !guarded.contains(c.field())) {
                        unguarded.putIfAbsent(c.field(), path);
                    }
                }
                case Condition.All all -> {
                    for (int k = 0; k < all.all().size(); k++) {
                        underNot(all.all().get(k), path + "/all/" + k, inside, guarded, unguarded);
                    }
                }
                case Condition.Any any -> {
                    for (int k = 0; k < any.any().size(); k++) {
                        underNot(any.any().get(k), path + "/any/" + k, inside, guarded, unguarded);
                    }
                }
                case Condition.Not not -> underNot(not.not(), path + "/not", true, guarded, unguarded);
                case Condition.Always always -> { }
            }
        }

        private static void presentGuards(Condition condition, Set<String> guarded) {
            switch (condition) {
                case Condition.Comparison c -> {
                    if (c.op() == Operator.PRESENT) {
                        guarded.add(c.field());
                    }
                }
                case Condition.All all -> all.all().forEach(child -> presentGuards(child, guarded));
                case Condition.Any any -> any.any().forEach(child -> presentGuards(child, guarded));
                case Condition.Not not -> presentGuards(not.not(), guarded);
                case Condition.Always always -> { }
            }
        }

        // ------------------------------------------------------------------ fields

        private void unusedFields() {
            for (int i = 0; i < ruleSet.fields().size(); i++) {
                Field field = ruleSet.fields().get(i);
                if (field.isDerived() && !firstSetAt.containsKey(field.name())) {
                    report(ValidationCode.DERIVED_NEVER_SET, "/fields/" + i, field.name() + " is never set",
                            List.of(), List.of(field.name()));
                } else if (!field.isDerived() && !used.contains(field.name())) {
                    report(ValidationCode.FIELD_UNUSED, "/fields/" + i, field.name() + " is not read by any rule",
                            List.of(), List.of(field.name()));
                }
            }
        }

        // ------------------------------------------------------------------ outcomes

        private void unreachable() {
            List<Placed> terminals = rules.stream().filter(r -> decides(r.rule(), null, true)).toList();
            for (int j = 0; j < terminals.size(); j++) {
                Placed later = terminals.get(j);
                Map<String, Constraint> laterRange = constraints(later);
                for (Placed earlier : terminals.subList(0, j)) {
                    Map<String, Constraint> earlierRange = constraints(earlier);
                    boolean subsumed = earlier.rule().condition() instanceof Condition.Always
                            || earlierRange != null && laterRange != null && earlierRange.entrySet().stream()
                                    .allMatch(e -> laterRange.containsKey(e.getKey())
                                            && laterRange.get(e.getKey()).isSubsetOf(e.getValue()));
                    if (subsumed) {
                        report(ValidationCode.RULE_UNREACHABLE, later.path(), later.id() + " is subsumed by "
                                + earlier.id(), List.of(later.id(), earlier.id()), List.of());
                        break;
                    }
                }
            }
        }

        private void overlaps() {
            List<Placed> deciders = rules.stream().filter(r -> decides(r.rule(), null, null)).toList();
            for (int j = 0; j < deciders.size(); j++) {
                Placed b = deciders.get(j);
                Map<String, Constraint> rangeB = constraints(b);
                for (Placed a : deciders.subList(0, j)) {
                    Map<String, Constraint> rangeA = constraints(a);
                    if (!decides(a.rule(), null, true) && singleField(rangeA) && singleField(rangeB)) {
                        overlap(a, rangeA, b, rangeB);
                    }
                }
            }
        }

        /** A candidate that may be overridden, and a later rule, on overlapping ranges of one field. */
        private void overlap(Placed a, Map<String, Constraint> rangeA, Placed b, Map<String, Constraint> rangeB) {
            String field = rangeA.keySet().iterator().next();
            Outcome outcomeA = firstOutcome(a.rule());
            Outcome outcomeB = firstOutcome(b.rule());
            if (rangeB.containsKey(field) && rangeA.get(field) instanceof Constraint.Interval ia
                    && outcomeA != outcomeB && ia.overlaps((Constraint.Interval) rangeB.get(field))) {
                report(ValidationCode.RULE_OVERLAP_CONFLICT, b.path(), a.id() + " (" + outcomeA.json()
                        + ") and " + b.id() + " (" + outcomeB.json() + ") overlap on " + field,
                        List.of(a.id(), b.id()), List.of(field));
            }
        }

        private static boolean singleField(@Nullable Map<String, Constraint> range) {
            return range != null && range.size() == 1;
        }

        private void referBeforeReject() {
            int lastReject = rules.stream().filter(r -> decides(r.rule(), Outcome.REJECT, true))
                    .mapToInt(Placed::priority).max().orElse(Integer.MIN_VALUE);
            for (Placed placed : rules) {
                Rule rule = placed.rule();
                boolean intended = rule.tags() != null && rule.tags().contains(PRECEDENCE_INTENDED);
                if (decides(rule, Outcome.REFER, true) && rule.priority() < lastReject && !intended) {
                    report(ValidationCode.REFER_PRECEDES_REJECT, placed.path() + "/priority", "terminal refer "
                            + rule.id() + " at " + rule.priority() + " precedes a terminal reject at " + lastReject,
                            List.of(rule.id()), List.of());
                }
            }
        }

        private void candidates() {
            int lastAlwaysTerminal = rules.stream()
                    .filter(r -> r.rule().condition() instanceof Condition.Always && decides(r.rule(), null, true))
                    .mapToInt(Placed::priority).max().orElse(Integer.MIN_VALUE);
            for (Placed placed : rules) {
                if (decides(placed.rule(), null, false) && lastAlwaysTerminal > placed.priority()) {
                    report(ValidationCode.CANDIDATE_NEVER_WINS, placed.path(), placed.id()
                            + " is a candidate that a later always-terminal rule overrides", List.of(placed.id()),
                            List.of());
                }
            }
        }

        private @Nullable Map<String, Constraint> constraints(Placed placed) {
            return Constraint.of(placed.rule().condition(), fields);
        }

        // ------------------------------------------------------------------ helpers

        private static boolean decides(Rule rule, @Nullable Outcome outcome, @Nullable Boolean terminal) {
            return rule.actions().stream().anyMatch(action -> action instanceof Action.Decide decide
                    && (outcome == null || decide.outcome() == outcome)
                    && (terminal == null || decide.isTerminal() == terminal));
        }

        private static Outcome firstOutcome(Rule rule) {
            return rule.actions().stream().filter(Action.Decide.class::isInstance).map(Action.Decide.class::cast)
                    .findFirst().orElseThrow().outcome();
        }

        private static void divisors(Operand operand, String path, Map<String, String> out) {
            if (operand instanceof Call call) {
                divisors(call, path, out);
            }
        }

        private static void divisors(Call call, String path, Map<String, String> out) {
            if (call.fn() == Function.DIV && call.args().get(1) instanceof FieldRef divisor) {
                out.putIfAbsent(divisor.field(), path);
            }
            for (int k = 0; k < call.args().size(); k++) {
                if (call.args().get(k) instanceof Call inner) {
                    divisors(inner, path + "/args/" + k, out);
                }
            }
        }

        private void report(ValidationCode code, String path, String message, List<String> ruleIds,
                List<String> fieldNames) {
            findings.add(new Finding(code, path, message, ruleIds, fieldNames));
        }
    }
}
