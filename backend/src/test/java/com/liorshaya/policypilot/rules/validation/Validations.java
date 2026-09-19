package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.support.Fixtures;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

/** Runs the validator the way the reference does on the committed fixtures (fixtures/README.md, file shapes). */
final class Validations {

    static final RuleSetValidator VALIDATOR = new RuleSetValidator();

    private Validations() {}

    /** The findings for {@code fixtures/conformance/invalid-<CODE>.json}, run in its own context. */
    static List<Finding> invalidFixture(String code) {
        JsonNode fixture = Fixtures.json("conformance/invalid-" + code + ".json");
        JsonNode modelRuleIds = fixture.path("modelRuleIds");
        return VALIDATOR.validate(Fixtures.ruleSetOf(fixture),
                ValidationContext.valueOf(fixture.get("context").stringValue()),
                paragraphs(fixture.get("policyText")),
                StreamSupport.stream(modelRuleIds.spliterator(), false)
                        .map(JsonNode::stringValue)
                        .collect(Collectors.toSet()))
                .findings();
    }

    /** The findings for a variant of the lending rule set, against the lending policy's paragraphs. */
    static List<Finding> lending(JsonNode document, ValidationContext context, String... modelRuleIds) {
        return VALIDATOR.validate(document, context, Fixtures.lendingParagraphs(), Set.of(modelRuleIds)).findings();
    }

    /** The findings for a variant of the lending rule set submitted for publishing. */
    static List<Finding> publish(JsonNode document) {
        return lending(document, ValidationContext.PUBLISH);
    }

    /** A fixture's {@code policyText}: absent, a path relative to the fixtures root, or an inline list. */
    static List<String> paragraphs(JsonNode policyText) {
        if (policyText == null) {
            return List.of();
        }
        if (policyText.isString()) {
            return Fixtures.paragraphs(policyText.stringValue());
        }
        return StreamSupport.stream(policyText.spliterator(), false).map(JsonNode::stringValue).toList();
    }
}
