package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Condition;
import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The normalization the evaluation runner compares conditions after (Document 4, "Rule matching"): "combinators
 * sorted, {@code not between} and two comparisons unified, {@code gte x} and {@code gt x-1} on integers unified,
 * enum lists sorted". Each test states two ways of writing one condition and asserts they normalize to the same
 * key; the expected value is that sentence of Document 4, not anything this code produces. The fields are the
 * lending fixture's, so the types the normalization turns on --- integer, number, enum --- are real ones.
 */
class ConditionNormalizerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());
    private static final Map<String, Field> FIELDS =
            LENDING.fields().stream().collect(Collectors.toMap(Field::name, Function.identity()));

    // Document 4: "combinators sorted". Expected: the same two branches in either order are one condition
    @Test
    void combinatorsAreSorted() {
        assertThat(keyOf("""
                {"all":[{"field":"age","op":"gt","value":70},{"field":"has_guarantor","op":"eq","value":false}]}"""))
                .isEqualTo(keyOf("""
                {"all":[{"field":"has_guarantor","op":"eq","value":false},{"field":"age","op":"gt","value":70}]}"""));
    }

    // Document 4: "not between and two comparisons unified". Expected: the fixture's own R-120, an amount outside
    // 10,000 to 150,000, written either way
    @Test
    void notBetweenEqualsTheTwoComparisonsItStandsFor() {
        assertThat(keyOf("""
                {"not":{"field":"requested_amount","op":"between","value":[10000,150000]}}"""))
                .isEqualTo(keyOf("""
                {"any":[{"field":"requested_amount","op":"lt","value":10000},\
                {"field":"requested_amount","op":"gt","value":150000}]}"""));
    }

    // The same reading the other way round: between is its two comparisons
    @Test
    void betweenEqualsTheTwoComparisonsItStandsFor() {
        assertThat(keyOf("""
                {"field":"debt_to_income","op":"between","value":[0.35,0.40]}"""))
                .isEqualTo(keyOf("""
                {"all":[{"field":"debt_to_income","op":"gte","value":0.35},\
                {"field":"debt_to_income","op":"lte","value":0.40}]}"""));
    }

    // Document 4: "gte x and gt x-1 on integers unified". Expected: age is an integer in the fixture, so 21 and
    // over is one condition however it is written
    @Test
    void gteEqualsGtOneBelowItOnAnInteger() {
        assertThat(keyOf("""
                {"field":"age","op":"gte","value":21}""")).isEqualTo(keyOf("""
                {"field":"age","op":"gt","value":20}"""));
        assertThat(keyOf("""
                {"field":"age","op":"lte","value":70}""")).isEqualTo(keyOf("""
                {"field":"age","op":"lt","value":71}"""));
    }

    // Document 4 says "on integers", and the bound being whole is not enough. Expected: monthly_income is a number
    // in the fixture, so the fixture's own 8,000 threshold (R-170) is not the same condition as over 7,999 ---
    // 7,999.50 meets one and not the other --- while the same pair on an integer field is
    @Test
    void gteIsNotUnifiedWithGtOnANumberEvenWhenTheBoundIsWhole() {
        assertThat(keyOf("""
                {"field":"monthly_income","op":"gte","value":8000}""")).isNotEqualTo(keyOf("""
                {"field":"monthly_income","op":"gt","value":7999}"""));
        assertThat(keyOf("""
                {"field":"employment_months","op":"gte","value":6}""")).isEqualTo(keyOf("""
                {"field":"employment_months","op":"gt","value":5}"""));
    }

    // Document 4: "enum lists sorted". Expected: the order the values are written in does not matter
    @Test
    void enumListsAreSorted() {
        assertThat(keyOf("""
                {"field":"employment_type","op":"in","value":["retired","salaried"]}"""))
                .isEqualTo(keyOf("""
                {"field":"employment_type","op":"in","value":["salaried","retired"]}"""));
    }

    // The enum is closed, so a listing and its complement name the same set of values. Expected: "not unemployed"
    // and "one of the other three" are one condition over the fixture's four employment types
    @Test
    void anEnumIsComparedAsTheSetOfValuesItAllows() {
        assertThat(keyOf("""
                {"field":"employment_type","op":"ne","value":"unemployed"}"""))
                .isEqualTo(keyOf("""
                {"field":"employment_type","op":"in","value":["retired","salaried","self_employed"]}"""));
        assertThat(keyOf("""
                {"field":"employment_type","op":"eq","value":"retired"}"""))
                .isEqualTo(keyOf("""
                {"field":"employment_type","op":"not_in","value":["salaried","self_employed","unemployed"]}"""));
    }

    // Document 4 lists the normalizations and no others, but a nested combinator of the same kind is the same
    // condition written with brackets. Expected: one flattened, sorted set of branches either way
    @Test
    void nestedCombinatorsOfTheSameKindAreFlattened() {
        assertThat(keyOf("""
                {"all":[{"field":"age","op":"gte","value":21},{"all":[\
                {"field":"employment_type","op":"ne","value":"unemployed"},\
                {"field":"credit_events_24m","op":"lt","value":2}]}]}"""))
                .isEqualTo(keyOf("""
                {"all":[{"field":"credit_events_24m","op":"lt","value":2},\
                {"field":"employment_type","op":"ne","value":"unemployed"},\
                {"field":"age","op":"gte","value":21}]}"""));
    }

    // A combinator with one branch is that branch. Expected: the same key with and without the wrapper
    @Test
    void aCombinatorOfOneBranchIsThatBranch() {
        assertThat(keyOf("""
                {"all":[{"field":"age","op":"gt","value":70}]}""")).isEqualTo(keyOf("""
                {"field":"age","op":"gt","value":70}"""));
    }

    // The guard that keeps the normalization honest: conditions that are genuinely different must stay different,
    // or every rule would match every rule. Expected: a different field, a different threshold, a different
    // combinator and a negation each give a different key
    @Test
    void conditionsThatDifferDoNotNormalizeEqual() {
        String over70 = keyOf("""
                {"field":"age","op":"gt","value":70}""");
        assertThat(over70).isNotEqualTo(keyOf("""
                {"field":"age","op":"gt","value":75}"""));
        assertThat(over70).isNotEqualTo(keyOf("""
                {"field":"employment_months","op":"gt","value":70}"""));
        assertThat(over70).isNotEqualTo(keyOf("""
                {"not":{"field":"age","op":"gt","value":70}}"""));
        assertThat(keyOf("""
                {"all":[{"field":"age","op":"gt","value":70},{"field":"has_guarantor","op":"eq","value":false}]}"""))
                .isNotEqualTo(keyOf("""
                {"any":[{"field":"age","op":"gt","value":70},{"field":"has_guarantor","op":"eq","value":false}]}"""));
    }

    // Document 3, always. Expected: the constant condition is its own key and matches nothing else
    @Test
    void alwaysIsItsOwnCondition() {
        assertThat(keyOf("{\"always\":true}")).isEqualTo(keyOf("{\"always\":true}"))
                .isNotEqualTo(keyOf("""
                {"field":"age","op":"gt","value":70}"""));
    }

    private static String keyOf(String json) {
        return ConditionNormalizer.key(condition(json), FIELDS);
    }

    /** The mapper reads whole documents, so a bare condition is read by swapping it into the fixture's first rule. */
    private static Condition condition(String json) {
        ObjectNode document = Fixtures.lendingV1();
        ((ObjectNode) document.required("rules").get(0)).set("condition", JSON.readTree(json));
        return new RuleSetMapper().toRuleSet(document).rules().getFirst().condition();
    }
}
