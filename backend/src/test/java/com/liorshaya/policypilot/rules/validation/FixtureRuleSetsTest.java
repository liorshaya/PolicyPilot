package com.liorshaya.policypilot.rules.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Fixtures;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/** The committed rule sets validate in Java as they do in the reference. */
class FixtureRuleSetsTest {

    /** Document 7, day 2: the lending rule set validates clean in PUBLISH; the reference asserts no finding at all. */
    @Test
    void lendingV1ValidatesCleanInPublish() {
        assertThat(Validations.publish(Fixtures.lendingV1())).isEmpty();
    }

    /** The reference's run_conformance: each C file's rule set has no error in its context (ANALYST_EDIT by default). */
    @ParameterizedTest(name = "{0}")
    @MethodSource("conformanceCases")
    void everyConformanceRuleSetValidatesWithoutErrors(Path file) {
        JsonNode fixture = Fixtures.json(file);
        ValidationContext context = ValidationContext.valueOf(fixture.path("context").asString("ANALYST_EDIT"));

        ValidationResult result = Validations.VALIDATOR.validate(Fixtures.ruleSetOf(fixture), context,
                Validations.paragraphs(fixture.get("policyText")), Set.of());

        assertThat(result.hasErrors()).isFalse();
    }

    /** The reference's evaluation-set admission: each expected.ruleset.json has no error in PUBLISH. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("evaluationPolicies")
    void everyEvaluationExpectedRuleSetValidatesWithoutErrorsInPublish(String slug) {
        ValidationResult result = Validations.VALIDATOR.validate(
                Fixtures.json("eval/policies/" + slug + "/expected.ruleset.json"), ValidationContext.PUBLISH,
                Fixtures.paragraphs(Fixtures.evaluationPolicyText(slug)), Set.of());

        assertThat(result.findings()).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    static Stream<Path> conformanceCases() {
        return Fixtures.conformanceCases().stream();
    }

    static Stream<String> evaluationPolicies() {
        return Fixtures.evaluationPolicies().stream();
    }
}
