package com.liorshaya.policypilot.rules.patch;

import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.FieldReferences;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules a change can affect (Document 4, Prompt 5, Candidate selection): the seeds, which are the rules closest to
 * the request and the rules it names; every rule that reads a field the seeds' conditions test or the request names;
 * and every rule that reads a field one of those derives, until no rule is added. A threshold change thus arrives
 * with its advisory rule and with the rules downstream of its derivations.
 *
 * @param ruleIds the candidates, in evaluation order: priority, then id
 * @param fields the request's fields, in the rule set's order: those the seeds' conditions test and those it names
 */
public record Impact(List<String> ruleIds, List<String> fields) {

    public Impact {
        ruleIds = List.copyOf(ruleIds);
        fields = List.copyOf(fields);
    }

    /**
     * @param seeds rule ids; one the rule set does not have is ignored
     * @param namedFields field names the request writes; one the rule set does not declare is ignored
     */
    public static Impact of(RuleSet ruleSet, Collection<String> seeds, Collection<String> namedFields) {
        Set<String> candidates = new HashSet<>();
        Set<String> requestFields = new HashSet<>(namedFields);
        for (Rule rule : ruleSet.rules()) {
            if (seeds.contains(rule.id())) {
                candidates.add(rule.id());
                requestFields.addAll(FieldReferences.inCondition(rule.condition()));
            }
        }
        // one pass over the rules in document order, and another whenever a candidate derives a field not yet read: a
        // rule that reads it may be written before the rule that derives it
        Set<String> read = new HashSet<>(requestFields);
        boolean newlyDerived = true;
        while (newlyDerived) {
            newlyDerived = false;
            for (Rule rule : ruleSet.rules()) {
                if (FieldReferences.readBy(rule).stream().anyMatch(read::contains)) {
                    candidates.add(rule.id());
                }
                if (candidates.contains(rule.id()) && read.addAll(FieldReferences.setBy(rule))) {
                    newlyDerived = true;
                }
            }
        }
        return new Impact(
                ruleSet.rules().stream().filter(rule -> candidates.contains(rule.id()))
                        .sorted(Comparator.comparingInt(Rule::priority).thenComparing(Rule::id)).map(Rule::id)
                        .toList(),
                ruleSet.fields().stream().map(Field::name).filter(requestFields::contains).toList());
    }
}
