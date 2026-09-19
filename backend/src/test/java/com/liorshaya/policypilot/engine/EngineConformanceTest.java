package com.liorshaya.policypilot.engine;

import static com.liorshaya.policypilot.engine.EngineFixtures.ENGINE;
import static com.liorshaya.policypilot.engine.EngineFixtures.LENDING;
import static com.liorshaya.policypilot.engine.EngineFixtures.compile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.liorshaya.policypilot.engine.EngineFixtures.Check;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.JsonSubset;
import com.liorshaya.policypilot.support.Requirement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Java and the reference on the same files (Document 3, Engine Conformance Suite; Document 6, Golden files and
 * Reference agreement): the 31 conformance cases, the 200 lending cases against {@code cases-expected.json}, and the
 * labeled cases of the 18 evaluation policies.
 */
@Requirement({"FR-8", "FR-9", "NFR-1"})
class EngineConformanceTest {

    private static final JsonNode EXPECTED = Fixtures.json("policies/consumer-lending/cases-expected.json");
    private static final List<JsonNode> CASES = list(Fixtures.json("policies/consumer-lending/cases-200.json").get("cases"));

    /** As {@code run_conformance}: a subset match with trace statuses, a simulation that leaves its base alone, C-26. */
    @ParameterizedTest(name = "{0} check {1}")
    @MethodSource("conformanceChecks")
    void conformanceCaseDecidesAsTheFixtureExpects(String name, int index, Check check) {
        if (check.check().has("cases")) {
            List<JsonNode> cases = list(Fixtures.json(check.check().get("cases").stringValue()).get("cases"));
            assertThat(cases).hasSize(check.expected().get("count").intValue());
            assertThat(canonicalAll(check.rules(), cases)).isEqualTo(canonicalAll(check.rules(), cases));
            return;
        }
        String before = DecisionJson.canonical(ENGINE.evaluate(check.rules(), check.input()));
        ObjectNode actual = check.actual();

        assertThat(JsonSubset.mismatch(check.expected(), actual)).isNull();
        check.check().path("traceStatus").properties().forEach(entry ->
                assertThat(status(actual, entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue().stringValue()));
        assertThat(DecisionJson.canonical(ENGINE.evaluate(check.rules(), check.input()))).isEqualTo(before);
    }

    @Test
    void twoHundredCasesTwiceByteIdentical() {
        List<String> first = canonicalAll(LENDING, CASES);
        List<String> second = canonicalAll(LENDING, CASES);

        assertThat(first).hasSize(200).isEqualTo(second);
    }

    @Test
    void everyFixtureCaseMatchesCasesExpected() {
        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < CASES.size(); i++) {
            ObjectNode expected = (ObjectNode) EXPECTED.get("cases").get(i).deepCopy();
            expected.remove("id");
            String problem = JsonSubset.mismatch(expected, summary(decide(CASES.get(i))));
            if (problem != null) {
                mismatches.add("case " + (i + 1) + ": " + problem);
            }
        }

        assertThat(mismatches).isEmpty();
    }

    @Test
    void summaryMatchesCasesExpected() {
        Map<String, Integer> outcomes = new LinkedHashMap<>();
        Map<String, Integer> decidingRules = new LinkedHashMap<>();
        for (JsonNode fixtureCase : CASES) {
            ObjectNode decision = decide(fixtureCase);
            outcomes.merge(decision.get("outcome").stringValue(), 1, Integer::sum);
            decidingRules.merge(decision.get("decidingRuleId").stringValue(), 1, Integer::sum);
        }
        ArrayNode top = JsonNodeFactory.instance.arrayNode();
        decidingRules.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .forEach(entry -> top.addObject().put("ruleId", entry.getKey()).put("count", entry.getValue()));

        JsonNode summary = EXPECTED.get("summary");
        assertThat(outcomes).containsExactlyInAnyOrderEntriesOf(Map.of(
                "approve", summary.get("outcomes").get("approve").intValue(),
                "reject", summary.get("outcomes").get("reject").intValue(),
                "refer", summary.get("outcomes").get("refer").intValue()));
        assertThat(top).isEqualTo(summary.get("topDecidingRules"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("evaluationPolicies")
    void everyEvaluationPolicyCaseDecidesAsLabeled(String slug) {
        CompiledRuleSet rules = compile(Fixtures.json("eval/policies/" + slug + "/expected.ruleset.json"));
        List<String> mismatches = new ArrayList<>();
        for (JsonNode labeled : Fixtures.json("eval/policies/" + slug + "/cases.json").get("cases")) {
            ObjectNode decision = DecisionJson.toJson(ENGINE.evaluate(rules, (ObjectNode) labeled.get("input")));
            assertThat(decision.get("status").stringValue()).as("case %s", labeled.get("id")).isEqualTo("OK");
            String problem = JsonSubset.mismatch(labeled.get("expected"), summary(decision));
            if (problem != null) {
                mismatches.add("case " + labeled.get("id") + ": " + problem);
            }
        }

        assertThat(mismatches).isEmpty();
    }

    static Stream<Arguments> conformanceChecks() {
        List<Arguments> out = new ArrayList<>();
        for (Path file : Fixtures.conformanceCases()) {
            String name = file.getFileName().toString().replace(".json", "");
            List<Check> checks = EngineFixtures.checks(name);
            for (int i = 0; i < checks.size(); i++) {
                out.add(arguments(name, i, checks.get(i)));
            }
        }
        return out.stream();
    }

    static Stream<String> evaluationPolicies() {
        return Fixtures.evaluationPolicies().stream();
    }

    /** The golden fields of a decision: outcome, deciding rule, derived values, and the flag codes. */
    private static ObjectNode summary(ObjectNode decision) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.set("outcome", decision.get("outcome"));
        out.set("decidingRuleId", decision.get("decidingRuleId"));
        out.set("derived", decision.get("derived"));
        ArrayNode flags = out.putArray("flags");
        decision.get("flags").forEach(flag -> flags.add(flag.get("code")));
        return out;
    }

    private static ObjectNode decide(JsonNode fixtureCase) {
        return DecisionJson.toJson(ENGINE.evaluate(LENDING, (ObjectNode) fixtureCase.get("input")));
    }

    private static List<String> canonicalAll(CompiledRuleSet rules, List<JsonNode> cases) {
        return cases.stream().map(c -> DecisionJson.canonical(ENGINE.evaluate(rules, (ObjectNode) c.get("input"))))
                .toList();
    }

    private static String status(JsonNode decision, String ruleId) {
        for (JsonNode step : decision.get("trace")) {
            if (step.get("ruleId").stringValue().equals(ruleId)) {
                return step.get("status").stringValue();
            }
        }
        return "absent";
    }

    private static List<JsonNode> list(JsonNode array) {
        List<JsonNode> out = new ArrayList<>();
        array.forEach(out::add);
        return out;
    }
}
