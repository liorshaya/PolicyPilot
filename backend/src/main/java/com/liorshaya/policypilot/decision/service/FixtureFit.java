package com.liorshaya.policypilot.decision.service;

import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Whether a fixture set's cases are cases of a version (Document 2, decide, 2026-10-01): some case of the set supplies
 * every required case input of the version, a field that is not derived, and every case input when the version requires
 * none, since nothing then marks what a case must carry. The 200 lending cases pass case validation on any version
 * whose fields are all optional, and there every comparison reads an absent field as false (Document 3, Missing
 * values), so on another policy's version they decided 200 approvals that meant nothing. An optional field a change
 * adds to the lending rule set (Document 3, add_field) leaves them fitting.
 */
final class FixtureFit {

    private FixtureFit() {}

    /** A case input of a version: a field that is not derived, and whether a case must supply it. */
    record Input(String name, boolean required) {}

    /** The version's case inputs the set must supply and none of its cases does, in the order the version declares. */
    static List<String> missingInputs(RuleSet ruleSet, Collection<? extends JsonNode> inputs) {
        Set<String> supplied = new HashSet<>();
        inputs.forEach(input -> supplied.addAll(input.propertyNames()));
        return missing(inputsOf(ruleSet), supplied);
    }

    /** The case inputs of a mapped rule set, in declaration order. */
    static List<Input> inputsOf(RuleSet ruleSet) {
        return ruleSet.fields().stream()
                .filter(field -> !field.isDerived())
                .map(field -> new Input(field.name(), field.isRequired()))
                .toList();
    }

    /**
     * The case inputs of a rule set document, read from its {@code fields} as they stand: the web layer answers with
     * any stored version, a draft included, and asks only for these, so nothing else of the document is mapped.
     */
    static List<Input> inputsOf(JsonNode document) {
        return document.path("fields").valueStream()
                .filter(field -> !field.path("derived").asBoolean(false))
                .map(field -> new Input(field.path("name").asString(), field.path("required").asBoolean(false)))
                .toList();
    }

    /**
     * The inputs of {@code declared} the set must supply and {@code supplied} lacks, in the order declared: the
     * required ones, or all of them when none is required.
     */
    static List<String> missing(List<Input> declared, Set<String> supplied) {
        boolean anyRequired = declared.stream().anyMatch(Input::required);
        return declared.stream()
                .filter(input -> !anyRequired || input.required())
                .map(Input::name)
                .filter(name -> !supplied.contains(name))
                .toList();
    }
}
