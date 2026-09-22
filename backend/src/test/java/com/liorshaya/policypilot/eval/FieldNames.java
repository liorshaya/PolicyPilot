package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.rules.model.Call;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.Operand;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Every field a condition reads, its own and the ones inside a comparison's expression. The evaluation runner
 * uses it for one thing: to say, of a rule that did not match, whether the reason is arithmetic or a field the
 * expected rule set never declared --- the distinction the {@code author/v2} decision turns on, since the author
 * prompt asks the model to invent the field names and the labeled cases use the expert's.
 */
final class FieldNames {

    private FieldNames() {}

    static Set<String> of(Condition condition) {
        Set<String> names = new LinkedHashSet<>();
        collect(condition, names);
        return names;
    }

    private static void collect(Condition condition, Set<String> names) {
        switch (condition) {
            case Condition.Always ignored -> { }
            case Condition.Not not -> collect(not.not(), names);
            case Condition.All all -> all.all().forEach(branch -> collect(branch, names));
            case Condition.Any any -> any.any().forEach(branch -> collect(branch, names));
            case Condition.Comparison comparison -> {
                names.add(comparison.field());
                if (comparison.value() != null) {
                    collect(comparison.value(), names);
                }
            }
        }
    }

    private static void collect(Operand operand, Set<String> names) {
        switch (operand) {
            case FieldRef ref -> names.add(ref.field());
            case Call call -> call.args().forEach(argument -> collect((Operand) argument, names));
            default -> { }
        }
    }

    /** Every field a value reads: the right-hand side of a comparison, or what a {@code set} action writes. */
    static Set<String> ofValue(Operand operand) {
        Set<String> names = new LinkedHashSet<>();
        collect(operand, names);
        return names;
    }
}
