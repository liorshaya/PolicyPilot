package com.liorshaya.policypilot.rules.validation;

import static com.liorshaya.policypilot.rules.validation.ValidationCode.CANDIDATE_NEVER_WINS;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.DERIVED_CYCLE;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.DERIVED_NEVER_SET;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.DERIVED_ORDER;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.DIVISION_BY_UNGUARDED_FIELD;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.FIELD_UNUSED;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.MISSING_FIELD_UNDER_NOT;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.NO_TERMINAL_APPROVE;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.PRIORITY_BAND_UNUSUAL;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.REFER_PRECEDES_REJECT;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.RULE_OVERLAP_CONFLICT;
import static com.liorshaya.policypilot.rules.validation.ValidationCode.RULE_UNREACHABLE;
import static com.liorshaya.policypilot.rules.validation.Validations.invalidFixture;
import static com.liorshaya.policypilot.rules.validation.Validations.invalidRuleSet;
import static com.liorshaya.policypilot.rules.validation.Validations.publish;
import static com.liorshaya.policypilot.rules.validation.Validations.withoutPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RuleSetBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Layer 3 (Document 3, Static Validation). Every expected list is the one {@code reference_check.py} returns for
 * the same document, code for code and in the same order; the pointers name the rule, field or node concerned.
 */
class StructuralChecksTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ANALYST = "\"provenance\": {\"kind\": \"analyst\", \"note\": \"added for a test\","
            + " \"actor\": \"test\"}";
    private static final String DECIDE_APPROVE = " \"actions\": [{\"type\": \"decide\", \"outcome\": \"approve\","
            + " \"terminal\": true, \"reason\": \"approved\"}], " + ANALYST;
    private static final String DECIDE_REJECT = " \"actions\": [{\"type\": \"decide\", \"outcome\": \"reject\","
            + " \"terminal\": true, \"reason\": \"rejected\"}], " + ANALYST;

    // ------------------------------------------------------------------ derived fields

    @Test
    void derivedCycleReportedTogetherWithDerivedOrder() {
        assertFindings(invalidFixture("DERIVED_CYCLE"),
                tuple(DERIVED_CYCLE, "/rules/0/actions/0"), tuple(DERIVED_CYCLE, "/rules/1/actions/0"),
                tuple(DERIVED_ORDER, "/rules/0"), tuple(FIELD_UNUSED, "/fields/0"));
    }

    @Test
    void readModifyWriteOfTheSameFieldIsNotACycle() {
        JsonNode document = RuleSetBuilder.lendingV1()
                .rule("R-420", r -> actions(r).add(json("{\"type\": \"set\", \"field\": \"debt_to_income\","
                        + " \"value\": {\"fn\": \"max\", \"args\": [{\"field\": \"debt_to_income\"}, 0]}}")))
                .build();

        assertThat(publish(document)).isEmpty();
    }

    @Test
    void derivedOrderReportedWhenTheReaderRunsFirst() {
        assertFindings(invalidFixture("DERIVED_ORDER"), tuple(DERIVED_ORDER, "/rules/0"),
                tuple(PRIORITY_BAND_UNUSUAL, "/rules/0/priority"), tuple(FIELD_UNUSED, "/fields/0"));
    }

    @Test
    void derivedOrderReportedWhenReaderAndSetterShareAPriority() {
        JsonNode document = RuleSetBuilder.lendingV1().rule("R-200", r -> r.put("priority", 20)).build();

        assertFindings(publish(document), tuple(DERIVED_ORDER, "/rules/12"),
                tuple(PRIORITY_BAND_UNUSUAL, "/rules/12/priority"));
    }

    @Test
    void derivedNeverSetReportedForAFieldNoRuleSets() {
        assertFindings(invalidFixture("DERIVED_NEVER_SET"), tuple(DERIVED_NEVER_SET, "/fields/1"));
    }

    @Test
    void derivedFieldSetOnlyByADisabledRuleIsNeverSet() {
        JsonNode document = RuleSetBuilder.lendingV1().rule("R-010", r -> r.put("enabled", false)).build();

        assertFindings(publish(document), tuple(DERIVED_NEVER_SET, "/fields/9"));
    }

    // ------------------------------------------------------------------ unused fields

    @Test
    void fieldUnusedReportedForAFieldNoRuleTouches() {
        assertFindings(invalidFixture("FIELD_UNUSED"), tuple(FIELD_UNUSED, "/fields/1"));
        // a warning alone does not block publishing
        ValidationResult result = Validations.VALIDATOR.validate(
                invalidRuleSet("FIELD_UNUSED"), ValidationContext.PUBLISH, List.of(), Set.of());
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void fieldReadOnlyInsideAnExpressionIsUsed() {
        // existing_monthly_debt is read only inside R-020's expression
        assertThat(publish(Fixtures.lendingV1())).isEmpty();
    }

    @Test
    void fieldReadOnlyByADisabledRuleIsUnused() {
        JsonNode document = RuleSetBuilder.lendingV1().rule("R-330", r -> r.put("enabled", false)).build();

        assertFindings(publish(document), tuple(FIELD_UNUSED, "/fields/8"));
    }

    // ------------------------------------------------------------------ reachability and overlap

    @Test
    void ruleUnreachableReportedForANarrowerRuleAfterAWiderOne() {
        assertFindings(invalidFixture("RULE_UNREACHABLE"), tuple(RULE_UNREACHABLE, "/rules/1"));
    }

    @Test
    void ruleUnreachableReportedAfterAnAlwaysTerminalAndForAnEnumSubset() {
        JsonNode afterAlways = withRule("{\"id\": \"R-950\", \"label\": \"approve older applicants\", \"priority\": 950,"
                + " \"condition\": {\"field\": \"age\", \"op\": \"gt\", \"value\": 30}," + DECIDE_APPROVE + "}");
        JsonNode enumSubset = withRule("{\"id\": \"R-145\", \"label\": \"reject the unemployed again\", \"priority\": 145,"
                + " \"condition\": {\"field\": \"employment_type\", \"op\": \"in\", \"value\": [\"unemployed\"]},"
                + DECIDE_REJECT + "}");

        assertFindings(publish(afterAlways), tuple(RULE_UNREACHABLE, "/rules/20"));
        assertFindings(publish(enumSubset), tuple(RULE_UNREACHABLE, "/rules/20"));
        assertThat(publish(enumSubset).getFirst().ruleIds()).containsExactly("R-145", "R-140");
    }

    @Test
    void widerRuleAfterANarrowerOneIsReachable() {
        JsonNode document = withRule("{\"id\": \"R-105\", \"label\": \"reject under 25\", \"priority\": 105,"
                + " \"condition\": {\"field\": \"age\", \"op\": \"lt\", \"value\": 25}," + DECIDE_REJECT + "}");

        assertThat(publish(document)).isEmpty();
    }

    @Test
    void ruleOverlapConflictReportedForOverlappingNonTerminalRanges() {
        assertFindings(invalidFixture("RULE_OVERLAP_CONFLICT"), tuple(RULE_OVERLAP_CONFLICT, "/rules/1"));
    }

    @Test
    void terminalFirstRuleResolvesTheOverlap() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("RULE_OVERLAP_CONFLICT"))
                .rule("R-300", r -> edit(r, "/actions/0").put("terminal", true))
                .build();

        assertThat(withoutPolicy(document)).isEmpty();
    }

    @Test
    void touchingHalfOpenRangesDoNotOverlap() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("RULE_OVERLAP_CONFLICT"))
                .rule("R-300", r -> r.set("condition", json("{\"field\": \"x\", \"op\": \"lt\", \"value\": 10}")))
                .rule("R-900", r -> r.set("condition", json("{\"field\": \"x\", \"op\": \"gte\", \"value\": 10}")))
                .build();

        assertThat(withoutPolicy(document)).isEmpty();
    }

    // ------------------------------------------------------------------ precedence and candidates

    @Test
    void referPrecedesRejectReportedForTheFixture() {
        assertFindings(invalidFixture("REFER_PRECEDES_REJECT"), tuple(PRIORITY_BAND_UNUSUAL, "/rules/0/priority"),
                tuple(RULE_UNREACHABLE, "/rules/1"), tuple(REFER_PRECEDES_REJECT, "/rules/0/priority"));
    }

    @Test
    void referPrecedesRejectReportedForLendingWithR320At210() {
        JsonNode document = RuleSetBuilder.lendingV1().rule("R-320", r -> r.put("priority", 210)).build();

        assertFindings(publish(document), tuple(PRIORITY_BAND_UNUSUAL, "/rules/15/priority"),
                tuple(REFER_PRECEDES_REJECT, "/rules/15/priority"));
    }

    @Test
    void precedenceIntendedTagSilencesReferPrecedesReject() {
        JsonNode document = RuleSetBuilder.lendingV1()
                .rule("R-320", r -> {
                    r.put("priority", 210);
                    ((ArrayNode) r.get("tags")).add("precedence_intended");
                })
                .build();

        assertFindings(publish(document), tuple(PRIORITY_BAND_UNUSUAL, "/rules/15/priority"));
    }

    @Test
    void candidateNeverWinsReportedForTheFixture() {
        assertFindings(invalidFixture("CANDIDATE_NEVER_WINS"), tuple(CANDIDATE_NEVER_WINS, "/rules/0"));
    }

    @Test
    void candidateNeverWinsReportedForLendingWithR320NonTerminal() {
        JsonNode document = RuleSetBuilder.lendingV1()
                .rule("R-320", r -> edit(r, "/actions/0").put("terminal", false))
                .build();

        assertFindings(publish(document), tuple(CANDIDATE_NEVER_WINS, "/rules/15"));
    }

    @Test
    void candidateFollowedOnlyByConditionalTerminalsCanWin() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("CANDIDATE_NEVER_WINS"))
                .rule("R-900", r -> r.set("condition", json("{\"field\": \"x\", \"op\": \"lt\", \"value\": 0}")))
                .build();

        assertThat(withoutPolicy(document)).isEmpty();
    }

    // ------------------------------------------------------------------ division

    @Test
    void divisionByUnguardedFieldReportedForTheFixture() {
        assertFindings(invalidFixture("DIVISION_BY_UNGUARDED_FIELD"),
                tuple(DIVISION_BY_UNGUARDED_FIELD, "/rules/0/actions/0/value"));
    }

    @Test
    void divisionByUnguardedFieldReportedForLendingR020WithoutItsGuard() {
        JsonNode document = RuleSetBuilder.lendingV1()
                .rule("R-020", r -> r.set("condition", json("{\"always\": true}")))
                .build();

        assertFindings(publish(document), tuple(DIVISION_BY_UNGUARDED_FIELD, "/rules/1/actions/0/value/args/0"));
    }

    @Test
    void positiveGuardOrExclusiveMinimumMakesADivisorSafe() {
        JsonNode exclusiveMinimum = RuleSetBuilder.from(invalidRuleSet("DIVISION_BY_UNGUARDED_FIELD"))
                .field("x", f -> f.remove("minimum")).field("x", f -> f.put("exclusiveMinimum", 0)).build();
        JsonNode minimumOne = RuleSetBuilder.from(invalidRuleSet("DIVISION_BY_UNGUARDED_FIELD"))
                .field("x", f -> f.put("minimum", 1)).build();
        JsonNode guardGteOne = RuleSetBuilder.from(invalidRuleSet("DIVISION_BY_UNGUARDED_FIELD"))
                .rule("R-10", r -> r.set("condition", json("{\"field\": \"x\", \"op\": \"gte\", \"value\": 1}")))
                .build();

        assertThat(withoutPolicy(exclusiveMinimum)).isEmpty();
        assertThat(withoutPolicy(minimumOne)).isEmpty();
        assertThat(withoutPolicy(guardGteOne)).isEmpty();
    }

    @Test
    void gteZeroGuardDoesNotExcludeZero() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("DIVISION_BY_UNGUARDED_FIELD"))
                .rule("R-10", r -> r.set("condition", json("{\"field\": \"x\", \"op\": \"gte\", \"value\": 0}")))
                .build();

        assertFindings(withoutPolicy(document), tuple(DIVISION_BY_UNGUARDED_FIELD, "/rules/0/actions/0/value"));
    }

    // ------------------------------------------------------------------ missing fields under not

    @Test
    void missingFieldUnderNotReportedForTheFixture() {
        assertFindings(invalidFixture("MISSING_FIELD_UNDER_NOT"),
                tuple(MISSING_FIELD_UNDER_NOT, "/rules/0/condition/not"), tuple(FIELD_UNUSED, "/fields/0"));
    }

    @Test
    void presentGuardDefaultOrRequiredSilencesMissingFieldUnderNot() {
        JsonNode guarded = RuleSetBuilder.from(invalidRuleSet("MISSING_FIELD_UNDER_NOT"))
                .rule("R-100", r -> r.set("condition", json("{\"all\": [{\"field\": \"y\", \"op\": \"present\"},"
                        + " {\"not\": {\"field\": \"y\", \"op\": \"gt\", \"value\": 5}}]}")))
                .build();
        JsonNode defaulted = RuleSetBuilder.from(invalidRuleSet("MISSING_FIELD_UNDER_NOT"))
                .field("y", f -> f.put("default", 0))
                .build();

        assertFindings(withoutPolicy(guarded), tuple(FIELD_UNUSED, "/fields/0"));
        assertFindings(withoutPolicy(defaulted), tuple(FIELD_UNUSED, "/fields/0"));
        // lending R-120 and R-130 put required fields under not
        assertThat(publish(Fixtures.lendingV1())).isEmpty();
    }

    // ------------------------------------------------------------------ bands and approval

    @Test
    void priorityBandUnusualReportedForAnApproveInTheGateBand() {
        assertFindings(invalidFixture("PRIORITY_BAND_UNUSUAL"), tuple(PRIORITY_BAND_UNUSUAL, "/rules/0/priority"));
    }

    @Test
    void priorityOutsideEveryBandIsNotChecked() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("PRIORITY_BAND_UNUSUAL"))
                .rule("R-150", r -> r.put("priority", 500))
                .build();

        assertThat(withoutPolicy(document)).isEmpty();
    }

    @Test
    void noTerminalApproveReportedWhenNothingApproves() {
        assertFindings(invalidFixture("NO_TERMINAL_APPROVE"), tuple(NO_TERMINAL_APPROVE, "/rules"));
    }

    @Test
    void nonTerminalApproveCountsAsAbleToApprove() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("NO_TERMINAL_APPROVE"))
                .rule("R-110", r -> edit(r, "/actions/0").put("outcome", "approve").put("terminal", false))
                .build();

        assertFindings(withoutPolicy(document), tuple(PRIORITY_BAND_UNUSUAL, "/rules/1/priority"));
    }

    // ------------------------------------------------------------------ edges, each against the reference's output

    @Test
    void aDiamondOfDerivationsIsNotACycle() {
        // d = x; b = d * 2; c = d + 1; a = b + c: d is reached twice, never itself
        JsonNode document = json("""
                {"dslVersion": "1.0", "id": "diamond", "name": "Diamond", "language": "en",
                 "fields": [{"name": "x", "type": "number", "required": true},
                            {"name": "a", "type": "number", "derived": true},
                            {"name": "b", "type": "number", "derived": true},
                            {"name": "c", "type": "number", "derived": true},
                            {"name": "d", "type": "number", "derived": true}],
                 "defaults": {"outcome": "refer", "reason": "undecided"},
                 "rules": [
                   {"id": "R-10", "label": "set d", "priority": 10, "condition": {"always": true},
                    "actions": [{"type": "set", "field": "d", "value": {"field": "x"}}], %1$s},
                   {"id": "R-20", "label": "set b", "priority": 20, "condition": {"always": true},
                    "actions": [{"type": "set", "field": "b", "value": {"fn": "mul", "args": [{"field": "d"}, 2]}}], %1$s},
                   {"id": "R-30", "label": "set c", "priority": 30, "condition": {"always": true},
                    "actions": [{"type": "set", "field": "c", "value": {"fn": "add", "args": [{"field": "d"}, 1]}}], %1$s},
                   {"id": "R-40", "label": "set a", "priority": 40, "condition": {"always": true},
                    "actions": [{"type": "set", "field": "a",
                                 "value": {"fn": "add", "args": [{"field": "b"}, {"field": "c"}]}}], %1$s},
                   {"id": "R-100", "label": "reject", "priority": 100,
                    "condition": {"field": "a", "op": "lt", "value": 0}, %2$s},
                   {"id": "R-900", "label": "approve", "priority": 900, "condition": {"always": true}, %3$s}]}
                """.formatted(ANALYST, DECIDE_REJECT, DECIDE_APPROVE));

        assertThat(withoutPolicy(document)).isEmpty();
    }

    /** The guard counts at the top of the condition or in its top-level all, never inside any. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            all of x gt 0 and x lt 100 | {"all": [{"field": "x", "op": "gt", "value": 0}, {"field": "x", "op": "lt", "value": 100}]} | false
            x gte 0.5                  | {"field": "x", "op": "gte", "value": 0.5}                                                 | false
            any of x gt 0              | {"any": [{"field": "x", "op": "gt", "value": 0}]}                                           | true
            all of any of x gt 0       | {"all": [{"any": [{"field": "x", "op": "gt", "value": 0}]}]}                                | true
            x gt -1                    | {"field": "x", "op": "gt", "value": -1}                                                     | true
            x gt a field               | {"field": "x", "op": "gt", "value": {"field": "x"}}                                         | true
            x lt 5                     | {"field": "x", "op": "lt", "value": 5}                                                      | true
            """)
    void divisorGuardCountsOnlyAtTheTopOfTheCondition(String name, String condition, boolean reported) {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("DIVISION_BY_UNGUARDED_FIELD"))
                .rule("R-10", r -> r.set("condition", json(condition)))
                .build();

        assertThat(withoutPolicy(document)).extracting(Finding::code, Finding::path).containsExactlyElementsOf(
                reported ? List.of(tuple(DIVISION_BY_UNGUARDED_FIELD, "/rules/0/actions/0/value")) : List.of());
    }

    @Test
    void negativeExclusiveMinimumDoesNotMakeADivisorSafe() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("DIVISION_BY_UNGUARDED_FIELD"))
                .field("x", f -> f.remove("minimum")).field("x", f -> f.put("exclusiveMinimum", -1)).build();

        assertFindings(withoutPolicy(document), tuple(DIVISION_BY_UNGUARDED_FIELD, "/rules/0/actions/0/value"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            not of all                  | {"not": {"all": [{"field": "y", "op": "gt", "value": 5}]}}                                                   | /rules/0/condition/not/all/0 | /fields/0
            not of any                  | {"not": {"any": [{"field": "y", "op": "gt", "value": 5}]}}                                                   | /rules/0/condition/not/any/0 | /fields/0
            a present guard inside any  | {"all": [{"any": [{"field": "y", "op": "present"}, {"field": "x", "op": "gt", "value": 1}]}, {"not": {"field": "y", "op": "gt", "value": 5}}]} | | 
            a present guard inside not  | {"not": {"all": [{"field": "y", "op": "present"}, {"field": "y", "op": "gt", "value": 5}]}}                   | | /fields/0
            absent under not            | {"not": {"field": "y", "op": "absent"}}                                                                        | | /fields/0
            a required field under not  | {"not": {"field": "x", "op": "gt", "value": 5}}                                                                | | /fields/1
            """)
    void missingFieldUnderNotLooksThroughAllAndAnyAndSeesGuardsAnywhere(
            String name, String condition, String missingAt, String unusedAt) {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("MISSING_FIELD_UNDER_NOT"))
                .rule("R-100", r -> r.set("condition", json(condition)))
                .build();
        List<Tuple> expected = new ArrayList<>();
        if (missingAt != null) {
            expected.add(tuple(MISSING_FIELD_UNDER_NOT, missingAt));
        }
        if (unusedAt != null) {
            expected.add(tuple(FIELD_UNUSED, unusedAt));
        }

        assertThat(withoutPolicy(document)).extracting(Finding::code, Finding::path)
                .containsExactlyElementsOf(expected);
    }

    @Test
    void derivedFieldUnderNotIsNotAMissingField() {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("MISSING_FIELD_UNDER_NOT"))
                .document(d -> {
                    ((ArrayNode) d.get("fields")).add(json("{\"name\": \"d\", \"type\": \"number\", \"derived\": true}"));
                    ((ArrayNode) d.get("rules")).insert(0, json("{\"id\": \"R-10\", \"label\": \"set d\", \"priority\": 10,"
                            + " \"condition\": {\"always\": true}, \"actions\": [{\"type\": \"set\", \"field\": \"d\","
                            + " \"value\": {\"field\": \"x\"}}], " + ANALYST + "}"));
                })
                .rule("R-100", r -> r.set("condition", json("{\"not\": {\"field\": \"d\", \"op\": \"gt\", \"value\": 5}}")))
                .build();

        assertFindings(withoutPolicy(document), tuple(FIELD_UNUSED, "/fields/1"));
    }

    @Test
    void fieldsReadInsideAnyOrAsAComparedValueAreUsed() {
        JsonNode floor = RuleSetBuilder.lendingV1()
                .document(d -> fields(d).add(json("{\"name\": \"floor\", \"type\": \"number\"}")))
                .rule("R-170", r -> edit(r, "/condition").set("value", json("{\"field\": \"floor\"}")))
                .build();
        JsonNode vip = RuleSetBuilder.lendingV1()
                .document(d -> fields(d).add(json("{\"name\": \"vip\", \"type\": \"boolean\"}")))
                .rule("R-140", r -> r.set("condition", json("{\"any\": [{\"field\": \"employment_type\", \"op\": \"eq\","
                        + " \"value\": \"unemployed\"}, {\"field\": \"vip\", \"op\": \"eq\", \"value\": false}]}")))
                .build();

        assertThat(publish(floor)).isEmpty();
        assertThat(publish(vip)).isEmpty();
    }

    @Test
    void ruleAtTheSamePriorityDoesNotPrecedeIt() {
        JsonNode referTiesReject = RuleSetBuilder.lendingV1().rule("R-330", r -> r.put("priority", 220)).build();
        JsonNode candidateTiesAlways = RuleSetBuilder.from(invalidRuleSet("CANDIDATE_NEVER_WINS"))
                .rule("R-300", r -> r.put("priority", 900)).build();

        assertFindings(publish(referTiesReject), tuple(PRIORITY_BAND_UNUSUAL, "/rules/16/priority"));
        assertFindings(withoutPolicy(candidateTiesAlways), tuple(PRIORITY_BAND_UNUSUAL, "/rules/0/priority"));
    }

    @Test
    void bandEdgesAreInclusive() {
        assertThat(publish(RuleSetBuilder.lendingV1().rule("R-020", r -> r.put("priority", 99)).build())).isEmpty();
        assertThat(publish(RuleSetBuilder.lendingV1().rule("R-420", r -> r.put("priority", 499)).build())).isEmpty();
        assertThat(publish(RuleSetBuilder.lendingV1().rule("R-420", r -> r.put("priority", 500)).build())).isEmpty();
        assertFindings(publish(RuleSetBuilder.lendingV1().rule("R-100", r -> r.put("priority", 99)).build()),
                tuple(PRIORITY_BAND_UNUSUAL, "/rules/2/priority"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            a two-field range           | {"all": [{"field": "x", "op": "gt", "value": 10}, {"field": "y", "op": "gt", "value": 0}]} | {"field": "x", "op": "between", "value": [5, 20]}
            ranges on different fields  | {"field": "x", "op": "gt", "value": 10}                                                   | {"field": "y", "op": "between", "value": [5, 20]}
            a later two-field range     | {"field": "x", "op": "gt", "value": 10}                                                   | {"all": [{"field": "x", "op": "between", "value": [5, 20]}, {"field": "y", "op": "gt", "value": 0}]}
            a later rule with no range  | {"field": "x", "op": "gt", "value": 10}                                                   | {"any": [{"field": "x", "op": "between", "value": [5, 20]}]}
            """)
    void overlapIsCheckedOnlyBetweenSingleFieldRanges(String name, String earlier, String later) {
        JsonNode document = RuleSetBuilder.from(invalidRuleSet("RULE_OVERLAP_CONFLICT"))
                .document(d -> fields(d).add(json("{\"name\": \"y\", \"type\": \"number\", \"required\": true}")))
                .rule("R-300", r -> r.set("condition", json(earlier)))
                .rule("R-900", r -> r.set("condition", json(later)))
                .build();

        assertThat(withoutPolicy(document)).noneMatch(finding -> finding.code() == RULE_OVERLAP_CONFLICT);
    }

    @Test
    void overlapNeedsNumericRangesAndDifferentOutcomes() {
        JsonNode enums = RuleSetBuilder.from(invalidRuleSet("RULE_OVERLAP_CONFLICT"))
                .document(d -> fields(d).add(json("{\"name\": \"e\", \"type\": \"enum\", \"values\": [\"a\", \"b\"],"
                        + " \"required\": true}")))
                .rule("R-300", r -> r.set("condition", json("{\"field\": \"e\", \"op\": \"in\", \"value\": [\"a\", \"b\"]}")))
                .rule("R-900", r -> r.set("condition", json("{\"field\": \"e\", \"op\": \"eq\", \"value\": \"a\"}")))
                .build();
        JsonNode sameOutcome = RuleSetBuilder.from(invalidRuleSet("RULE_OVERLAP_CONFLICT"))
                .rule("R-900", r -> edit(r, "/actions/0").put("outcome", "refer"))
                .build();

        assertFindings(withoutPolicy(enums), tuple(FIELD_UNUSED, "/fields/0"));
        assertFindings(withoutPolicy(sameOutcome), tuple(PRIORITY_BAND_UNUSUAL, "/rules/1/priority"),
                tuple(NO_TERMINAL_APPROVE, "/rules"));
    }

    // ------------------------------------------------------------------ helpers

    private static void assertFindings(List<Finding> findings, Tuple... expected) {
        assertThat(findings).extracting(Finding::code, Finding::path).containsExactly(expected);
    }

    /** Lending v1 with one rule appended, at index 20. */
    private static JsonNode withRule(String rule) {
        return RuleSetBuilder.lendingV1().document(d -> ((ArrayNode) d.get("rules")).add(json(rule))).build();
    }

    private static ArrayNode fields(ObjectNode document) {
        return (ArrayNode) document.get("fields");
    }

    private static ArrayNode actions(ObjectNode rule) {
        return (ArrayNode) rule.get("actions");
    }

    private static ObjectNode edit(ObjectNode node, String pointer) {
        return (ObjectNode) node.at(pointer);
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }
}
