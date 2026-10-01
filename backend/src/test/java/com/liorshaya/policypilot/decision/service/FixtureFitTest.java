package com.liorshaya.policypilot.decision.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.decision.service.FixtureFit.Input;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Which case inputs of a version a fixture set leaves unsupplied (Document 2, decide, 2026-10-01): an input is a field
 * that is not derived, a set supplies it when at least one of its cases does, and the set must supply every required
 * input, or every input when the version requires none. The expected lists are read off the committed rule sets and
 * cases-200.json.
 */
class FixtureFitTest {

    private static final RuleSetMapper MAPPER = new RuleSetMapper();

    /** ruleset.v1.json's seven required inputs, in declaration order. */
    private static final List<String> LENDING_REQUIRED = List.of("age", "requested_amount", "term_months",
            "employment_type", "monthly_income", "existing_monthly_debt", "credit_events_24m");

    // ruleset.v1.json and cases-200.json, the set written for it. Expected: none missing
    @Test
    void theLendingCasesFitTheLendingRuleSet() {
        assertThat(FixtureFit.missingInputs(lending(), lendingCases())).isEmpty();
    }

    // arnona-discount-seniors requires age, receives_old_age_pension, apartment_count, area_sqm and submission_date;
    // cases-200.json supplies age alone. Expected: the other four required inputs, in the order declared
    @Test
    void theLendingCasesLeaveFourOfTheSecondDomainsRequiredInputsUnsupplied() {
        assertThat(FixtureFit.missingInputs(arnona(), lendingCases())).containsExactly("receives_old_age_pension",
                "apartment_count", "area_sqm", "submission_date");
    }

    // A set with no cases against ruleset.v1.json, which requires seven of its nine inputs. Expected: the seven, and
    // neither employment_months nor has_guarantor (optional) nor the two derived fields
    @Test
    void aVersionThatRequiresInputsNeedsOnlyThoseSupplied() {
        assertThat(FixtureFit.missingInputs(lending(), List.of())).containsExactlyElementsOf(LENDING_REQUIRED);
    }

    // Document 3, add_field: a change may add an optional case field, which no stored case supplies. Expected: the
    // lending cases still fit the lending rule set with one more optional input
    @Test
    void anOptionalInputAChangeAddsLeavesTheSetFitting() {
        ObjectNode document = Fixtures.lendingV1().deepCopy();
        document.withArray("fields").addObject().put("name", "has_collateral").put("type", "boolean");

        assertThat(FixtureFit.missingInputs(MAPPER.toRuleSet(document), lendingCases())).isEmpty();
    }

    // The new-policy walk of 2026-10-01: a version that marks no field required. Expected: every input is needed, so
    // case 16 alone lacks employment_months and has_guarantor, case 1 supplies the first and case 5 the second
    @Test
    void aVersionThatRequiresNothingNeedsEveryInputSuppliedBySomeCase() {
        RuleSet nothingRequired = MAPPER.toRuleSet(withNothingRequired(Fixtures.lendingV1()));

        assertThat(FixtureFit.missingInputs(nothingRequired, List.of(lendingCase(16))))
                .containsExactly("employment_months", "has_guarantor");
        assertThat(FixtureFit.missingInputs(nothingRequired, List.of(lendingCase(16), lendingCase(1))))
                .containsExactly("has_guarantor");
        assertThat(FixtureFit.missingInputs(nothingRequired,
                List.of(lendingCase(16), lendingCase(1), lendingCase(5)))).isEmpty();
    }

    // The same rule read from a stored document: arnona's expected.ruleset.json. Expected: its seven inputs with their
    // required flags, without the derived discount_rate, and the same inputs as the mapped rule set gives
    @Test
    void aStoredDocumentIsReadAsTheSameInputs() {
        JsonNode document = Fixtures.json(Fixtures.SECOND_DOMAIN + "expected.ruleset.json");

        assertThat(FixtureFit.inputsOf(document)).containsExactly(new Input("age", true),
                new Input("receives_old_age_pension", true), new Input("receives_income_supplement", false),
                new Input("apartment_count", true), new Input("area_sqm", true),
                new Input("municipal_debt", false), new Input("submission_date", true));
        assertThat(FixtureFit.inputsOf(document)).isEqualTo(FixtureFit.inputsOf(arnona()));
    }

    // A document with no fields, as no stored version is but a malformed body could be. Expected: no inputs at all
    @Test
    void aDocumentWithoutFieldsDeclaresNoInputs() {
        assertThat(FixtureFit.inputsOf(Fixtures.lendingV1().deepCopy().without("fields"))).isEmpty();
    }

    private static RuleSet lending() {
        return MAPPER.toRuleSet(Fixtures.lendingV1());
    }

    private static RuleSet arnona() {
        return MAPPER.toRuleSet(Fixtures.json(Fixtures.SECOND_DOMAIN + "expected.ruleset.json"));
    }

    private static ObjectNode withNothingRequired(ObjectNode lending) {
        ObjectNode document = lending.deepCopy();
        ArrayNode fields = document.withArray("fields");
        fields.valueStream().map(ObjectNode.class::cast).forEach(field -> field.remove("required"));
        return document;
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
