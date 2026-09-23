package com.liorshaya.policypilot.decision.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.engine.CompiledRuleSet;
import com.liorshaya.policypilot.engine.Decision;
import com.liorshaya.policypilot.engine.RuleEngine;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The regression report of Document 3 on the labeled changes of fixtures/eval/changes.json: each request's expected
 * patches applied to its policy's rule set, and every case of its regression case file decided by the rule set and
 * again by the copy. The flip counts are the fixture's, which its builder took from the Python reference.
 */
@Requirement("FR-18")
class RegressionTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();
    private static final RuleEngine ENGINE = new RuleEngine();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // changes.json: 12 flips for CR-1, 3 for CR-2, 1 for CR-3, 0 for CR-4 (a derived rate, no outcome) and 1 for
    // CR-5. Expected: those counts, each over every case of the request's file
    @ParameterizedTest
    @ValueSource(strings = {"CR-1", "CR-2", "CR-3", "CR-4", "CR-5"})
    void eachLabeledChangeFlipsTheCasesTheReferenceCounted(String id) {
        JsonNode change = labeled(id);
        ObjectNode base = (ObjectNode) Fixtures.json(change.required("ruleset").asString()).deepCopy();
        List<ObjectNode> inputs = inputs(change.required("regression").required("cases").asString());

        Regression regression = regression(base, patched(base, change.required("expected").required("patches")),
                inputs);

        assertThat(regression.decisions()).isEqualTo(inputs.size());
        assertThat(regression.flips()).hasSize(change.required("regression").required("flips").asInt());
    }

    // Document 3: "exactly 12 of the 200 cases flip from approve or refer to reject". Expected: every flip lands on
    // reject by R-170, the raised threshold, and the transitions add up to the twelve
    @Test
    void theScriptedChangeFlipsApprovalsAndReferralsToRejection() {
        JsonNode change = labeled("CR-1");
        ObjectNode base = Fixtures.lendingV1();

        Regression regression = regression(base, patched(base, change.required("expected").required("patches")),
                inputs(change.required("regression").required("cases").asString()));

        assertThat(regression.flips()).allSatisfy(flip -> {
            assertThat(flip.after()).isEqualTo("reject");
            assertThat(flip.decidingRuleAfter()).isEqualTo("R-170");
            assertThat(flip.before()).isIn("approve", "refer");
        });
        assertThat(regression.transitions().keySet()).isSubsetOf("approve → reject", "refer → reject");
        assertThat(regression.transitions().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(12);
    }

    // Expected: the flips by case number, a case decided on its own after every stored case, and two of those in
    // the order of their decision ids
    @Test
    void flipsAreInCaseOrderWithCasesDecidedOnTheirOwnLast() {
        ObjectNode base = Fixtures.lendingV1();
        ObjectNode input = inputs("policies/consumer-lending/cases-200.json").getFirst();
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        List<Regression.Decided> decided = List.of(
                new Regression.Decided(second, null, "refer", null, input),
                new Regression.Decided(UUID.randomUUID(), 3, "refer", null, input),
                new Regression.Decided(first, null, "refer", null, input),
                new Regression.Decided(UUID.randomUUID(), 1, "refer", null, input));
        String outcome = outcomeOf(base, input);

        Regression regression = Regression.of(decided, CompiledRuleSet.compile(MAPPER.toRuleSet(base)), ENGINE);

        assertThat(outcome).isNotEqualTo("refer");
        assertThat(regression.flips()).extracting(Regression.Flip::caseNo).containsExactly(1, 3, null, null);
        assertThat(regression.flips().subList(2, 4)).extracting(Regression.Flip::decisionId)
                .containsExactly(first, second);
        assertThat(regression.transitions()).isEqualTo(Map.of("refer → " + outcome, 4));
    }

    // A copy with a new required case field cannot decide the stored inputs. Expected: the decision flips to error,
    // with no deciding rule after, and an input that was already an error and still is does not flip
    @Test
    void anInputTheCopyCannotDecideIsAnError() {
        ObjectNode base = Fixtures.lendingV1();
        ObjectNode copy = base.deepCopy();
        ((ArrayNode) copy.required("fields")).add(JSON.readTree(
                "{\"name\": \"bonus_income\", \"type\": \"number\", \"required\": true}"));
        ObjectNode input = inputs("policies/consumer-lending/cases-200.json").getFirst();
        List<Regression.Decided> decided = List.of(
                new Regression.Decided(UUID.randomUUID(), 1, outcomeOf(base, input), "R-900", input),
                new Regression.Decided(UUID.randomUUID(), 2, Regression.ERROR, null, input));

        Regression regression = Regression.of(decided, CompiledRuleSet.compile(MAPPER.toRuleSet(copy)), ENGINE);

        assertThat(regression.decisions()).isEqualTo(2);
        assertThat(regression.flips()).singleElement().satisfies(flip -> {
            assertThat(flip.caseNo()).isEqualTo(1);
            assertThat(flip.after()).isEqualTo(Regression.ERROR);
            assertThat(flip.decidingRuleAfter()).isNull();
        });
    }

    // Document 3: "A sandbox that has decided nothing on the base version gets an empty report". Expected: no
    // decisions, no flips and no transitions
    @Test
    void nothingDecidedIsAnEmptyReport() {
        Regression regression = Regression.of(List.of(),
                CompiledRuleSet.compile(MAPPER.toRuleSet(Fixtures.lendingV1())), ENGINE);

        assertThat(regression.decisions()).isZero();
        assertThat(regression.flips()).isEmpty();
        assertThat(regression.transitions()).isEmpty();
    }

    /** Every input decided by the base, then the regression of the copy over those decisions. */
    private static Regression regression(ObjectNode base, ObjectNode copy, List<ObjectNode> inputs) {
        List<Regression.Decided> decided = new ArrayList<>();
        CompiledRuleSet compiled = CompiledRuleSet.compile(MAPPER.toRuleSet(base));
        for (int i = 0; i < inputs.size(); i++) {
            Decision decision = (Decision) ENGINE.evaluate(compiled, inputs.get(i).deepCopy());
            decided.add(new Regression.Decided(UUID.randomUUID(), i + 1, decision.outcome().json(),
                    decision.decidingRuleId(), inputs.get(i)));
        }
        return Regression.of(decided, CompiledRuleSet.compile(MAPPER.toRuleSet(copy)), ENGINE);
    }

    private static String outcomeOf(ObjectNode ruleSet, ObjectNode input) {
        return ((Decision) ENGINE.evaluate(CompiledRuleSet.compile(MAPPER.toRuleSet(ruleSet)), input.deepCopy()))
                .outcome().json();
    }

    /** The base with the expected patches applied as Document 3 applies them: replace in place, add, remove. */
    private static ObjectNode patched(ObjectNode base, JsonNode patches) {
        ObjectNode copy = base.deepCopy();
        ArrayNode rules = (ArrayNode) copy.required("rules");
        for (JsonNode patch : patches) {
            String ruleId = patch.required("ruleId").asString();
            switch (patch.required("op").asString()) {
                case "add" -> rules.add(patch.required("rule"));
                case "remove" -> rules.removeIf(rule -> rule.required("id").asString().equals(ruleId));
                default -> {
                    for (int i = 0; i < rules.size(); i++) {
                        if (rules.get(i).required("id").asString().equals(ruleId)) {
                            rules.set(i, patch.required("rule"));
                        }
                    }
                }
            }
        }
        return copy;
    }

    private static List<ObjectNode> inputs(String caseFile) {
        return Fixtures.json(caseFile).required("cases").valueStream()
                .map(labeledCase -> (ObjectNode) labeledCase.required("input").deepCopy()).toList();
    }

    private static JsonNode labeled(String id) {
        return Fixtures.json("eval/changes.json").required("changes").valueStream()
                .filter(change -> change.required("id").asString().equals(id)).findFirst().orElseThrow();
    }
}
