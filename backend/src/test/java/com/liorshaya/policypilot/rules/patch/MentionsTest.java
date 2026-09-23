package com.liorshaya.policypilot.rules.patch;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.FieldRef;
import com.liorshaya.policypilot.rules.model.NumberLiteral;
import com.liorshaya.policypilot.rules.model.Operator;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.RuleSetBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * What a change request names (Document 3, Patch validation: "by its id or by a value its condition tests: a number,
 * or a word such as an enum value"). The rules are the fixtures' own, and so are the requests.
 */
@Requirement("FR-17")
class MentionsTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();
    private static final RuleSet LENDING = MAPPER.toRuleSet(Fixtures.lendingV1());
    private static final RuleSet SCHOLARSHIP =
            MAPPER.toRuleSet(Fixtures.json("eval/policies/scholarship-merit/expected.ruleset.json"));

    // The id however it is written: R-140, r-140, R140, and inside Hebrew text
    @Test
    void aRuleIsNamedByItsId() {
        assertThat(Mentions.of("הסר את R-140").names(rule(LENDING, "R-140"))).isTrue();
        assertThat(Mentions.of("remove r140 please").names(rule(LENDING, "R-140"))).isTrue();
        assertThat(Mentions.of("remove R-1400").names(rule(LENDING, "R-140"))).isFalse();
    }

    // CR-5 and its expected rationale in changes.json: the request cancels "the 36 credits requirement", R-140 tests
    // credits < 36, and R-130 (credits < 40) "was not mentioned and stays"
    @Test
    void cr5NamesTheRuleThatTests36CreditsAndNotTheOneThatTests40() {
        Mentions request = Mentions.of(ChangeRequests.labeled("CR-5").required("text").asString());

        assertThat(request.names(rule(SCHOLARSHIP, "R-140"))).isTrue();
        assertThat(request.names(rule(SCHOLARSHIP, "R-130"))).isFalse();
    }

    // The scripted request writes 9,000: R-410's band is [8000, 9000], R-170 tests 8000
    @Test
    void aNumberIsReadWithItsThousandsSeparator() {
        Mentions request = Mentions.of(ChangeRequests.scripted());

        assertThat(request.names(rule(LENDING, "R-410"))).isTrue();
        assertThat(request.names(rule(LENDING, "R-170"))).isFalse();
    }

    // R-116 tests age >= 78 - term_months / 12: a number inside an operand's expression is one it tests
    @Test
    void aNumberInsideAnExpressionIsTested() {
        assertThat(Mentions.of("drop the rule on the age of 78").names(rule(LENDING, "R-116"))).isTrue();
        // 12 is inside the division within the subtraction
        assertThat(Mentions.of("stop dividing the term by 12").names(rule(LENDING, "R-116"))).isTrue();
    }

    // R-140 tests employment_type eq unemployed; R-310 tests employment_type in [salaried, self_employed]
    @Test
    void aRuleIsNamedByAWordItsConditionTests() {
        assertThat(Mentions.of("stop rejecting Unemployed applicants").names(rule(LENDING, "R-140"))).isTrue();
        assertThat(Mentions.of("stop referring self_employed applicants").names(rule(LENDING, "R-310"))).isTrue();
        assertThat(Mentions.of("stop rejecting the unemployment benefit").names(rule(LENDING, "R-140"))).isFalse();
    }

    // R-330 tests credit_events_24m eq 1 and has_guarantor eq false: a boolean is no word a request names a rule by
    @Test
    void aBooleanItTestsDoesNotNameARule() {
        assertThat(Mentions.of("this is false").names(rule(LENDING, "R-330"))).isFalse();
        assertThat(Mentions.of("one event, 1").names(rule(LENDING, "R-330"))).isTrue();
    }

    // The digits of an id are not also a number: "R-140" does not name a rule that tests 140
    @Test
    void theDigitsOfARuleIdAreNotANumber() {
        ObjectNode testing140 = RuleSetBuilder.lendingV1()
                .rule("R-100", r -> ((ObjectNode) r.required("condition")).put("value", 140)).build();

        assertThat(Mentions.of("remove R-140").names(rule(MAPPER.toRuleSet(testing140), "R-100"))).isFalse();
    }

    // A value inside any of the combinators names the rule: under any and under not
    @Test
    void aValueUnderAnyOrNotNamesTheRule() {
        Rule underAny = new Rule("R-125", "סכום או תקופה חריגים", 125, null, new Condition.Any(List.of(
                new Condition.Comparison("requested_amount", Operator.GT, new NumberLiteral(new BigDecimal(150000))),
                new Condition.Not(new Condition.Comparison("term_months", Operator.LTE,
                        new NumberLiteral(new BigDecimal(84)))))),
                rule(LENDING, "R-120").actions(), rule(LENDING, "R-120").provenance(), null);

        assertThat(Mentions.of("drop the 150,000 cap").names(underAny)).isTrue();
        assertThat(Mentions.of("drop the 84 months cap").names(underAny)).isTrue();
        assertThat(Mentions.of("drop the 96 months cap").names(underAny)).isFalse();
    }

    // A comparison with another field names no value, and a rule whose condition is always names none either
    @Test
    void aFieldOperandAndAnAlwaysConditionNameNothing() {
        Rule byField = new Rule("R-105", "גיל מול גיל הפרישה", 105, null,
                new Condition.Comparison("age", Operator.GT, new FieldRef("retirement_age")),
                rule(LENDING, "R-100").actions(), rule(LENDING, "R-100").provenance(), null);

        assertThat(Mentions.of("age retirement_age 105").names(byField)).isFalse();
        assertThat(Mentions.of("always true 900").names(rule(LENDING, "R-900"))).isFalse();
    }

    // The fields the request names by their own names, in the rule set's order
    @Test
    void aFieldIsNamedByItsSnakeCaseName() {
        assertThat(Mentions.of("raise MONTHLY_INCOME and existing_monthly_debt").fields(LENDING))
                .containsExactly("monthly_income", "existing_monthly_debt");
        assertThat(Mentions.of("raise the monthly_income_bonus").fields(LENDING)).isEmpty();
    }

    // The ids the request writes, as R- and their digits
    @Test
    void theRuleIdsAreReadAsTheRuleSetWritesThem() {
        assertThat(Mentions.of("merge r170 into R-410 and R 200").ruleIds()).containsExactly("R-170", "R-410");
        assertThat(Mentions.of("ל-R-170 ולR-410").ruleIds()).containsExactly("R-170", "R-410");
    }

    private static Rule rule(RuleSet ruleSet, String id) {
        return ruleSet.rules().stream().filter(rule -> rule.id().equals(id)).findFirst().orElseThrow();
    }
}
