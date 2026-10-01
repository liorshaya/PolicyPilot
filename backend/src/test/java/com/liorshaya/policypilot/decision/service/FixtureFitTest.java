package com.liorshaya.policypilot.decision.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Which case inputs of a version a fixture set leaves unsupplied (Document 2, decide, 2026-10-01): an input is a field
 * that is not derived, and a set supplies it when at least one of its cases does. The expected lists are read off the
 * committed rule sets and cases-200.json.
 */
class FixtureFitTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();

    // ruleset.v1.json declares nine inputs, and cases-200.json is the set it was written for. Expected: none missing
    @Test
    void theLendingCasesSupplyEveryInputOfTheLendingRuleSet() {
        assertThat(FixtureFit.missingInputs(lending(), lendingCases())).isEmpty();
    }

    // arnona-discount-seniors declares seven inputs; age is the only one cases-200.json supplies. Expected: the other
    // six, in the order the rule set declares them
    @Test
    void theLendingCasesLeaveSixOfTheSecondDomainsSevenInputsUnsupplied() {
        RuleSet arnona = MAPPER.toRuleSet(Fixtures.json(Fixtures.SECOND_DOMAIN + "expected.ruleset.json"));

        assertThat(FixtureFit.missingInputs(arnona, lendingCases())).containsExactly("receives_old_age_pension",
                "receives_income_supplement", "apartment_count", "area_sqm", "municipal_debt", "submission_date");
    }

    // cases-200.json: case 16 omits employment_months and has_guarantor, case 1 supplies the first, case 5 the second.
    // Expected: an input one case of the set supplies counts as supplied, so only the cases' union decides
    @Test
    void anInputCountsAsSuppliedWhenOneCaseOfTheSetSuppliesIt() {
        assertThat(FixtureFit.missingInputs(lending(), List.of(lendingCase(16))))
                .containsExactly("employment_months", "has_guarantor");
        assertThat(FixtureFit.missingInputs(lending(), List.of(lendingCase(16), lendingCase(1))))
                .containsExactly("has_guarantor");
        assertThat(FixtureFit.missingInputs(lending(), List.of(lendingCase(16), lendingCase(1), lendingCase(5))))
                .isEmpty();
    }

    // ruleset.v1.json derives monthly_installment and debt_to_income, which no case may supply (Document 3, step 1).
    // Expected: a derived field is never reported as missing, even for a set with no cases at all
    @Test
    void aDerivedFieldIsNeverAnInputTheSetMustSupply() {
        assertThat(FixtureFit.missingInputs(lending(), List.of())).containsExactly("age", "requested_amount",
                "term_months", "employment_type", "employment_months", "monthly_income", "existing_monthly_debt",
                "credit_events_24m", "has_guarantor");
    }

    // ruleset.v1.json as a document: its nine inputs in declaration order, without monthly_installment and
    // debt_to_income, which it derives. Expected: the names, read from the document's fields as they stand
    @Test
    void aDocumentsInputsAreItsFieldsThatAreNotDerivedInDeclarationOrder() {
        assertThat(FixtureFit.inputsOf(Fixtures.lendingV1())).containsExactly("age", "requested_amount",
                "term_months", "employment_type", "employment_months", "monthly_income", "existing_monthly_debt",
                "credit_events_24m", "has_guarantor");
    }

    // A document with no fields, as no stored version is but a malformed body could be. Expected: no inputs at all
    @Test
    void aDocumentWithoutFieldsDeclaresNoInputs() {
        assertThat(FixtureFit.inputsOf(Fixtures.lendingV1().deepCopy().without("fields"))).isEmpty();
    }

    private static RuleSet lending() {
        return MAPPER.toRuleSet(Fixtures.lendingV1());
    }

    private static List<JsonNode> lendingCases() {
        return Fixtures.json("policies/consumer-lending/cases-200.json").required("cases").valueStream()
                .map(fixture -> fixture.required("input")).toList();
    }

    private static JsonNode lendingCase(int id) {
        return Fixtures.json("policies/consumer-lending/cases-200.json").required("cases").valueStream()
                .filter(fixture -> fixture.required("id").asInt() == id).findFirst().orElseThrow()
                .required("input");
    }
}
