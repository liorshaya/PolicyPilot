package com.liorshaya.policypilot.rules.validation;

import static com.liorshaya.policypilot.rules.validation.Validations.invalidFixture;
import static com.liorshaya.policypilot.rules.validation.Validations.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.RuleSetBuilder;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Layer 2 (Document 3, Static Validation): per code, the committed {@code invalid-<CODE>.json} fixture as the
 * positive case and a near miss built from the lending rule set as the negative one. Pointers name the node that is
 * wrong; the reference's pointer is a prefix of each.
 */
@Requirement({"FR-3", "FR-4", "FR-6"})
class SemanticValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // ------------------------------------------------------------------ FIELD_DUPLICATE, FIELD_UNKNOWN

    @Test
    void fieldDuplicateReportedAtTheSecondField() {
        assertThat(codesAndPaths(invalidFixture("FIELD_DUPLICATE")))
                .containsExactly(tuple(ValidationCode.FIELD_DUPLICATE, "/fields/1/name"));
    }

    @Test
    void fieldUnknownReportedInACondition() {
        assertThat(codesAndPaths(invalidFixture("FIELD_UNKNOWN")))
                .containsExactly(tuple(ValidationCode.FIELD_UNKNOWN, "/rules/0/condition/field"));
        assertThat(codesAndPaths(publish(lending(b -> b.rule("R-140", r -> r.set("condition", json("{\"any\": ["
                        + "{\"field\": \"employment_type\", \"op\": \"eq\", \"value\": \"unemployed\"},"
                        + "{\"field\": \"benefits\", \"op\": \"present\"}]}")))))))
                .containsExactly(tuple(ValidationCode.FIELD_UNKNOWN, "/rules/8/condition/any/1/field"));
    }

    @Test
    void fieldUnknownReportedInAnExpressionAndInASetTarget() {
        assertThat(codesAndPaths(publish(lending(b -> b
                .rule("R-020", r -> edit(r, "/actions/0/value/args/0/args/0/args/0")
                        .put("field", "existing_debt"))))))
                .containsExactly(tuple(ValidationCode.FIELD_UNKNOWN, "/rules/1/actions/0/value/args/0/args/0/args/0"));
        assertThat(codesAndPaths(publish(lending(b -> b
                .rule("R-010", r -> edit(r, "/actions/0").put("field", "installment"))))))
                .containsExactly(tuple(ValidationCode.FIELD_UNKNOWN, "/rules/0/actions/0/field"));
        assertThat(codesAndPaths(publish(lending(b -> b.rule("R-170", r -> edit(r, "/condition")
                        .set("value", json("{\"field\": \"minimum_income\"}")))))))
                .containsExactly(tuple(ValidationCode.FIELD_UNKNOWN, "/rules/11/condition/value"));
    }

    // ------------------------------------------------------------------ FIELD_TYPE_MISMATCH

    @Test
    void fieldTypeMismatchReportedForGtOnABoolean() {
        assertThat(codesAndPaths(invalidFixture("FIELD_TYPE_MISMATCH")))
                .containsExactly(tuple(ValidationCode.FIELD_TYPE_MISMATCH, "/rules/0/condition/op"));
    }

    @Test
    void fieldTypeMismatchReportedForAStringAgainstANumberAndMatchesOnAnEnum() {
        assertThat(codesAndPaths(publish(lending(b -> b
                .rule("R-170", r -> edit(r, "/condition").put("value", "8000"))))))
                .containsExactly(tuple(ValidationCode.FIELD_TYPE_MISMATCH, "/rules/11/condition/value"));
        assertThat(codesAndPaths(publish(lending(b -> b.rule("R-140", r -> edit(r, "/condition")
                        .put("op", "matches").put("value", "unemp.*"))))))
                .containsExactly(tuple(ValidationCode.FIELD_TYPE_MISMATCH, "/rules/8/condition/op"));
    }

    /** The other places a value can fail to fit its field, each at its own node. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("typeMismatches")
    void fieldTypeMismatchReportedWhereverAValueDoesNotFit(
            String name, Consumer<RuleSetBuilder> change, String pointer) {
        assertThat(codesAndPaths(publish(lending(change))))
                .containsExactly(tuple(ValidationCode.FIELD_TYPE_MISMATCH, pointer));
    }

    @Test
    void fieldTypeMismatchReportedForAFractionAgainstAnIntegerField() {
        assertThat(codesAndPaths(publish(lending(b -> b.rule("R-100", r -> edit(r, "/condition").put("value", 20.5))))))
                .containsExactly(tuple(ValidationCode.FIELD_TYPE_MISMATCH, "/rules/2/condition/value"));
    }

    @Test
    void numberAndIntegerFieldReferencesAreCompatible() {
        JsonNode document = lending(b -> b
                .rule("R-170", r -> edit(r, "/condition").set("value", json("{\"field\": \"age\"}")))
                .rule("R-100", r -> edit(r, "/condition").put("value", 21.0)));

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void valuesThatFitTheirFieldRaiseNoError() {
        JsonNode document = lending(b -> b
                .document(d -> fields(d)
                        .add(json("{\"name\": \"application_date\", \"type\": \"date\"}"))
                        .add(json("{\"name\": \"flagged\", \"type\": \"boolean\", \"derived\": true}")))
                .rule("R-420", r -> ((ArrayNode) r.get("actions"))
                        .add(json("{\"type\": \"set\", \"field\": \"flagged\", \"value\": true}")))
                .rule("R-900", r -> r.set("condition",
                        json("{\"field\": \"application_date\", \"op\": \"lt\", \"value\": \"2027-01-01\"}")))
                .rule("R-220", r -> edit(r, "/condition").put("op", "in").set("value", json("[2, 3, 4]"))));

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    // ------------------------------------------------------------------ FIELD_DOMAIN_INVALID

    @Test
    void fieldDomainInvalidReportedForMinimumAboveMaximum() {
        assertThat(codesAndPaths(invalidFixture("FIELD_DOMAIN_INVALID")))
                .containsExactly(tuple(ValidationCode.FIELD_DOMAIN_INVALID, "/fields/0"));
        // exclusive bounds stand in for the inclusive ones when those are absent
        assertThat(codesAndPaths(publish(lending(b -> b.field("age", f -> f.remove("minimum")).field("age",
                        f -> f.put("exclusiveMinimum", 121))))))
                .containsExactly(tuple(ValidationCode.FIELD_DOMAIN_INVALID, "/fields/0"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"})
    void fieldDomainInvalidReportedForADomainOnANonNumericField(String bound) {
        assertThat(codesAndPaths(publish(lending(b -> b.field("employment_type", f -> f.put(bound, 0))))))
                .containsExactly(tuple(ValidationCode.FIELD_DOMAIN_INVALID, "/fields/3/" + bound));
    }

    @Test
    void minimumEqualToMaximumIsAValidDomain() {
        JsonNode document = lending(b -> b.field("age", f -> f.put("minimum", 120).put("maximum", 120))
                .field("term_months", f -> f.remove("minimum")).field("term_months",
                        f -> f.put("exclusiveMinimum", 0).put("exclusiveMaximum", 85))
                .field("credit_events_24m", f -> f.remove("minimum")).field("credit_events_24m",
                        f -> f.put("maximum", 50)));

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    // ------------------------------------------------------------------ ENUM_VALUE_UNKNOWN

    @Test
    void enumValueUnknownReportedForEq() {
        assertThat(codesAndPaths(invalidFixture("ENUM_VALUE_UNKNOWN")))
                .containsExactly(tuple(ValidationCode.ENUM_VALUE_UNKNOWN, "/rules/0/condition/value"));
    }

    @Test
    void enumValueUnknownReportedForInNotInAndNe() {
        assertThat(codesAndPaths(publish(lending(b -> b.rule("R-310", r -> ((ArrayNode) r.at("/condition/all/0/value"))
                        .set(1, "freelancer"))))))
                .containsExactly(tuple(ValidationCode.ENUM_VALUE_UNKNOWN, "/rules/14/condition/all/0/value/1"));
        assertThat(codesAndPaths(publish(lending(b -> b
                .rule("R-310", r -> edit(r, "/condition/all/0").put("op", "not_in")
                        .set("value", json("[\"freelancer\", \"salaried\"]")))))))
                .containsExactly(tuple(ValidationCode.ENUM_VALUE_UNKNOWN, "/rules/14/condition/all/0/value/0"));
        assertThat(codesAndPaths(publish(lending(b -> b
                .rule("R-110", r -> edit(r, "/condition/all/1").put("value", "pensioner"))))))
                .containsExactly(tuple(ValidationCode.ENUM_VALUE_UNKNOWN, "/rules/3/condition/all/1/value"));
    }

    // ------------------------------------------------------------------ EXPR_TYPE_MISMATCH, EXPR_ARITY

    @Test
    void exprTypeMismatchReportedForArithmeticOnAString() {
        assertThat(codesAndPaths(invalidFixture("EXPR_TYPE_MISMATCH")))
                .containsExactly(tuple(ValidationCode.EXPR_TYPE_MISMATCH, "/rules/0/actions/0/value/args/0"));
    }

    @Test
    void exprTypeMismatchReportedForMonthsBetweenOnANumberAndADateInArithmetic() {
        assertThat(codesAndPaths(publish(withDates("{\"fn\": \"months_between\", \"args\": "
                        + "[{\"field\": \"birth_date\"}, {\"field\": \"age\"}]}"))))
                .containsExactly(tuple(ValidationCode.EXPR_TYPE_MISMATCH, "/rules/5/condition/all/1/value/args/1"));
        assertThat(publish(withDates("{\"fn\": \"add\", \"args\": [{\"field\": \"birth_date\"}, 1]}")))
                .extracting(Finding::code, Finding::path, Finding::message)
                .containsExactly(tuple(ValidationCode.EXPR_TYPE_MISMATCH, "/rules/5/condition/all/1/value/args/0",
                        "R-116: add takes numbers only"));
        // a function's result is a number, so it cannot stand where months_between needs a date
        assertThat(publish(withDates("{\"fn\": \"months_between\", \"args\": "
                        + "[{\"fn\": \"abs\", \"args\": [1]}, {\"field\": \"application_date\"}]}")))
                .extracting(Finding::code, Finding::path, Finding::message)
                .containsExactly(tuple(ValidationCode.EXPR_TYPE_MISMATCH, "/rules/5/condition/all/1/value/args/0",
                        "R-116: months_between takes date fields only"));
    }

    @Test
    void monthsBetweenOnTwoDateFieldsIsValid() {
        JsonNode document = withDates("{\"fn\": \"div\", \"args\": [{\"fn\": \"months_between\", \"args\": "
                + "[{\"field\": \"birth_date\"}, {\"field\": \"application_date\"}]}, 12]}");

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void exprArityReportedForAbsWithTwoArguments() {
        assertThat(codesAndPaths(invalidFixture("EXPR_ARITY")))
                .containsExactly(tuple(ValidationCode.EXPR_ARITY, "/rules/0/actions/0/value"));
    }

    @Test
    void exprArityReportedForAddWithOneArgument() {
        assertThat(codesAndPaths(publish(lending(b -> b
                .rule("R-020", r -> ((ArrayNode) r.at("/actions/0/value/args/0/args/0/args"))
                        .remove(1))))))
                .containsExactly(tuple(ValidationCode.EXPR_ARITY, "/rules/1/actions/0/value/args/0/args/0"));
    }

    @Test
    void addWithEightArgumentsIsValid() {
        JsonNode document = lending(b -> b.rule("R-020", r -> edit(r, "/actions/0/value/args/0/args/0")
                .set("args", json("[{\"field\": \"existing_monthly_debt\"}, {\"field\": \"monthly_installment\"},"
                        + " 0, 0, 0, 0, 0, 0]"))));

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void exprDepthReportedForNineNestedFunctions() {
        assertThat(codesAndPaths(invalidFixture("EXPR_DEPTH")))
                .containsExactly(tuple(ValidationCode.EXPR_DEPTH, "/rules/0/actions/0/value" + "/args/0".repeat(8)));
    }

    @Test
    void eightNestedFunctionsAreValid() {
        String expression = "{\"field\": \"monthly_installment\"}";
        for (int depth = 0; depth < 8; depth++) {
            expression = "{\"fn\": \"add\", \"args\": [" + expression + ", 0]}";
        }
        String deepest = expression;
        JsonNode document = lending(b -> b.rule("R-200", r -> edit(r, "/condition").set("value", json(deepest))));

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    // ------------------------------------------------------------------ BETWEEN_RANGE_INVALID, REGEX_INVALID

    @Test
    void betweenRangeInvalidReportedForLowAboveHigh() {
        assertThat(codesAndPaths(invalidFixture("BETWEEN_RANGE_INVALID")))
                .containsExactly(tuple(ValidationCode.BETWEEN_RANGE_INVALID, "/rules/0/condition/value"));
        assertThat(codesAndPaths(publish(datesBuilder().rule("R-900", r -> r.set("condition", json(
                        "{\"field\": \"application_date\", \"op\": \"between\","
                                + " \"value\": [\"2026-12-31\", \"2026-01-01\"]}")))
                        .build())))
                .containsExactly(tuple(ValidationCode.BETWEEN_RANGE_INVALID, "/rules/19/condition/value"));
    }

    @Test
    void betweenWithEqualBoundsIsValid() {
        JsonNode document = lending(b -> b.rule("R-120", r -> edit(r, "/condition/not")
                .set("value", json("[10000, 10000]"))));
        JsonNode dates = datesBuilder().rule("R-900", r -> r.set("condition", json(
                "{\"field\": \"application_date\", \"op\": \"between\", \"value\": [\"2026-01-01\", \"2026-01-01\"]}")))
                .build();

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
        assertThat(publish(dates)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void regexInvalidReportedForALookahead() {
        assertThat(codesAndPaths(invalidFixture("REGEX_INVALID")))
                .containsExactly(tuple(ValidationCode.REGEX_INVALID, "/rules/0/condition/value"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"(a)\\1", "[a-"})
    void regexInvalidReportedForABackreferenceAndAPatternThatDoesNotCompile(String pattern) {
        assertThat(codesAndPaths(publish(withIban(pattern))))
                .containsExactly(tuple(ValidationCode.REGEX_INVALID, "/rules/19/condition/value"));
    }

    @Test
    void re2CompatiblePatternIsValid() {
        assertThat(publish(withIban("^IL[0-9]{2}"))).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    // ------------------------------------------------------------------ identifiers, ids, set targets

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"today", "now", "null", "true", "false"})
    void reservedIdentifierReportedForEveryReservedWord(String word) {
        assertThat(codesAndPaths(publish(lending(b -> b.document(d -> fields(d)
                        .add(json("{\"name\": \"" + word + "\", \"type\": \"number\"}")))))))
                .containsExactly(tuple(ValidationCode.RESERVED_IDENTIFIER, "/fields/11/name"));
        if (word.equals("today")) {
            assertThat(codesAndPaths(invalidFixture("RESERVED_IDENTIFIER")))
                    .containsExactly(tuple(ValidationCode.RESERVED_IDENTIFIER, "/fields/0/name"));
        }
    }

    @Test
    void identifierContainingAReservedWordIsAllowed() {
        JsonNode document = lending(b -> b.document(d -> fields(d)
                .add(json("{\"name\": \"today_rate\", \"type\": \"number\"}"))));

        assertThat(publish(document)).noneMatch(finding -> finding.severity() == Severity.ERROR);
    }

    @Test
    void ruleIdDuplicateReportedAtTheSecondRule() {
        assertThat(codesAndPaths(invalidFixture("RULE_ID_DUPLICATE")))
                .containsExactly(tuple(ValidationCode.RULE_ID_DUPLICATE, "/rules/1/id"));
    }

    @Test
    void derivedWriteOnlyReportedForASetOnACaseField() {
        assertThat(codesAndPaths(invalidFixture("DERIVED_WRITE_ONLY")))
                .containsExactly(tuple(ValidationCode.DERIVED_WRITE_ONLY, "/rules/0/actions/0/field"));
    }

    // ------------------------------------------------------------------ provenance against the policy text

    @Test
    void provenanceParagraphMissingReportedForARule() {
        assertThat(codesAndPaths(invalidFixture("PROVENANCE_PARAGRAPH_MISSING")))
                .containsExactly(tuple(ValidationCode.PROVENANCE_PARAGRAPH_MISSING, "/rules/2/provenance/paragraph"));
    }

    @Test
    void provenanceParagraphMissingReportedForAFieldSource() {
        assertThat(codesAndPaths(publish(lending(b -> b.field("age", f -> edit(f, "/source").put("paragraph", 10))))))
                .containsExactly(tuple(ValidationCode.PROVENANCE_PARAGRAPH_MISSING, "/fields/0/source/paragraph"));
    }

    @Test
    void lastParagraphIsValidAndOnePastItIsMissing() {
        assertThat(publish(lending(b -> { }))).isEmpty();
        assertThat(codesAndPaths(publish(lending(b -> b
                .rule("R-900", r -> edit(r, "/provenance").put("paragraph", 10))))))
                .containsExactly(tuple(ValidationCode.PROVENANCE_PARAGRAPH_MISSING, "/rules/19/provenance/paragraph"));
    }

    @Test
    void provenanceQuoteMismatchReportedForARule() {
        assertThat(invalidFixture("PROVENANCE_QUOTE_MISMATCH"))
                .extracting(Finding::code, Finding::path, Finding::message)
                .containsExactly(tuple(ValidationCode.PROVENANCE_QUOTE_MISMATCH, "/rules/2/provenance/quote",
                        "R-100: the quote does not occur in paragraph 1"));
    }

    @Test
    void provenanceQuoteMismatchReportedForAFieldSource() {
        assertThat(publish(lending(b -> b.field("age", f -> edit(f, "/source").put("quote", "גיל מינימלי")))))
                .extracting(Finding::code, Finding::path, Finding::message)
                .containsExactly(tuple(ValidationCode.PROVENANCE_QUOTE_MISMATCH, "/fields/0/source/quote",
                        "age: the quote does not occur in paragraph 1"));
    }

    @Test
    void quoteDifferingOnlyInPunctuationAndWhitespaceMatches() {
        JsonNode document = lending(b -> b.rule("R-100", r -> edit(r, "/provenance")
                .put("quote", "גילו  21 עד 70, בעת הגשת הבקשה.")));

        assertThat(publish(document)).isEmpty();
    }

    // ------------------------------------------------------------------ the layer stop

    @Test
    void semanticErrorSuppressesStructuralFindings() {
        // R-320 at 210 alone warns REFER_PRECEDES_REJECT; with an unknown field the semantic error is all that shows
        JsonNode document = lending(b -> b.rule("R-320", r -> r.put("priority", 210))
                .rule("R-100", r -> edit(r, "/condition").put("field", "age_years")));

        assertThat(codesAndPaths(publish(document)))
                .containsExactly(tuple(ValidationCode.FIELD_UNKNOWN, "/rules/2/condition/field"));
    }

    // ------------------------------------------------------------------ the finding shape

    @Test
    void findingCarriesCodeSeverityPointerMessageRuleIdsAndFieldNames() {
        assertThat(invalidFixture("ENUM_VALUE_UNKNOWN")).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(ValidationCode.ENUM_VALUE_UNKNOWN);
            assertThat(finding.severity()).isEqualTo(Severity.ERROR);
            assertThat(finding.path()).isEqualTo("/rules/0/condition/value");
            assertThat(finding.message()).isEqualTo("R-100: z is not a value of e");
            assertThat(finding.ruleIds()).containsExactly("R-100");
            assertThat(finding.fieldNames()).containsExactly("e");
        });
        assertThat(invalidFixture("FIELD_DUPLICATE")).singleElement().satisfies(finding -> {
            assertThat(finding.ruleIds()).isEmpty();
            assertThat(finding.fieldNames()).containsExactly("x");
        });
        assertThat(invalidFixture("EXPR_ARITY")).singleElement()
                .satisfies(finding -> assertThat(finding.fieldNames()).isEmpty());
    }

    // ------------------------------------------------------------------ cases and helpers

    static Stream<Arguments> typeMismatches() {
        return Stream.of(
                mismatch("between on an enum", b -> b.rule("R-310", r -> edit(r, "/condition/all/0")
                        .put("op", "between").set("value", json("[\"a\", \"b\"]"))), "/rules/14/condition/all/0/op"),
                mismatch("between bounds of the wrong type", b -> b.rule("R-120", r -> edit(r, "/condition/not")
                        .set("value", json("[\"low\", 150000]"))), "/rules/6/condition/not/value"),
                mismatch("between bounds with a high bound of the wrong type", b -> b.rule("R-120",
                        r -> edit(r, "/condition/not").set("value", json("[10000, \"high\"]"))),
                        "/rules/6/condition/not/value"),
                mismatch("a boolean against a number",
                        b -> b.rule("R-100", r -> edit(r, "/condition").put("value", true)),
                        "/rules/2/condition/value"),
                mismatch("a date that does not exist", b -> b
                        .document(d -> fields(d).add(json("{\"name\": \"application_date\", \"type\": \"date\"}")))
                        .rule("R-900", r -> r.set("condition", json(
                                "{\"field\": \"application_date\", \"op\": \"lt\", \"value\": \"2026-02-30\"}"))),
                        "/rules/19/condition/value"),
                mismatch("a date without leading zeros", b -> b
                        .document(d -> fields(d).add(json("{\"name\": \"application_date\", \"type\": \"date\"}")))
                        .rule("R-900", r -> r.set("condition", json(
                                "{\"field\": \"application_date\", \"op\": \"lt\", \"value\": \"2026-9-1\"}"))),
                        "/rules/19/condition/value"),
                mismatch("a date with a five-digit year", b -> b
                        .document(d -> fields(d).add(json("{\"name\": \"application_date\", \"type\": \"date\"}")))
                        .rule("R-900", r -> r.set("condition", json(
                                "{\"field\": \"application_date\", \"op\": \"lt\", \"value\": \"+12026-01-01\"}"))),
                        "/rules/19/condition/value"),
                mismatch("a number in an enum list",
                        b -> b.rule("R-310", r -> ((ArrayNode) r.at("/condition/all/0/value")).set(1, 5)),
                        "/rules/14/condition/all/0/value/1"),
                mismatch("lt on an enum", b -> b.rule("R-140", r -> edit(r, "/condition").put("op", "lt")),
                        "/rules/8/condition/op"),
                mismatch("eq with a list", b -> b.rule("R-140", r -> edit(r, "/condition")
                        .set("value", json("[\"unemployed\"]"))), "/rules/8/condition/value"),
                mismatch("an expression against an enum", b -> b.rule("R-140", r -> edit(r, "/condition")
                        .set("value", json("{\"fn\": \"add\", \"args\": [1, 2]}"))), "/rules/8/condition/value"),
                mismatch("an enum compared with a number field", b -> b.rule("R-140", r -> edit(r, "/condition")
                        .set("value", json("{\"field\": \"age\"}"))), "/rules/8/condition/value"),
                mismatch("a string set into a number", b -> b.rule("R-010", r -> edit(r, "/actions/0")
                        .put("value", "high")), "/rules/0/actions/0/value"),
                mismatch("an expression set into a boolean", b -> b
                        .document(d -> fields(d)
                                .add(json("{\"name\": \"flagged\", \"type\": \"boolean\", \"derived\": true}")))
                        .rule("R-420", r -> ((ArrayNode) r.get("actions")).add(json(
                                "{\"type\": \"set\", \"field\": \"flagged\", \"value\": {\"field\": \"age\"}}"))),
                        "/rules/18/actions/1/value"));
    }

    private static Arguments mismatch(String name, Consumer<RuleSetBuilder> change, String pointer) {
        return arguments(name, change, pointer);
    }

    /** The code and pointer of each finding, in order, for a direct assertion. */
    private static List<Tuple> codesAndPaths(List<Finding> findings) {
        return findings.stream().map(finding -> tuple(finding.code(), finding.path())).toList();
    }

    private static JsonNode lending(Consumer<RuleSetBuilder> change) {
        RuleSetBuilder builder = RuleSetBuilder.lendingV1();
        change.accept(builder);
        return builder.build();
    }

    /** Lending with two optional date fields. */
    private static RuleSetBuilder datesBuilder() {
        return RuleSetBuilder.lendingV1().document(d -> fields(d)
                .add(json("{\"name\": \"birth_date\", \"type\": \"date\"}"))
                .add(json("{\"name\": \"application_date\", \"type\": \"date\"}")));
    }

    /** Lending with the two date fields and the given expression as R-116's right-hand side. */
    private static JsonNode withDates(String expression) {
        return datesBuilder().rule("R-116", r -> edit(r, "/condition/all/1").set("value", json(expression))).build();
    }

    /** Lending with an optional string field and R-900 guarded by a {@code matches} on it. */
    private static JsonNode withIban(String pattern) {
        return RuleSetBuilder.lendingV1()
                .document(d -> fields(d).add(json("{\"name\": \"iban\", \"type\": \"string\"}")))
                .rule("R-900", r -> r.putObject("condition").put("field", "iban").put("op", "matches")
                        .put("value", pattern))
                .build();
    }

    private static ArrayNode fields(ObjectNode document) {
        return (ArrayNode) document.get("fields");
    }

    private static ObjectNode edit(ObjectNode node, String pointer) {
        return (ObjectNode) node.at(pointer);
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }
}
