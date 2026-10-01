package com.liorshaya.policypilot.decision.service;

import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Whether a fixture set's cases are cases of a version (Document 2, decide, 2026-10-01): every case input the version
 * declares, a field that is not derived, is supplied by at least one case of the set. The 200 lending cases pass case
 * validation on any version whose fields are all optional, and there every comparison reads an absent field as false
 * (Document 3, Missing values), so on another policy's version they decided 200 approvals that meant nothing.
 */
final class FixtureFit {

    private FixtureFit() {}

    /** The version's case inputs no case of the set supplies, in the order the version declares them. */
    static List<String> missingInputs(RuleSet ruleSet, Collection<? extends JsonNode> inputs) {
        Set<String> supplied = new HashSet<>();
        inputs.forEach(input -> supplied.addAll(input.propertyNames()));
        List<String> declared = ruleSet.fields().stream()
                .filter(field -> !field.isDerived())
                .map(Field::name)
                .toList();
        return missing(declared, supplied);
    }

    /**
     * The case inputs a rule set document declares, read from its {@code fields} as they stand: the web layer answers
     * with any stored version, a draft included, and asks only for names, so nothing else of the document is mapped.
     */
    static List<String> inputsOf(JsonNode document) {
        return document.path("fields").valueStream()
                .filter(field -> !field.path("derived").asBoolean(false))
                .map(field -> field.path("name").asString())
                .toList();
    }

    /** The inputs of {@code declared} that {@code supplied} lacks, in the order declared. */
    static List<String> missing(List<String> declared, Set<String> supplied) {
        return declared.stream().filter(name -> !supplied.contains(name)).toList();
    }
}
