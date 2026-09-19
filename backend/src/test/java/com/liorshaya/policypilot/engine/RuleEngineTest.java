package com.liorshaya.policypilot.engine;

import static com.liorshaya.policypilot.engine.EngineFixtures.CASE_17;
import static com.liorshaya.policypilot.engine.EngineFixtures.ENGINE;
import static com.liorshaya.policypilot.engine.EngineFixtures.LENDING;
import static com.liorshaya.policypilot.engine.EngineFixtures.checks;
import static com.liorshaya.policypilot.engine.EngineFixtures.compile;
import static com.liorshaya.policypilot.engine.EngineFixtures.decideJson;
import static com.liorshaya.policypilot.engine.EngineFixtures.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.liorshaya.policypilot.engine.EngineFixtures.Check;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.validation.CaseProblem;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.JsonSubset;
import com.liorshaya.policypilot.support.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The evaluation algorithm (Document 3, Evaluation Semantics steps 1 to 8; Document 2, Rules Engine Design). The
 * conformance fixtures give most expected values; where they are silent, the small rule sets below were run through
 * {@code reference_check.py} and its results are the expected values.
 */
@Requirement({"FR-8", "NFR-1", "NFR-2"})
class RuleEngineTest {

    @Test
    void invalidCaseIsACaseErrorAndNothingIsEvaluated() {
        List<Check> cases = new ArrayList<>();
        for (String name : List.of("C-12", "C-13", "C-14", "C-30")) {
            cases.addAll(checks(name));
        }

        assertThat(cases).hasSize(5).allSatisfy(check -> {
            assertThat(ENGINE.evaluate(check.rules(), check.input())).isInstanceOf(CaseError.class);
            assertThat(JsonSubset.mismatch(check.expected(), check.actual())).isNull();
        });
    }

    @Test
    void absentOptionalFieldTakesItsDefaultBeforeEvaluation() {
        assertThat(fixtureMismatches("C-11")).isEmpty();
    }

    @Test
    void rulesRunByPriorityThenIdAndDisabledOnesAreTracedAsDisabled() {
        assertThat(fixtureMismatches("C-01", "C-02", "C-25")).isEmpty();
    }

    @Test
    void conditionsAreEvaluatedWithoutShortCircuit() {
        assertThat(fixtureMismatches("C-08")).isEmpty();
        CompiledRuleSet any = rules(X + ", {\"name\": \"y\", \"type\": \"number\"}", rule("R-100", 100,
                "{\"any\": [{\"field\": \"x\", \"op\": \"gt\", \"value\": 0}, {\"field\": \"y\", \"op\": \"lt\","
                        + " \"value\": 5}]}", decide("reject", true)), APPROVE_ALL);

        Decision decision = (Decision) ENGINE.evaluate(any, object("{\"x\": 1}"));

        assertThat(decision.decidingRuleId()).isEqualTo("R-100");
        assertThat(decision.trace().getFirst().comparisons()).extracting(TraceStep.Compared::field, TraceStep.Compared::result)
                .containsExactly(tuple("x", true), tuple("y", false));
    }

    @Test
    void actionsApplyInOrderAndSetRecordsFromAndTo() {
        assertThat(fixtureMismatches("C-15", "C-16")).isEmpty();
    }

    @Test
    void terminalDecideStopsAndLaterRulesAreSkipped() {
        assertThat(fixtureMismatches("C-03", "C-07")).isEmpty();
    }

    @Test
    void highestSeverityCandidateWinsAndTheEarliestOnATie() {
        assertThat(fixtureMismatches("C-04", "C-05")).isEmpty();
        CompiledRuleSet tie = rules(X, rule("R-200", 200, ALWAYS, decide("reject", false)),
                rule("R-210", 210, ALWAYS, decide("reject", false)), rule("R-300", 300, ALWAYS, decide("refer", false)));

        Decision decision = (Decision) ENGINE.evaluate(tie, object("{\"x\": 1}"));

        assertThat(decision.decidingRuleId()).isEqualTo("R-200");
        assertThat(decision.reason()).isEqualTo("reject by rule");
        assertThat(decision.terminal()).isFalse();
        assertThat(decision.candidates()).extracting(Decision.Candidate::ruleId)
                .containsExactly("R-200", "R-210", "R-300");
        CompiledRuleSet weakerFirst = rules(X, rule("R-200", 200, ALWAYS, decide("approve", false)),
                rule("R-210", 210, ALWAYS, decide("refer", false)), rule("R-220", 220, ALWAYS, decide("reject", false)));
        assertThat(((Decision) ENGINE.evaluate(weakerFirst, object("{\"x\": 1}"))).decidingRuleId()).isEqualTo("R-220");
    }

    @Test
    void defaultOutcomeWhenNoRuleDecides() {
        assertThat(fixtureMismatches("C-06")).isEmpty();
    }

    @Test
    void evaluationErrorStopsWithStatusErrorAndThePartialTrace() {
        assertThat(fixtureMismatches("C-19", "C-28")).isEmpty();
        CompiledRuleSet nonFinite = rules(X + ", " + DERIVED_A, rule("R-10", 10, ALWAYS,
                set("a", "{\"fn\": \"pow\", \"args\": [{\"field\": \"x\"}, 0.5]}")), APPROVE_ALL);
        CompiledRuleSet absentField = rules(X + ", {\"name\": \"y\", \"type\": \"number\"}, " + DERIVED_A,
                rule("R-10", 10, ALWAYS, set("a", "{\"fn\": \"add\", \"args\": [{\"field\": \"y\"}, 1]}")), APPROVE_ALL);
        CompiledRuleSet absentDate = rules(X + ", {\"name\": \"d1\", \"type\": \"date\"}, {\"name\": \"d2\", \"type\":"
                        + " \"date\"}, {\"name\": \"m\", \"type\": \"number\", \"derived\": true}",
                rule("R-10", 10, "{\"field\": \"x\", \"op\": \"gt\", \"value\": 0}",
                        set("m", "{\"fn\": \"months_between\", \"args\": [{\"field\": \"d1\"}, {\"field\": \"d2\"}]}")),
                APPROVE_ALL);

        assertThat(List.of(
                        (Decision) ENGINE.evaluate(nonFinite, object("{\"x\": 2}")),
                        (Decision) ENGINE.evaluate(absentField, object("{\"x\": 1}")),
                        (Decision) ENGINE.evaluate(absentDate, object("{\"x\": 1, \"d1\": \"2025-01-01\"}"))))
                .extracting(Decision::status, Decision::errorCode, Decision::errorRuleId, d -> d.trace().size())
                .containsExactly(
                        tuple(Decision.Status.ERROR, EvaluationError.EVAL_NON_FINITE, "R-10", 1),
                        tuple(Decision.Status.ERROR, EvaluationError.EVAL_ABSENT_FIELD, "R-10", 1),
                        tuple(Decision.Status.ERROR, EvaluationError.EVAL_ABSENT_DATE, "R-10", 1));
    }

    @Test
    void missingOptionalCaseFieldComparesFalseExceptAbsent() {
        assertThat(fixtureMismatches("C-09", "C-10")).isEmpty();
        // not of a missing comparison is true, as the three-valued shortcut of Document 3 implies
        CompiledRuleSet negated = rules(X + ", {\"name\": \"y\", \"type\": \"number\"}",
                rule("R-100", 100, "{\"not\": {\"field\": \"y\", \"op\": \"lt\", \"value\": 5}}", decide("reject", true)),
                APPROVE_ALL);

        assertThat(((Decision) ENGINE.evaluate(negated, object("{\"x\": 1}"))).decidingRuleId()).isEqualTo("R-100");
    }

    @Test
    void unsetDerivedFieldIsLoudExceptForPresentAndAbsent() {
        assertThat(fixtureMismatches("C-28", "C-29")).isEmpty();
        CompiledRuleSet presence = rules(X + ", " + DERIVED_A,
                rule("R-10", 10, "{\"field\": \"x\", \"op\": \"gt\", \"value\": 100}", "{\"type\": \"set\", \"field\":"
                        + " \"a\", \"value\": 1}"),
                rule("R-100", 100, "{\"field\": \"a\", \"op\": \"absent\"}", decide("refer", true)), APPROVE_ALL);

        Decision decision = (Decision) ENGINE.evaluate(presence, object("{\"x\": 1}"));

        assertThat(decision.status()).isEqualTo(Decision.Status.OK);
        assertThat(decision.decidingRuleId()).isEqualTo("R-100");
    }

    @Test
    void everyOperatorComparesByItsType() {
        assertThat(fixtureMismatches("C-20", "C-21", "C-22", "C-23", "C-17")).isEmpty();
        ObjectNode case17 = decideJson(LENDING, object(CASE_17));

        assertThat(comparison(case17, "R-110", 1)).isEqualTo(tuple("ne", "salaried", true));
        assertThat(comparison(case17, "R-310", 0)).isEqualTo(tuple("in", "salaried", true));
        assertThat(comparison(case17, "R-120", 0)).isEqualTo(tuple("between", "60000", true));
        assertThat(comparison(case17, "R-200", 0)).isEqualTo(tuple("gt", "0.2835", false));
        assertThat(comparison(case17, "R-220", 0)).isEqualTo(tuple("gte", "1", false));
        assertThat(comparison(case17, "R-100", 0)).isEqualTo(tuple("lt", "34", false));
    }

    /** Each operator at its boundary; the expected results are the reference's compare on the same leaf and case. */
    @ParameterizedTest(name = "{0} is {1}")
    @MethodSource("operatorLeaves")
    void operatorsCompareByTypeAtTheirBoundaries(String leaf, boolean expected) {
        CompiledRuleSet oneLeaf = rules(OPERATOR_FIELDS, rule("R-100", 100, leaf, decide("reject", true)), APPROVE_ALL);

        Decision decision = (Decision) ENGINE.evaluate(oneLeaf, object(OPERATOR_CASE));

        assertThat(decision.trace().getFirst().comparisons().getFirst().result()).isEqualTo(expected);
    }

    static Stream<Arguments> operatorLeaves() {
        return Stream.of(
                arguments("{\"field\": \"x\", \"op\": \"eq\", \"value\": 5.0}", true),
                arguments("{\"field\": \"x\", \"op\": \"ne\", \"value\": 5}", false),
                arguments("{\"field\": \"x\", \"op\": \"lt\", \"value\": 5}", false),
                arguments("{\"field\": \"x\", \"op\": \"lte\", \"value\": 5}", true),
                arguments("{\"field\": \"x\", \"op\": \"lte\", \"value\": 4}", false),
                arguments("{\"field\": \"x\", \"op\": \"gt\", \"value\": 5}", false),
                arguments("{\"field\": \"x\", \"op\": \"gte\", \"value\": 5}", true),
                arguments("{\"field\": \"x\", \"op\": \"between\", \"value\": [5, 5]}", true),
                arguments("{\"field\": \"x\", \"op\": \"between\", \"value\": [5.1, 6]}", false),
                arguments("{\"field\": \"x\", \"op\": \"in\", \"value\": [1, 5.0]}", true),
                arguments("{\"field\": \"x\", \"op\": \"not_in\", \"value\": [5]}", false),
                arguments("{\"field\": \"x\", \"op\": \"not_in\", \"value\": [6]}", true),
                arguments("{\"field\": \"d\", \"op\": \"lt\", \"value\": \"2026-01-02\"}", true),
                arguments("{\"field\": \"d\", \"op\": \"gte\", \"value\": \"2026-01-02\"}", false),
                arguments("{\"field\": \"d\", \"op\": \"between\", \"value\": [\"2025-12-31\", \"2026-01-01\"]}", true),
                arguments("{\"field\": \"b\", \"op\": \"eq\", \"value\": true}", true),
                arguments("{\"field\": \"b\", \"op\": \"ne\", \"value\": true}", false),
                arguments("{\"field\": \"e\", \"op\": \"in\", \"value\": [\"b\", \"c\"]}", false),
                arguments("{\"field\": \"e\", \"op\": \"not_in\", \"value\": [\"b\", \"c\"]}", true),
                arguments("{\"field\": \"x\", \"op\": \"present\"}", true),
                arguments("{\"field\": \"y\", \"op\": \"present\"}", false),
                arguments("{\"field\": \"y\", \"op\": \"absent\"}", true),
                arguments("{\"field\": \"y\", \"op\": \"eq\", \"value\": 1}", false));
    }

    @Test
    void matchesUsesTheWholeValueAndCapsTheInputAt2000Characters() {
        assertThat(fixtureMismatches("C-22")).isEmpty();
        CompiledRuleSet whole = rules(X + ", {\"name\": \"s\", \"type\": \"string\"}",
                rule("R-100", 100, "{\"field\": \"s\", \"op\": \"matches\", \"value\": \"IL\"}", decide("reject", true)),
                APPROVE_ALL);
        // a derived string can be longer than a case string; only its first 2,000 characters are matched
        CompiledRuleSet capped = rules(X + ", {\"name\": \"s\", \"type\": \"string\", \"derived\": true}",
                rule("R-10", 10, ALWAYS, "{\"type\": \"set\", \"field\": \"s\", \"value\": \"" + "a".repeat(2001) + "\"}"),
                rule("R-100", 100, "{\"field\": \"s\", \"op\": \"matches\", \"value\": \"a{1000}a{1000}\"}", decide("reject", true)),
                APPROVE_ALL);

        assertThat(((Decision) ENGINE.evaluate(whole, object("{\"x\": 1, \"s\": \"IL12\"}"))).decidingRuleId())
                .isEqualTo("R-900");
        assertThat(((Decision) ENGINE.evaluate(capped, object("{\"x\": 1}"))).decidingRuleId()).isEqualTo("R-100");
    }

    @Test
    void redosPatternRunsInLinearTime() {
        CompiledRuleSet redos = rules(X + ", {\"name\": \"s\", \"type\": \"string\"}",
                rule("R-100", 100, "{\"field\": \"s\", \"op\": \"matches\", \"value\": \"(a+)+$\"}", decide("reject", true)),
                APPROVE_ALL);
        ObjectNode input = object("{\"x\": 1}").put("s", "a".repeat(1999) + "!");

        long best = Long.MAX_VALUE;
        for (int run = 0; run < 20; run++) {
            long start = System.nanoTime();
            ENGINE.evaluate(redos, input);
            best = Math.min(best, System.nanoTime() - start);
        }

        assertThat(best).as("best of 20 runs, in nanoseconds").isLessThan(10_000_000L);
        assertThat(((Decision) ENGINE.evaluate(redos, input)).decidingRuleId()).isEqualTo("R-900");
    }

    @Test
    void flagsCarryCodeMessageAndRuleId() {
        ObjectNode input = object("""
                {"age": 30, "employment_type": "salaried", "employment_months": 24, "monthly_income": 8500,
                 "existing_monthly_debt": 0, "requested_amount": 20000, "term_months": 36, "credit_events_24m": 0}""");

        Decision decision = (Decision) ENGINE.evaluate(LENDING, input);

        assertThat(decision.decidingRuleId()).isEqualTo("R-900");
        assertThat(decision.flags()).containsExactly(
                new Decision.Flag("INCOME_NEAR_MINIMUM", "ההכנסה בטווח של 1,000 ש\"ח מעל המינימום", "R-410"),
                new Decision.Flag("STABLE_INCOME_MANUAL_CHECK",
                        "המדיניות דורשת הכנסה יציבה; הקריטריון אינו מוגדר ונבדק ידנית", "R-420"));
        assertThat(DecisionJson.toJson(decision).get("flags").get(1)).isEqualTo(object("""
                {"code": "STABLE_INCOME_MANUAL_CHECK", "message": "המדיניות דורשת הכנסה יציבה; הקריטריון אינו מוגדר ונבדק ידנית",
                 "ruleId": "R-420"}"""));
    }

    @Test
    void simulationAppliesOverridesAndLeavesTheBaseUnchanged() {
        Check c31 = checks("C-31").getFirst();
        ObjectNode base = c31.input().deepCopy();

        assertThat(JsonSubset.mismatch(c31.expected(), c31.actual())).isNull();
        assertThat(c31.input()).isEqualTo(base);
        assertThat(((Decision) ENGINE.evaluate(c31.rules(), base)).decidingRuleId()).isEqualTo("R-330");
        assertThat(((Decision) ENGINE.evaluate(c31.rules(), base)).simulation()).isFalse();
    }

    @Test
    void simulationWithAnInvalidOverrideIsACaseError() {
        Evaluation result = ENGINE.simulate(LENDING, object(CASE_17), object("{\"debt_to_income\": 0.1}"));

        assertThat(result).isInstanceOfSatisfying(CaseError.class, error -> assertThat(error.problems())
                .extracting(CaseProblem::code, CaseProblem::field)
                .containsExactly(tuple(CaseProblem.Code.CASE_DERIVED_SUPPLIED, "debt_to_income")));
    }

    @Test
    void compiledRuleSetSortsRulesAndCompilesEachPatternOnce() {
        CompiledRuleSet tieById = checks("C-02").getFirst().rules();
        CompiledRuleSet twoPatterns = rules(X + ", {\"name\": \"s\", \"type\": \"string\"}",
                rule("R-110", 110, "{\"field\": \"s\", \"op\": \"matches\", \"value\": \"^IL[0-9]{2}\"}", decide("refer", true)),
                rule("R-100", 100, "{\"field\": \"s\", \"op\": \"matches\", \"value\": \"^IL[0-9]{2}\"}", decide("reject", true)),
                APPROVE_ALL);

        assertThat(tieById.rules()).extracting(Rule::id).containsExactly("R-10", "R-20");
        assertThat(twoPatterns.rules()).extracting(Rule::id).containsExactly("R-100", "R-110", "R-900");
        assertThat(twoPatterns.pattern("^IL[0-9]{2}")).isNotNull().isSameAs(twoPatterns.pattern("^IL[0-9]{2}"));
        assertThat(LENDING.rules()).extracting(Rule::id).startsWith("R-010", "R-020", "R-100").endsWith("R-900");
        CompiledRuleSet nested = rules(X + ", {\"name\": \"s\", \"type\": \"string\"}", rule("R-100", 100,
                "{\"all\": [{\"any\": [{\"not\": {\"field\": \"s\", \"op\": \"matches\", \"value\": \"^X\"}}]}]}",
                decide("reject", true)), APPROVE_ALL);
        assertThat(nested.pattern("^X")).isNotNull();
        assertThat(((Decision) ENGINE.evaluate(nested, object("{\"x\": 1, \"s\": \"Y\"}"))).decidingRuleId())
                .isEqualTo("R-100");
    }

    @Test
    void twoHundredCasesDecideWellUnder200Milliseconds() {
        List<ObjectNode> inputs = new ArrayList<>();
        Fixtures.json("policies/consumer-lending/cases-200.json").get("cases")
                .forEach(fixtureCase -> inputs.add((ObjectNode) fixtureCase.get("input")));
        inputs.forEach(input -> ENGINE.evaluate(LENDING, input));   // warm up

        long start = System.nanoTime();
        inputs.forEach(input -> ENGINE.evaluate(LENDING, input));
        long elapsed = System.nanoTime() - start;

        // Document 6: the timing guard fails only above three times its 200 ms target
        assertThat(elapsed).as("200 cases, in nanoseconds").isLessThan(600_000_000L);
    }

    // ------------------------------------------------------------------ helpers

    private static final String X = "{\"name\": \"x\", \"type\": \"number\", \"required\": true}";
    private static final String DERIVED_A = "{\"name\": \"a\", \"type\": \"number\", \"derived\": true}";
    private static final String ALWAYS = "{\"always\": true}";
    private static final String OPERATOR_FIELDS = """
            {"name": "x", "type": "number"}, {"name": "d", "type": "date"}, {"name": "b", "type": "boolean"},
            {"name": "e", "type": "enum", "values": ["a", "b", "c"]}, {"name": "y", "type": "number"}""";
    private static final String OPERATOR_CASE = "{\"x\": 5, \"d\": \"2026-01-01\", \"b\": true, \"e\": \"a\"}";
    private static final String APPROVE_ALL = rule("R-900", 900, ALWAYS, decide("approve", true));

    /** Every difference between the named fixtures' expectations and the engine, as the reference checks them. */
    private static List<String> fixtureMismatches(String... names) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            for (Check check : checks(name)) {
                ObjectNode actual = check.actual();
                String problem = JsonSubset.mismatch(check.expected(), actual);
                if (problem != null) {
                    out.add(name + " " + problem);
                }
                check.check().path("traceStatus").properties().forEach(entry -> {
                    String status = stepStatus(actual, entry.getKey());
                    if (!status.equals(entry.getValue().stringValue())) {
                        out.add(name + " " + entry.getKey() + " is " + status);
                    }
                });
            }
        }
        return out;
    }

    private static String stepStatus(JsonNode decision, String ruleId) {
        for (JsonNode step : decision.get("trace")) {
            if (step.get("ruleId").stringValue().equals(ruleId)) {
                return step.get("status").stringValue();
            }
        }
        return "absent";
    }

    /** Op, actual value as text, and result of one comparison of a rule in a decision's trace. */
    private static org.assertj.core.groups.Tuple comparison(JsonNode decision, String ruleId, int index) {
        for (JsonNode step : decision.get("trace")) {
            if (step.get("ruleId").stringValue().equals(ruleId)) {
                JsonNode compared = step.get("comparisons").get(index);
                return tuple(compared.get("op").stringValue(), compared.get("actual").asString(),
                        compared.get("result").booleanValue());
            }
        }
        throw new AssertionError("no trace step for " + ruleId);
    }

    private static CompiledRuleSet rules(String fields, String... rules) {
        return compile(object("""
                {"dslVersion": "1.0", "id": "engine-test", "name": "Engine test", "language": "en",
                 "fields": [%s], "defaults": {"outcome": "refer", "reason": "nothing decided"}, "rules": [%s]}"""
                .formatted(fields, String.join(", ", rules))));
    }

    private static String rule(String id, int priority, String condition, String action) {
        return """
                {"id": "%s", "label": "rule %s", "priority": %d, "condition": %s, "actions": [%s],
                 "provenance": {"kind": "analyst", "note": "engine test rule", "actor": "test"}}"""
                .formatted(id, id, priority, condition, action);
    }

    private static String decide(String outcome, boolean terminal) {
        return "{\"type\": \"decide\", \"outcome\": \"%s\", \"terminal\": %s, \"reason\": \"%s by rule\"}"
                .formatted(outcome, terminal, outcome);
    }

    private static String set(String field, String expression) {
        return "{\"type\": \"set\", \"field\": \"%s\", \"value\": %s}".formatted(field, expression);
    }
}
