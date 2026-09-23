package com.liorshaya.policypilot.rules.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.Fixtures;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Which fields a rule reads and writes, on the rules of the lending rule set as {@code ruleset.v1.json} writes them:
 * the expected sets are read off that file, one field reference at a time.
 */
class FieldReferencesTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());

    // R-020: when monthly_income > 0, debt_to_income = (existing_monthly_debt + monthly_installment) / monthly_income
    @Test
    void theDebtToIncomeDerivationReadsThreeFieldsAndWritesOne() {
        Rule r020 = rule("R-020");

        assertThat(FieldReferences.inCondition(r020.condition())).containsExactly("monthly_income");
        assertThat(FieldReferences.readBy(r020))
                .containsExactly("existing_monthly_debt", "monthly_income", "monthly_installment");
        assertThat(FieldReferences.setBy(r020)).containsExactly("debt_to_income");
    }

    // R-170: monthly_income lt 8000 and a decision; everything it reads is in its condition
    @Test
    void aRuleThatReadsOnlyInItsConditionReadsWhatItCompares() {
        assertThat(FieldReferences.readBy(rule("R-170"))).containsExactly("monthly_income");
    }

    // R-116: employment_type eq retired and age gte 78 - term_months / 12: the operand's expression reads term_months
    @Test
    void anOperandExpressionIsReadWithItsComparison() {
        assertThat(FieldReferences.inCondition(rule("R-116").condition()))
                .containsExactly("age", "employment_type", "term_months");
    }

    // R-900: {"always": true} and a decision, no set action
    @Test
    void anAlwaysRuleReadsAndWritesNothing() {
        assertThat(FieldReferences.readBy(rule("R-900"))).isEmpty();
        assertThat(FieldReferences.setBy(rule("R-900"))).isEmpty();
    }

    // any, not, a field compared with another field, a literal list and a present check, built by hand
    @Test
    void everyKindOfConditionIsWalked() {
        Condition condition = new Condition.Any(List.of(
                new Condition.Not(new Condition.Comparison("age", Operator.GT, new FieldRef("retirement_age"))),
                new Condition.Comparison("employment_type", Operator.IN,
                        new LiteralList(List.of(new StringLiteral("retired")))),
                new Condition.Comparison("employment_months", Operator.PRESENT, null)));

        assertThat(FieldReferences.inCondition(condition))
                .containsExactly("age", "employment_months", "employment_type", "retirement_age");
    }

    // a set action's value: a field it copies, or a literal it writes
    @Test
    void aValueReadsTheFieldItRefersToAndALiteralReadsNone() {
        assertThat(FieldReferences.inValue(new FieldRef("monthly_income"))).containsExactly("monthly_income");
        assertThat(FieldReferences.inValue(new NumberLiteral(BigDecimal.TEN))).isEmpty();
    }

    private static Rule rule(String id) {
        return LENDING.rules().stream().filter(rule -> rule.id().equals(id)).findFirst().orElseThrow();
    }
}
