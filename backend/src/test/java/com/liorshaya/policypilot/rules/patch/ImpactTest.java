package com.liorshaya.policypilot.rules.patch;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The rules a change can affect (Document 4, Prompt 5, Candidate selection), on the lending rule set. The expected
 * candidates of the scripted request are the fixture's (change-request-1.json: candidates and candidateFields); the
 * others follow from the rule set's field references, read off ruleset.v1.json.
 */
@Requirement("FR-17")
class ImpactTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());
    private static final JsonNode SCRIPTED =
            Fixtures.json("policies/consumer-lending/change-request-1.json").required("expected");

    // R-170 tests monthly_income; R-410 and R-020 read it; R-020 derives debt_to_income, which R-200 and R-320 test
    @Test
    void theScriptedSeedYieldsTheExpectedFiveAndTheIncomeField() {
        Impact impact = Impact.of(LENDING, Set.of("R-170"), Set.of());

        assertThat(impact.ruleIds()).containsExactlyInAnyOrderElementsOf(strings(SCRIPTED.required("candidates")));
        assertThat(impact.fields()).containsExactlyElementsOf(strings(SCRIPTED.required("candidateFields")));
    }

    // Whichever of the income rules is closest, the same five: each tests monthly_income
    @Test
    void eachIncomeRuleAsASeedYieldsTheSameFive() {
        for (String seed : List.of("R-410", "R-020")) {
            assertThat(Impact.of(LENDING, Set.of(seed), Set.of()).ruleIds()).as(seed)
                    .containsExactlyInAnyOrderElementsOf(ChangeRequests.expectedCandidates("CR-1"));
        }
    }

    // R-900 is {"always": true}: it tests no field, so it is its own only candidate
    @Test
    void aSeedThatTestsNoFieldIsACandidateAlone() {
        Impact impact = Impact.of(LENDING, Set.of("R-900"), Set.of());

        assertThat(impact.ruleIds()).containsExactly("R-900");
        assertThat(impact.fields()).isEmpty();
    }

    // R-010 derives monthly_installment, R-020 reads it and derives debt_to_income, R-200 and R-320 test that
    @Test
    void aDerivationSeedCarriesTheRulesDownstreamOfIt() {
        assertThat(Impact.of(LENDING, Set.of("R-010"), Set.of()).ruleIds())
                .containsExactly("R-010", "R-020", "R-200", "R-320");
    }

    // A field the request names is a request field: term_months is read by R-010, R-116 and R-130, and R-010's
    // installment flows on to R-020, R-200 and R-320
    @Test
    void aNamedFieldIsARequestField() {
        Impact impact = Impact.of(LENDING, Set.of(), Set.of("term_months"));

        assertThat(impact.ruleIds()).containsExactly("R-010", "R-020", "R-116", "R-130", "R-200", "R-320");
        assertThat(impact.fields()).containsExactly("term_months");
    }

    // The rules array in another order: R-200 and R-320, which test debt_to_income, written before R-020, which
    // derives it. Where a rule is written does not change the candidates
    @Test
    void aReaderWrittenBeforeItsDerivationIsStillACandidate() {
        ObjectNode reordered = Fixtures.lendingV1();
        ArrayNode rules = (ArrayNode) reordered.required("rules");
        Set<String> ratioReaders = Set.of("R-200", "R-320");
        Map<Boolean, List<JsonNode>> readsTheRatio = rules.valueStream()
                .collect(Collectors.partitioningBy(rule -> ratioReaders.contains(rule.required("id").asString())));
        rules.removeAll().addAll(readsTheRatio.get(true)).addAll(readsTheRatio.get(false));

        assertThat(Impact.of(new RuleSetMapper().toRuleSet(reordered), Set.of("R-170"), Set.of()).ruleIds())
                .containsExactlyInAnyOrderElementsOf(ChangeRequests.expectedCandidates("CR-1"));
    }

    // Evaluation order is priority, then id; seeds and names the rule set does not have are ignored
    @Test
    void candidatesAreInEvaluationOrderAndUnknownNamesAreIgnored() {
        Impact impact = Impact.of(LENDING, List.of("R-410", "R-999", "R-170"), List.of("bonus_income"));

        assertThat(impact.ruleIds()).containsExactly("R-020", "R-170", "R-200", "R-320", "R-410");
        assertThat(impact.fields()).containsExactly("monthly_income");
    }

    private static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
