package com.liorshaya.policypilot.engine;

import com.google.re2j.Pattern;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rule set prepared once for many evaluations (Document 2, Rules Engine Design, Compilation): the rules in
 * evaluation order (priority, then id), the fields by name, and every {@code matches} pattern compiled once on the
 * linear-time engine. Immutable; the API caches one per published version.
 */
public final class CompiledRuleSet {

    private final RuleSet ruleSet;
    private final List<Rule> rules;
    private final Map<String, Field> fields;
    private final Map<String, Pattern> patterns;

    private CompiledRuleSet(RuleSet ruleSet, List<Rule> rules, Map<String, Field> fields, Map<String, Pattern> patterns) {
        this.ruleSet = ruleSet;
        this.rules = rules;
        this.fields = fields;
        this.patterns = patterns;
    }

    /** Compiles a rule set that passed validation without errors. */
    public static CompiledRuleSet compile(RuleSet ruleSet) {
        Map<String, Field> fields = new LinkedHashMap<>();
        ruleSet.fields().forEach(field -> fields.put(field.name(), field));
        Map<String, Pattern> patterns = new HashMap<>();
        ruleSet.rules().forEach(rule -> collectPatterns(rule.condition(), patterns));
        List<Rule> ordered = ruleSet.rules().stream()
                .sorted(Comparator.comparingInt(Rule::priority).thenComparing(Rule::id))
                .toList();
        return new CompiledRuleSet(ruleSet, ordered, Map.copyOf(fields), Map.copyOf(patterns));
    }

    /** The rule set as published. */
    public RuleSet ruleSet() {
        return ruleSet;
    }

    /** Every rule, disabled ones included, in evaluation order. */
    List<Rule> rules() {
        return rules;
    }

    Map<String, Field> fields() {
        return fields;
    }

    /** The compiled form of a pattern the rule set uses. */
    Pattern pattern(String regex) {
        return patterns.get(regex);
    }

    private static void collectPatterns(Condition condition, Map<String, Pattern> patterns) {
        switch (condition) {
            case Condition.Comparison c -> {
                if (c.op() == Operator.MATCHES) {
                    String regex = ((StringLiteral) c.value()).value();
                    patterns.computeIfAbsent(regex, Pattern::compile);
                }
            }
            case Condition.All all -> all.all().forEach(child -> collectPatterns(child, patterns));
            case Condition.Any any -> any.any().forEach(child -> collectPatterns(child, patterns));
            case Condition.Not not -> collectPatterns(not.not(), patterns);
            case Condition.Always always -> { }
        }
    }
}
