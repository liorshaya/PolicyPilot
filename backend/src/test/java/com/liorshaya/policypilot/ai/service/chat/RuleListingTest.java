package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What {@code listRules(tag?)} answers (Document 4, Tools available to the answer prompt: "Rule ids, labels,
 * priorities, outcomes"). Every expected value is read from {@code fixtures/policies/consumer-lending/
 * ruleset.v1.json}, the rule set the demo publishes, never from the listing itself.
 */
class RuleListingTest {

    private static final ObjectNode DOCUMENT = Fixtures.lendingV1();
    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(DOCUMENT);

    // Document 4, listRules. Expected: the fixture's 20 rules, each with the id, label and priority it carries there
    @Test
    void everyRuleIsListedWithItsIdLabelAndPriority() {
        JsonNode listed = RuleListing.of(LENDING, null).required("rules");

        assertThat(listed.size()).isEqualTo(DOCUMENT.required("rules").size());
        for (JsonNode expected : DOCUMENT.required("rules")) {
            JsonNode rule = byId(listed, expected.required("id").asString());
            assertThat(rule.required("label").asString()).isEqualTo(expected.required("label").asString());
            assertThat(rule.required("priority").asInt()).isEqualTo(expected.required("priority").asInt());
        }
    }

    // Document 4, listRules: "outcomes". Expected: R-100 rejects, R-900 approves, R-330 refers, as the fixture says
    @Test
    void aRuleThatDecidesIsListedWithTheOutcomeItRecords() {
        JsonNode listed = RuleListing.of(LENDING, null).required("rules");

        assertThat(byId(listed, "R-100").required("outcome").asString()).isEqualTo("reject");
        assertThat(byId(listed, "R-330").required("outcome").asString()).isEqualTo("refer");
        assertThat(byId(listed, "R-900").required("outcome").asString()).isEqualTo("approve");
    }

    // Document 3, Actions: a rule may derive a field or attach a flag instead of deciding. Expected: R-010 sets
    // monthly_installment and R-420 flags STABLE_INCOME_MANUAL_CHECK, neither carrying an outcome
    @Test
    void aRuleThatDerivesOrFlagsIsListedByWhatItDoesAndCarriesNoOutcome() {
        JsonNode listed = RuleListing.of(LENDING, null).required("rules");

        JsonNode derives = byId(listed, "R-010");
        assertThat(derives.path("outcome").isMissingNode()).isTrue();
        assertThat(derives.required("sets")).singleElement().extracting(JsonNode::asString)
                .isEqualTo("monthly_installment");
        JsonNode flags = byId(listed, "R-420");
        assertThat(flags.path("outcome").isMissingNode()).isTrue();
        assertThat(flags.required("flags")).singleElement().extracting(JsonNode::asString)
                .isEqualTo("STABLE_INCOME_MANUAL_CHECK");
    }

    // Document 3, Evaluation order. Expected: the fixture's rules by ascending priority, so the listing reads the way
    // the engine evaluates them and not the way the document happens to be written
    @Test
    void rulesAreListedInEvaluationOrder() {
        JsonNode listed = RuleListing.of(LENDING, null).required("rules");

        List<Integer> priorities = new ArrayList<>();
        listed.forEach(rule -> priorities.add(rule.required("priority").asInt()));
        assertThat(priorities).isSorted();
    }

    // Document 4, listRules(tag?). Expected: the two rules the fixture tags credit_history, R-220 and R-330
    @Test
    void aTagListsOnlyTheRulesCarryingIt() {
        JsonNode listed = RuleListing.of(LENDING, "credit_history").required("rules");

        assertThat(ids(listed)).isEqualTo(idsTagged("credit_history"));
    }

    // Document 4, listRules(tag?): a tag is one of several a rule may carry. Expected: R-020, tagged derivation and
    // affordability in the fixture, is listed under both
    @Test
    void aRuleIsListedUnderEveryTagItCarries() {
        assertThat(ids(RuleListing.of(LENDING, "derivation").required("rules"))).contains("R-020");
        assertThat(ids(RuleListing.of(LENDING, "affordability").required("rules"))).contains("R-020");
    }

    // Document 4, listRules(tag?). Expected: an empty list, not every rule, so the model is not handed the whole set
    // when it asked for something the rule set does not tag
    @Test
    void aTagNoRuleCarriesListsNothing() {
        JsonNode listed = RuleListing.of(LENDING, "no_such_tag").required("rules");

        assertThat(listed).isEmpty();
    }

    // Document 3, enabled: a disabled rule is never evaluated. Expected: it is listed and marked, so an answer cannot
    // read it as one that still decides
    @Test
    void aDisabledRuleIsListedAsDisabled() {
        RuleSet withDisabled = disable(LENDING, "R-330");

        JsonNode listed = RuleListing.of(withDisabled, null).required("rules");

        assertThat(byId(listed, "R-330").required("enabled").asBoolean()).isFalse();
        assertThat(byId(listed, "R-320").path("enabled").isMissingNode()).isTrue();
    }

    private static JsonNode byId(JsonNode listed, String ruleId) {
        return listed.valueStream().filter(rule -> ruleId.equals(rule.path("id").asString("")))
                .findFirst().orElseThrow(() -> new AssertionError(ruleId + " is not listed"));
    }

    private static List<String> ids(JsonNode listed) {
        List<String> ids = new ArrayList<>();
        listed.forEach(rule -> ids.add(rule.required("id").asString()));
        return ids;
    }

    /** The rule ids the fixture document itself tags, in priority order, which is what a listing must return. */
    private static List<String> idsTagged(String tag) {
        return DOCUMENT.required("rules").valueStream()
                .filter(rule -> rule.path("tags").valueStream().anyMatch(carried -> tag.equals(carried.asString())))
                .sorted((left, right) -> Integer.compare(left.required("priority").asInt(),
                        right.required("priority").asInt()))
                .map(rule -> rule.required("id").asString())
                .toList();
    }

    private static RuleSet disable(RuleSet ruleSet, String ruleId) {
        List<Rule> rules = ruleSet.rules().stream()
                .map(rule -> rule.id().equals(ruleId)
                        ? new Rule(rule.id(), rule.label(), rule.priority(), false, rule.condition(), rule.actions(),
                                rule.provenance(), rule.tags())
                        : rule)
                .toList();
        return new RuleSet(ruleSet.dslVersion(), ruleSet.id(), ruleSet.name(), ruleSet.language(),
                ruleSet.description(), ruleSet.fields(), ruleSet.defaults(), rules);
    }
}
