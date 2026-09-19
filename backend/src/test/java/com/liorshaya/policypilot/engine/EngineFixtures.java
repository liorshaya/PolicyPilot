package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** The engine on the committed fixtures, as the reference runs them (fixtures/README.md, file shapes). */
final class EngineFixtures {

    static final RuleSetMapper MAPPER = new RuleSetMapper();
    static final RuleEngine ENGINE = new RuleEngine();
    static final CompiledRuleSet LENDING = compile(Fixtures.lendingV1());

    /** Case 17 of the demo (Document 3, Worked Example: A Case and Its Trace). */
    static final String CASE_17 = """
            {"age": 34, "employment_type": "salaried", "employment_months": 30, "monthly_income": 9500,
             "existing_monthly_debt": 1200, "requested_amount": 60000, "term_months": 48, "credit_events_24m": 1}""";

    private EngineFixtures() {}

    static CompiledRuleSet compile(JsonNode document) {
        return CompiledRuleSet.compile(MAPPER.toRuleSet(document));
    }

    static ObjectNode object(String json) {
        return (ObjectNode) MAPPER.readTree(json);
    }

    static ObjectNode decideJson(CompiledRuleSet rules, ObjectNode input) {
        return DecisionJson.toJson(ENGINE.evaluate(rules, input));
    }

    /** One check of a conformance fixture: its rule set, case, overrides, expected subset and trace statuses. */
    record Check(String name, CompiledRuleSet rules, JsonNode check) {

        ObjectNode input() {
            return (ObjectNode) check.get("case");
        }

        JsonNode expected() {
            return check.get("expected");
        }

        /** The engine's answer as JSON: a decision, an ERROR decision, a case error, or a simulation. */
        ObjectNode actual() {
            Evaluation evaluation = check.has("overrides")
                    ? ENGINE.simulate(rules, input(), (ObjectNode) check.get("overrides"))
                    : ENGINE.evaluate(rules, input());
            return DecisionJson.toJson(evaluation);
        }
    }

    /** The checks of {@code fixtures/conformance/<name>.json}, in file order. */
    static List<Check> checks(String name) {
        JsonNode fixture = Fixtures.json("conformance/" + name + ".json");
        CompiledRuleSet rules = compile(Fixtures.ruleSetOf(fixture));
        List<Check> out = new ArrayList<>();
        if (fixture.has("checks")) {
            fixture.get("checks").forEach(check -> out.add(new Check(name, rules, check)));
        } else {
            out.add(new Check(name, rules, fixture));
        }
        return out;
    }
}
