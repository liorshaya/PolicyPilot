package com.liorshaya.policypilot.rules.model;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Which fields a rule reads and which it writes (Document 3, Actions and Rules): what the structural checks order the
 * derived fields by, and what the candidates of a change request are closed over (Document 4, Prompt 5). Every set
 * is in name order, so a walk over one is the same on every run.
 */
public final class FieldReferences {

    private FieldReferences() {}

    /** The fields a condition compares, with the fields its operand expressions read. */
    public static SortedSet<String> inCondition(Condition condition) {
        SortedSet<String> fields = new TreeSet<>();
        collect(condition, fields);
        return Collections.unmodifiableSortedSet(fields);
    }

    /** The fields a value reads: the one it refers to, or those of its expression; none for a literal. */
    public static SortedSet<String> inValue(Value value) {
        SortedSet<String> fields = new TreeSet<>();
        collect(value, fields);
        return Collections.unmodifiableSortedSet(fields);
    }

    /** Every field a rule reads: those its condition compares and those the values of its set actions read. */
    public static SortedSet<String> readBy(Rule rule) {
        SortedSet<String> fields = new TreeSet<>();
        collect(rule.condition(), fields);
        for (Action action : rule.actions()) {
            if (action instanceof Action.SetField set) {
                collect(set.value(), fields);
            }
        }
        return Collections.unmodifiableSortedSet(fields);
    }

    /** The derived fields a rule's set actions write. */
    public static SortedSet<String> setBy(Rule rule) {
        SortedSet<String> fields = new TreeSet<>();
        for (Action action : rule.actions()) {
            if (action instanceof Action.SetField set) {
                fields.add(set.field());
            }
        }
        return Collections.unmodifiableSortedSet(fields);
    }

    private static void collect(Condition condition, SortedSet<String> out) {
        switch (condition) {
            case Condition.Comparison comparison -> {
                out.add(comparison.field());
                if (comparison.value() instanceof Value value) {
                    collect(value, out);
                }
            }
            case Condition.All all -> all.all().forEach(child -> collect(child, out));
            case Condition.Any any -> any.any().forEach(child -> collect(child, out));
            case Condition.Not not -> collect(not.not(), out);
            case Condition.Always always -> { }
        }
    }

    private static void collect(Value value, SortedSet<String> out) {
        switch (value) {
            case FieldRef ref -> out.add(ref.field());
            case Call call -> call.args().forEach(arg -> collect(arg, out));
            case Literal literal -> { }
        }
    }

    private static void collect(Expression expression, SortedSet<String> out) {
        switch (expression) {
            case FieldRef ref -> out.add(ref.field());
            case Call call -> call.args().forEach(arg -> collect(arg, out));
            case NumberLiteral number -> { }
        }
    }
}
