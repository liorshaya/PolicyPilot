package com.liorshaya.policypilot.rules.validation;

import static com.liorshaya.policypilot.rules.validation.CaseProblem.Code.CASE_OUT_OF_RANGE;
import static com.liorshaya.policypilot.rules.validation.CaseProblem.Code.CASE_TYPE_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.BooleanLiteral;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.model.StringLiteral;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Case validation (Document 3, Evaluation Semantics, steps 1 and 2). Expected values come from the conformance
 * fixtures and from {@code validate_case} in {@code reference_check.py} run on the same input.
 */
@Requirement("FR-8")
class CaseValidatorTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();
    private static final RuleSet LENDING = MAPPER.toRuleSet(Fixtures.lendingV1());

    /** Fields with every kind of domain, a date, a string and a boolean with a default. */
    private static final RuleSet DOMAINS = MAPPER.toRuleSet(MAPPER.readTree("""
            {"dslVersion": "1.0", "id": "domains", "name": "Domains", "language": "en",
             "fields": [{"name": "x", "type": "number", "minimum": 0, "maximum": 10},
                        {"name": "y", "type": "number", "exclusiveMinimum": 0, "exclusiveMaximum": 10},
                        {"name": "d", "type": "date"},
                        {"name": "s", "type": "string"},
                        {"name": "b", "type": "boolean", "default": true}],
             "defaults": {"outcome": "refer", "reason": "undecided"},
             "rules": [{"id": "R-900", "label": "approve", "priority": 900, "condition": {"always": true},
                        "actions": [{"type": "decide", "outcome": "approve", "reason": "approved"}],
                        "provenance": {"kind": "analyst", "note": "a test rule", "actor": "test"}}]}
            """));

    /** Case 17 of the demo, the base the reference self-test changes one value of. */
    private static final String CASE_17 = """
            {"age": 34, "employment_type": "salaried", "employment_months": 30, "monthly_income": 9500,
             "existing_monthly_debt": 1200, "requested_amount": 60000, "term_months": 48, "credit_events_24m": 1}""";

    private final CaseValidator validator = new CaseValidator();

    @ParameterizedTest(name = "{0} check {1}")
    @MethodSource("caseErrorChecks")
    void caseInvalidConformanceChecksReportTheirProblems(String fixture, int check, RuleSet ruleSet, ObjectNode input,
            JsonNode expectedProblems) {
        CaseValidation result = validator.validate(ruleSet, input);

        assertThat(result.isValid()).isFalse();
        assertThat(result.problems()).hasSameSizeAs(expectedProblems);
        for (int i = 0; i < expectedProblems.size(); i++) {
            JsonNode expected = expectedProblems.get(i);
            CaseProblem actual = result.problems().get(i);
            assertThat(actual.code().name()).isEqualTo(expected.get("code").stringValue());
            assertThat(actual.field()).isEqualTo(expected.get("field").stringValue());
            if (expected.has("value")) {
                assertThat(actual.value().decimalValue()).isEqualByComparingTo(expected.get("value").decimalValue());
            }
        }
    }

    @Test
    void negativeIncomeIsOutOfRange() {
        assertThat(codesAndFields(validator.validate(LENDING, case17("monthly_income", "-9000"))))
                .containsExactly(tuple(CASE_OUT_OF_RANGE, "monthly_income"));
    }

    @Test
    void zeroIncomeIsInsideTheDomain() {
        CaseValidation result = validator.validate(LENDING, case17("monthly_income", "0"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.values()).containsEntry("monthly_income", new NumberLiteral(BigDecimal.ZERO));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            {"x": 0, "y": 5}  | true
            {"x": 10, "y": 5} | true
            {"x": -0.01}      | false
            {"x": 10.01}      | false
            {"y": 0}          | false
            {"y": 10}         | false
            {"y": 0.01}       | true
            {"y": 9.99}       | true
            """)
    void boundValueIsAcceptedByInclusiveAndRejectedByExclusiveBounds(String input, boolean valid) {
        CaseValidation result = validator.validate(DOMAINS, object(input));

        assertThat(result.isValid()).isEqualTo(valid);
        assertThat(result.problems()).allMatch(problem -> problem.code() == CASE_OUT_OF_RANGE);
    }

    /** Document 3, Types: a string is at most 2,000 characters, counted as characters (the reference's len). */
    @Test
    void stringLongerThan2000CharactersIsOutOfRange() {
        String limit = "x".repeat(CaseValidator.MAX_STRING_CHARS);
        String emoji = "\uD83D\uDE00".repeat(CaseValidator.MAX_STRING_CHARS);

        assertThat(validator.validate(DOMAINS, textInput(limit)).values()).containsEntry("s", new StringLiteral(limit));
        assertThat(validator.validate(DOMAINS, textInput(emoji)).isValid()).isTrue();
        assertThat(codesAndFields(validator.validate(DOMAINS, textInput(limit + "x"))))
                .containsExactly(tuple(CASE_OUT_OF_RANGE, "s"));
    }

    @Test
    void integerFieldRejectsAFractionAndAcceptsAWholeNumberWrittenWithOne() {
        assertThat(codesAndFields(validator.validate(LENDING, case17("age", "34.5"))))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "age"));
        assertThat(validator.validate(LENDING, case17("age", "34.0")).isValid()).isTrue();
    }

    @Test
    void booleanIsNotANumber() {
        assertThat(codesAndFields(validator.validate(LENDING, case17("monthly_income", "true"))))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "monthly_income"));
        assertThat(codesAndFields(validator.validate(DOMAINS, object("{\"b\": \"yes\"}"))))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "b"));
        assertThat(codesAndFields(validator.validate(DOMAINS, object("{\"s\": 5}"))))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "s"));
        assertThat(validator.validate(DOMAINS, object("{\"s\": \"text\"}")).values())
                .containsEntry("s", new StringLiteral("text"));
    }

    @Test
    void enumValueOutsideTheDeclaredSetIsATypeMismatch() {
        CaseValidation result = validator.validate(LENDING, case17("employment_type", "\"freelancer\""));

        assertThat(codesAndFields(result))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "employment_type"));
        assertThat(result.problems().getFirst().value().stringValue()).isEqualTo("freelancer");
    }

    @Test
    void dateMustBeAnIsoCalendarDate() {
        assertThat(codesAndFields(validator.validate(DOMAINS, object("{\"d\": \"2026-02-30\"}"))))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "d"));
        assertThat(codesAndFields(validator.validate(DOMAINS, object("{\"d\": \"2026-9-1\"}"))))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "d"));
        assertThat(validator.validate(DOMAINS, object("{\"d\": \"2026-02-28\"}")).values())
                .containsEntry("d", new StringLiteral("2026-02-28"));
    }

    @Test
    void absentOptionalFieldTakesItsDefault() {
        // case 17 omits has_guarantor, which defaults to false; C-11 relies on the same
        assertThat(validator.validate(LENDING, object(CASE_17)).values())
                .containsEntry("has_guarantor", new BooleanLiteral(false));
        assertThat(validator.validate(DOMAINS, object("{}")).values())
                .containsExactlyEntriesOf(Map.of("b", new BooleanLiteral(true)));
    }

    @Test
    void absentOptionalFieldWithoutADefaultStaysAbsent() {
        ObjectNode withoutSeniority = object(CASE_17);
        withoutSeniority.remove("employment_months");

        CaseValidation result = validator.validate(LENDING, withoutSeniority);

        assertThat(result.isValid()).isTrue();
        assertThat(result.values()).doesNotContainKey("employment_months").hasSize(8);
    }

    @Test
    void everyInvalidFieldIsReportedInFieldOrder() {
        ObjectNode input = object(CASE_17);
        input.put("term_months", 0).put("age", "34");

        assertThat(codesAndFields(validator.validate(LENDING, input)))
                .containsExactly(tuple(CASE_TYPE_MISMATCH, "age"), tuple(CASE_OUT_OF_RANGE, "term_months"));
    }

    @Test
    void everyLendingFixtureCaseIsValid() {
        JsonNode cases = Fixtures.json("policies/consumer-lending/cases-200.json").get("cases");

        assertThat(cases).hasSize(200);
        for (JsonNode fixtureCase : cases) {
            assertThat(validator.validate(LENDING, (ObjectNode) fixtureCase.get("input")).problems())
                    .as("case %s", fixtureCase.get("id")).isEmpty();
        }
    }

    static Stream<Arguments> caseErrorChecks() {
        List<Arguments> out = new ArrayList<>();
        for (String name : List.of("C-12", "C-13", "C-14", "C-30")) {
            JsonNode fixture = Fixtures.json("conformance/" + name + ".json");
            RuleSet ruleSet = MAPPER.toRuleSet(Fixtures.ruleSetOf(fixture));
            JsonNode checks = fixture.has("checks") ? fixture.get("checks") : MAPPER.readTree("[" + fixture + "]");
            for (int i = 0; i < checks.size(); i++) {
                JsonNode check = checks.get(i);
                out.add(arguments(name, i, ruleSet, check.get("case"), check.get("expected").get("problems")));
            }
        }
        return out.stream();
    }

    /** The code and field of each problem, in order, for a direct assertion. */
    private static List<Tuple> codesAndFields(CaseValidation result) {
        return result.problems().stream().map(problem -> tuple(problem.code(), problem.field())).toList();
    }

    private static ObjectNode case17(String field, String json) {
        ObjectNode input = object(CASE_17);
        input.set(field, MAPPER.readTree(json));
        return input;
    }

    private static ObjectNode textInput(String text) {
        ObjectNode input = object("{}");
        input.put("s", text);
        return input;
    }

    private static ObjectNode object(String json) {
        return (ObjectNode) MAPPER.readTree(json);
    }
}
