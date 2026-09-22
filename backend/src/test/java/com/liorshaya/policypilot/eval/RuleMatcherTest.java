package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Document 4, "Rule matching". The label is {@code fixtures/eval/policies/consumer-lending/expected.ruleset.json}
 * and the cases are that policy's {@code cases.json}; every expected value below is read from those files or is
 * the sentence of Document 4 the test names. A candidate rule set is built by changing the label itself in one
 * stated way, so what each test proves is that one difference, and nothing else, decides the match.
 */
class RuleMatcherTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();
    private static final ObjectNode LABEL =
            (ObjectNode) Fixtures.json("eval/policies/consumer-lending/expected.ruleset.json");
    private static final RuleSet EXPECTED = MAPPER.toRuleSet(LABEL);
    private static final List<ObjectNode> CASES = cases();

    // Document 4: ids, labels, priorities and reasons are not compared. Expected: the label against itself with
    // every rule renamed, reworded and renumbered still matches all 18 of its rules, on their own paragraphs
    @Test
    void theLabelMatchesItselfThroughDifferentIdsLabelsPrioritiesAndReasons() {
        ObjectNode reworded = LABEL.deepCopy();
        int next = 500;
        for (JsonNode rule : reworded.required("rules")) {
            ((ObjectNode) rule).put("id", "X-" + next).put("label", "a label the expert did not write")
                    .put("priority", next);
            next++;
            for (JsonNode action : rule.required("actions")) {
                if (action.has("reason")) {
                    ((ObjectNode) action).put("reason", "wording of its own");
                }
            }
        }

        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, MAPPER.toRuleSet(reworded), CASES);

        assertThat(result.matched()).isEqualTo(EXPECTED.rules().size());
        assertThat(result.recall()).isEqualTo(1.0);
        assertThat(result.precision()).isEqualTo(1.0);
        assertThat(result.provenanceAccuracy()).isEqualTo(1.0);
    }

    // Document 4: "Provenance is correct when the paragraph index matches the expected rule's". Expected: R-330
    // quotes paragraph 7 in the label; moved to paragraph 3 it still matches, and provenance drops by that one
    @Test
    void aRuleQuotingTheWrongParagraphStillMatchesButItsProvenanceIsNotCorrect() {
        RuleSet moved = withRule("R-330", rule -> ((ObjectNode) rule.required("provenance")).put("paragraph", 3));

        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, moved, CASES);

        assertThat(matchOf(result, "R-330").matched()).isTrue();
        assertThat(matchOf(result, "R-330").provenanceCorrect()).isFalse();
        assertThat(result.matched()).isEqualTo(EXPECTED.rules().size());
        assertThat(result.provenanceAccuracy())
                .isEqualTo((EXPECTED.rules().size() - 1.0) / EXPECTED.rules().size());
    }

    // Document 4: the actions must be identical, "outcome and terminal flag". Expected: R-330 refers in the
    // label, so a rule that rejects instead is not it, and the reason names both
    @Test
    void aRuleWithADifferentOutcomeIsNotAMatch() {
        RuleSet rejecting = withRule("R-330",
                rule -> ((ObjectNode) rule.required("actions").get(0)).put("outcome", "reject"));

        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, rejecting, CASES);

        assertThat(matchOf(result, "R-330").matched()).isFalse();
        assertThat(matchOf(result, "R-330").reason()).contains("reject").contains("refer");
    }

    // Document 4: "the same set target and an expression that evaluates equal on the policy's cases". Expected:
    // R-010 rounds the installment to 2 places in the label; without the round the value differs on the cases,
    // so the rule does not match and the reason says on how many
    @Test
    void aSetExpressionThatEvaluatesDifferentlyOnTheCasesIsNotAMatch() {
        RuleSet unrounded = withRule("R-010", rule -> {
            ObjectNode action = (ObjectNode) rule.required("actions").get(0);
            action.set("value", action.required("value").required("args").get(0));
        });

        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, unrounded, CASES);

        assertThat(matchOf(result, "R-010").matched()).isFalse();
        assertThat(matchOf(result, "R-010").reason()).contains("the set value differs on").contains("cases");
    }

    // The other half of the same sentence. Expected: R-020 adds the existing debt to the installment before
    // dividing; adding them the other way round is the same sum on every case, so the rule still matches
    @Test
    void aSetExpressionThatEvaluatesEqualOnTheCasesIsAMatch() {
        RuleSet rewritten = withRule("R-020", rule -> {
            // round(div(add(existing_monthly_debt, monthly_installment), monthly_income), 4)
            ObjectNode sum = (ObjectNode) rule.required("actions").get(0)
                    .required("value").required("args").get(0).required("args").get(0);
            List<JsonNode> terms = new ArrayList<>();
            sum.required("args").forEach(terms::add);
            ArrayNode swapped = sum.putArray("args");
            for (int at = terms.size() - 1; at >= 0; at--) {
                swapped.add(terms.get(at));
            }
        });

        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, rewritten, CASES);

        assertThat(matchOf(result, "R-020").matched()).isTrue();
        assertThat(matchOf(result, "R-020").reason()).startsWith("matched");
    }

    // Document 4 compares fields by name, and the author prompt lets the model invent them. Expected: the rule
    // does not match, and the reason names the field rather than blaming the arithmetic
    @Test
    void aConditionReadingAFieldTheLabelDoesNotDeclareSaysSo() {
        RuleSet renamed = withRule("R-170", rule -> ((ObjectNode) rule.required("condition"))
                .put("field", "net_monthly_income"));

        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, renamed, CASES);

        assertThat(matchOf(result, "R-170").matched()).isFalse();
        assertThat(matchOf(result, "R-170").reason()).contains("net_monthly_income")
                .contains("no field of that name is expected");
    }

    // Document 4: a match is one to one. Expected: the label with R-330 written twice matches R-330 once, the
    // copy is left over, and precision falls because a rule was generated that matched nothing
    @Test
    void aSecondGeneratedRuleThatAlsoFitsIsLeftOver() {
        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, MAPPER.toRuleSet(duplicated("R-331", 7)), CASES);

        assertThat(result.matched()).isEqualTo(EXPECTED.rules().size());
        assertThat(result.unmatchedGenerated()).containsExactly("R-331");
        assertThat(result.precision())
                .isEqualTo((double) EXPECTED.rules().size() / (EXPECTED.rules().size() + 1));
    }

    // Document 4: "Provenance is correct when the paragraph index matches the expected rule's", so where two
    // generated rules fit one expected rule the one on its paragraph is taken; otherwise provenance accuracy
    // would measure the order of the list. Expected: R-330 quotes paragraph 7, so the copy on paragraph 3 is the
    // one left over and provenance stays whole --- and the same holds when the copy is written first
    @Test
    void theCandidateOnTheExpectedParagraphIsPreferred() {
        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, MAPPER.toRuleSet(duplicated("R-331", 3)), CASES);

        assertThat(matchOf(result, "R-330").generatedId()).isEqualTo("R-330");
        assertThat(result.provenanceAccuracy()).isEqualTo(1.0);

        ObjectNode copyFirst = duplicated("R-329", 3);
        List<JsonNode> reversed = new ArrayList<>();
        copyFirst.required("rules").forEach(rule -> reversed.add(0, rule));
        copyFirst.putArray("rules").addAll(reversed);

        RuleMatcher.Result either = RuleMatcher.match(EXPECTED, MAPPER.toRuleSet(copyFirst), CASES);

        assertThat(matchOf(either, "R-330").generatedId()).isEqualTo("R-330");
        assertThat(either.provenanceAccuracy()).isEqualTo(1.0);
    }

    // Document 4: "each expected rule can match at most one generated rule, and the reverse". A label that says
    // the same thing twice is the input that tests the second half. Expected: one generated R-330 answers one of
    // the two, not both, so recall counts 18 of 19 rather than being inflated to 19
    @Test
    void oneGeneratedRuleIsNotCountedForTwoExpectedRules() {
        RuleSet twiceExpected = MAPPER.toRuleSet(duplicated("R-331", 7));

        RuleMatcher.Result result = RuleMatcher.match(twiceExpected, EXPECTED, CASES);

        assertThat(result.matched()).isEqualTo(EXPECTED.rules().size());
        assertThat(result.recall())
                .isEqualTo((double) EXPECTED.rules().size() / (EXPECTED.rules().size() + 1));
    }

    // Document 4: each expected rule takes at most one generated rule. Expected: with R-330 missing, 17 of the 18
    // match and recall is 17 of 18; the unmatched one says no rule carried its action
    @Test
    void anExpectedRuleNoGeneratedRuleCarriesIsCountedAgainstRecall() {
        ObjectNode fewer = LABEL.deepCopy();
        List<JsonNode> kept = new ArrayList<>();
        fewer.required("rules").forEach(rule -> {
            if (!"R-330".equals(rule.required("id").asString())) {
                kept.add(rule);
            }
        });
        fewer.putArray("rules").addAll(kept);

        RuleMatcher.Result result = RuleMatcher.match(EXPECTED, MAPPER.toRuleSet(fewer), CASES);

        assertThat(result.matched()).isEqualTo(EXPECTED.rules().size() - 1);
        assertThat(result.recall()).isEqualTo((EXPECTED.rules().size() - 1.0) / EXPECTED.rules().size());
        assertThat(matchOf(result, "R-330").matched()).isFalse();
    }

    private static RuleMatcher.Match matchOf(RuleMatcher.Result result, String expectedId) {
        return result.matches().stream().filter(match -> match.expectedId().equals(expectedId))
                .findFirst().orElseThrow();
    }

    /** The label with one of its rules changed in the way the test states, read back as a rule set. */
    private static RuleSet withRule(String ruleId, java.util.function.Consumer<JsonNode> change) {
        ObjectNode document = LABEL.deepCopy();
        change.accept(ruleOf(document, ruleId));
        return MAPPER.toRuleSet(document);
    }

    /** The label with R-330 written a second time under another id, quoting the paragraph given. */
    private static ObjectNode duplicated(String secondId, int paragraph) {
        ObjectNode twice = LABEL.deepCopy();
        ObjectNode copy = ruleOf(twice, "R-330").deepCopy();
        copy.put("id", secondId).put("priority", Integer.parseInt(secondId.substring(2)));
        ((ObjectNode) copy.required("provenance")).put("paragraph", paragraph);
        twice.withArray("rules").add(copy);
        return twice;
    }

    private static ObjectNode ruleOf(ObjectNode document, String ruleId) {
        return (ObjectNode) document.required("rules").valueStream()
                .filter(rule -> ruleId.equals(rule.path("id").asString("")))
                .findFirst().orElseThrow();
    }

    private static List<ObjectNode> cases() {
        List<ObjectNode> inputs = new ArrayList<>();
        Fixtures.json("eval/policies/consumer-lending/cases.json").required("cases")
                .forEach(labelled -> inputs.add((ObjectNode) labelled.required("input")));
        return inputs;
    }
}
