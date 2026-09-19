package com.liorshaya.policypilot.engine;

import static com.liorshaya.policypilot.engine.EngineFixtures.CASE_17;
import static com.liorshaya.policypilot.engine.EngineFixtures.LENDING;
import static com.liorshaya.policypilot.engine.EngineFixtures.decideJson;
import static com.liorshaya.policypilot.engine.EngineFixtures.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.JsonSubset;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The trace and decision format (Document 3, Decision object and TraceStep object). Expected values come from the
 * golden {@code sample-decision.json}, produced by the reference for case 17, and from the conformance fixtures.
 */
@Requirement({"FR-8", "NFR-2"})
class TraceTest {

    private static final JsonNode GOLDEN = Fixtures.json("policies/consumer-lending/sample-decision.json");

    @Test
    void firedStepCarriesComparisonsActionsAndProvenance() {
        JsonNode r330 = step(decideJson(LENDING, object(CASE_17)), "R-330");

        // equal to the golden step both ways, numbers by value (the reference writes 1 as 1.0)
        assertThat(JsonSubset.mismatch(step(GOLDEN, "R-330"), r330)).isNull();
        assertThat(JsonSubset.mismatch(r330, step(GOLDEN, "R-330"))).isNull();
        assertThat(r330.get("status").stringValue()).isEqualTo("fired");
        assertThat(r330.get("comparisons")).hasSize(2);
        assertThat(r330.get("actions").get(0).get("outcome").stringValue()).isEqualTo("refer");
        assertThat(r330.get("provenance").get("paragraph").intValue()).isEqualTo(7);
    }

    @Test
    void notFiredStepShowsAnExpressionAsItsValueAndText() {
        JsonNode r116 = step(decideJson(LENDING, object(CASE_17)), "R-116");
        JsonNode expected = r116.get("comparisons").get(1).get("expected");

        assertThat(r116.get("status").stringValue()).isEqualTo("not_fired");
        assertThat(r116.has("actions")).isFalse();
        assertThat(expected.get("value").decimalValue()).isEqualByComparingTo("74");
        assertThat(expected.get("text").stringValue()).isEqualTo("(78 - (term_months / 12))");
    }

    @Test
    void skippedAndDisabledStepsCarryIdentityAndStatusOnly() {
        JsonNode r900 = step(decideJson(LENDING, object(CASE_17)), "R-900");
        JsonNode disabled = EngineFixtures.checks("C-25").getFirst().actual().get("trace").get(0);

        assertThat(r900.propertyNames()).containsExactly("ruleId", "label", "priority", "status");
        assertThat(r900.get("status").stringValue()).isEqualTo("skipped");
        assertThat(disabled.propertyNames()).containsExactly("ruleId", "label", "priority", "status");
        assertThat(disabled.get("status").stringValue()).isEqualTo("disabled");
    }

    @Test
    void errorStepCarriesItsCodeAndDetail() {
        ObjectNode c28 = EngineFixtures.checks("C-28").getFirst().actual();
        JsonNode failing = c28.get("trace").get(c28.get("trace").size() - 1);

        assertThat(failing.get("status").stringValue()).isEqualTo("error");
        assertThat(failing.get("error").get("code").stringValue()).isEqualTo("EVAL_DERIVED_ABSENT");
        assertThat(failing.get("error").get("detail").stringValue()).isEqualTo("d");
        assertThat(failing.has("provenance")).isTrue();
        assertThat(c28.propertyNames()).containsExactly("status", "errorCode", "errorRuleId", "derived", "trace");
    }

    @Test
    void derivedListsEveryDerivedFieldWithNullWhenUnset() {
        ObjectNode c29 = EngineFixtures.checks("C-29").getFirst().actual();

        assertThat(c29.get("derived").propertyNames()).containsExactly("monthly_installment", "debt_to_income");
        assertThat(c29.get("derived").get("debt_to_income").isNull()).isTrue();
        assertThat(c29.get("derived").get("monthly_installment").decimalValue()).isEqualByComparingTo("1493.1");
    }

    @Test
    void serializationIsCanonical() {
        String first = DecisionJson.canonical(EngineFixtures.ENGINE.evaluate(LENDING, object(CASE_17)));
        String second = DecisionJson.canonical(EngineFixtures.ENGINE.evaluate(LENDING, object(CASE_17)));

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("{\"status\":\"OK\",\"outcome\":\"refer\",\"reason\":");
        assertThat(first).contains("\"derived\":{\"monthly_installment\":1493.1,\"debt_to_income\":0.2835}");
        assertThat(first).contains("\"actual\":60000,").doesNotContain("E+").doesNotContain("E-");
        EngineFixtures.Check c18 = EngineFixtures.checks("C-18").getFirst();
        assertThat(DecisionJson.canonical(EngineFixtures.ENGINE.evaluate(c18.rules(), c18.input())))
                .contains("\"a\":0.333333333333");
    }

    @Test
    void decisionEqualsTheGoldenSampleDecision() {
        ObjectNode actual = decideJson(LENDING, object(CASE_17));

        assertThat(JsonSubset.mismatch(GOLDEN, actual)).isNull();
        assertThat(JsonSubset.mismatch(actual, GOLDEN)).isNull();
    }

    private static JsonNode step(JsonNode decision, String ruleId) {
        for (JsonNode step : decision.get("trace")) {
            if (step.get("ruleId").stringValue().equals(ruleId)) {
                return step;
            }
        }
        throw new AssertionError("no trace step for " + ruleId);
    }
}
