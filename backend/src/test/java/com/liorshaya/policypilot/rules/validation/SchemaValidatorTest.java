package com.liorshaya.policypilot.rules.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.RuleSetBuilder;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Layer 1, the committed JSON Schema (Document 3, Static Validation and JSON Schema). */
@Requirement({"FR-3", "FR-6", "NFR-7"})
class SchemaValidatorTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void lendingV1PassesTheSchema() {
        assertThat(validator.validate(Fixtures.lendingV1())).isEmpty();
    }

    /**
     * The thirteen schema-level rejections of Document 3 (Engine Conformance Suite), as the reference self-test
     * builds them from version 1. The pointer is the most specific node the schema can name.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedVariants")
    void malformedVariantIsRejectedWithItsCode(
            String name, Consumer<RuleSetBuilder> change, ValidationCode code, String pointer, String keyword) {
        RuleSetBuilder builder = RuleSetBuilder.lendingV1();
        change.accept(builder);

        List<Finding> findings = validator.validate(builder.build());

        assertThat(findings).isNotEmpty().allSatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(code);
            assertThat(finding.path()).isEqualTo(pointer);
            assertThat(finding.message()).startsWith(keyword);
        });
    }

    @Test
    void wrongDslVersionIsReportedOnlyAsDslVersionUnsupported() {
        JsonNode fixture = Fixtures.json("conformance/invalid-DSL_VERSION_UNSUPPORTED.json");
        ObjectNode numericVersion = RuleSetBuilder.lendingV1().document(d -> d.put("dslVersion", 1.0)).build();

        for (JsonNode document : List.of(Fixtures.ruleSetOf(fixture), numericVersion)) {
            assertThat(validator.validate(document)).singleElement().satisfies(finding -> {
                assertThat(finding.code()).isEqualTo(ValidationCode.DSL_VERSION_UNSUPPORTED);
                assertThat(finding.severity()).isEqualTo(Severity.ERROR);
                assertThat(finding.path()).isEqualTo("/dslVersion");
            });
        }
        ObjectNode alsoMalformed = RuleSetBuilder.lendingV1()
                .document(d -> d.put("dslVersion", "2.0").put("extra", 1))
                .build();
        assertThat(validator.validate(alsoMalformed)).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.DSL_VERSION_UNSUPPORTED, "/dslVersion"),
                        tuple(ValidationCode.DSL_SCHEMA, ""));
        // a missing version is a missing required property, not an unsupported one
        ObjectNode missing = RuleSetBuilder.lendingV1().document(d -> d.remove("dslVersion")).build();
        assertThat(validator.validate(missing)).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.DSL_SCHEMA, ""));
    }

    @Test
    void derivedFieldWithADefaultIsReportedAsDerivedRequired() {
        ObjectNode document =
                RuleSetBuilder.lendingV1().field("monthly_installment", f -> f.put("default", 0)).build();

        assertThat(validator.validate(document)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(ValidationCode.DERIVED_REQUIRED);
            assertThat(finding.path()).isEqualTo("/fields/9");
            assertThat(finding.fieldNames()).containsExactly("monthly_installment");
            assertThat(finding.ruleIds()).isEmpty();
        });
    }

    @Test
    void schemaFindingCarriesTheJsonPointerAndTheSchemaKeyword() {
        ObjectNode document = RuleSetBuilder.lendingV1()
                .rule("R-120", r -> ((ObjectNode) r.at("/condition/not")).set("value",
                        NODES.arrayNode().add(1).add(2).add(3)))
                .build();

        assertThat(validator.validate(document)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(ValidationCode.DSL_SCHEMA);
            assertThat(finding.path()).isEqualTo("/rules/6/condition/not/value");
            assertThat(finding.message()).startsWith("maxItems at /rules/6/condition/not/value: ");
            assertThat(finding.ruleIds()).containsExactly("R-120");
            assertThat(finding.fieldNames()).isEmpty();
        });
        ObjectNode numericId = RuleSetBuilder.lendingV1().rule("R-010", r -> r.put("id", 10)).build();
        assertThat(validator.validate(numericId)).singleElement().satisfies(finding -> {
            assertThat(finding.path()).isEqualTo("/rules/0/id");
            assertThat(finding.ruleIds()).isEmpty();
        });
        ObjectNode unknownKey = RuleSetBuilder.lendingV1().document(d -> d.put("extra", 1)).build();
        assertThat(validator.validate(unknownKey)).singleElement().satisfies(finding ->
                assertThat(finding.message()).startsWith("additionalProperties at the document root: "));
    }

    @Test
    void schemaErrorStopsBeforeTheSemanticLayer() {
        // a duplicate field (semantic) and an unknown key (schema): only the schema layer reports
        ObjectNode document = RuleSetBuilder.lendingV1()
                .document(d -> ((ArrayNode) d.get("fields")).add(d.get("fields").get(0).deepCopy()))
                .document(d -> d.put("extra", 1))
                .build();

        ValidationResult result = new RuleSetValidator()
                .validate(document, ValidationContext.PUBLISH, Fixtures.lendingParagraphs(), Set.of());

        assertThat(result.findings()).extracting(Finding::code).containsExactly(ValidationCode.DSL_SCHEMA);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.ruleSet()).isNull();
    }

    @Test
    void resultCarriesTheRuleSetOnceTheSchemaPasses() {
        RuleSetValidator validator = new RuleSetValidator();
        ObjectNode duplicateField = RuleSetBuilder.lendingV1()
                .document(d -> ((ArrayNode) d.get("fields")).add(d.get("fields").get(0).deepCopy()))
                .build();

        ValidationResult clean = validator.validate(
                Fixtures.lendingV1(), ValidationContext.PUBLISH, Fixtures.lendingParagraphs(), Set.of());
        ValidationResult semanticError = validator.validate(
                duplicateField, ValidationContext.PUBLISH, Fixtures.lendingParagraphs(), Set.of());

        assertThat(clean.hasErrors()).isFalse();
        assertThat(clean.ruleSet().id()).isEqualTo("consumer-lending");
        assertThat(semanticError.hasErrors()).isTrue();
        assertThat(semanticError.ruleSet()).isNotNull();
    }

    @Test
    void equallyCloseAlternativesReportOnlyTheSummaryError() {
        // an action with neither a type nor any other property is as far from decide as from set and flag
        ObjectNode document = RuleSetBuilder.lendingV1()
                .rule("R-100", r -> r.putArray("actions").addObject())
                .build();

        assertThat(validator.validate(document)).singleElement().satisfies(finding -> {
            assertThat(finding.path()).isEqualTo("/rules/2/actions/0");
            assertThat(finding.message()).startsWith("oneOf at /rules/2/actions/0: ");
        });
    }

    @Test
    void errorsInsideTheMatchingAlternativeDoNotCountAgainstIt() {
        // two mistakes inside a not: the not is still the closest shape, so both are reported where they are
        ObjectNode document = RuleSetBuilder.lendingV1()
                .rule("R-120", r -> ((ObjectNode) r.at("/condition/not")).put("field", "Amount")
                        .set("value", NODES.arrayNode().add(1).add(2).add(3)))
                .build();

        assertThat(validator.validate(document)).extracting(Finding::path).containsExactlyInAnyOrder(
                "/rules/6/condition/not/field", "/rules/6/condition/not/value");
    }

    @Test
    void alternativeNamedByItsTypeOrKindReportsWhatItLacks() {
        ObjectNode bareSet = RuleSetBuilder.lendingV1()
                .rule("R-100", r -> r.putArray("actions").addObject().put("type", "set"))
                .build();
        ObjectNode barePending = RuleSetBuilder.lendingV1()
                .rule("R-100", r -> r.putObject("provenance").put("kind", "pending"))
                .build();

        assertThat(validator.validate(bareSet)).extracting(Finding::path, Finding::message).containsExactly(
                tuple("/rules/2/actions/0", "required at /rules/2/actions/0: required property 'field' not found"),
                tuple("/rules/2/actions/0", "required at /rules/2/actions/0: required property 'value' not found"));
        assertThat(validator.validate(barePending)).extracting(Finding::message).containsExactly(
                "required at /rules/2/provenance: required property 'changeRequestId' not found",
                "required at /rules/2/provenance: required property 'rationale' not found");
    }

    static Stream<Arguments> malformedVariants() {
        String longPattern = "a".repeat(201);
        return Stream.of(
                variant("an unknown top-level key",
                        b -> b.document(d -> d.put("extra", 1)),
                        ValidationCode.DSL_SCHEMA, "", "additionalProperties"),
                variant("between with three values",
                        b -> b.rule("R-120", r -> ((ObjectNode) r.at("/condition/not"))
                                .set("value", NODES.arrayNode().add(1).add(2).add(3))),
                        ValidationCode.DSL_SCHEMA, "/rules/6/condition/not/value", "maxItems"),
                variant("present with a value",
                        b -> b.rule("R-310", r -> ((ObjectNode) r.at("/condition/all/1")).put("value", 1)),
                        ValidationCode.DSL_SCHEMA, "/rules/14/condition/all/1", "not"),
                variant("a rule id RULE1",
                        b -> b.rule("R-010", r -> r.put("id", "RULE1")),
                        ValidationCode.DSL_SCHEMA, "/rules/0/id", "pattern"),
                variant("a derived field marked required",
                        b -> b.field("monthly_installment", f -> f.put("required", true)),
                        ValidationCode.DERIVED_REQUIRED, "/fields/9/required", "const"),
                variant("an enum field without values",
                        b -> b.field("employment_type", f -> f.remove("values")), ValidationCode.DSL_SCHEMA,
                        "/fields/3", "required"),
                variant("an empty all",
                        b -> b.rule("R-110", r -> r.putObject("condition").putArray("all")),
                        ValidationCode.DSL_SCHEMA, "/rules/3/condition/all", "minItems"),
                variant("analyst provenance without a note",
                        b -> b.rule("R-310", r -> ((ObjectNode) r.get("provenance")).remove("note")),
                        ValidationCode.DSL_SCHEMA, "/rules/14/provenance", "required"),
                variant("pending provenance without a rationale",
                        b -> b.rule("R-010", r -> r.putObject("provenance")
                                .put("kind", "pending").put("changeRequestId", "cr-1")),
                        ValidationCode.DSL_SCHEMA, "/rules/0/provenance", "required"),
                variant("dslVersion 2.0",
                        b -> b.document(d -> d.put("dslVersion", "2.0")),
                        ValidationCode.DSL_VERSION_UNSUPPORTED, "/dslVersion", "dslVersion must be 1.0"),
                variant("a field named Age",
                        b -> b.field("age", f -> f.put("name", "Age")),
                        ValidationCode.DSL_SCHEMA, "/fields/0/name", "pattern"),
                variant("a matches pattern of 201 characters",
                        b -> b.rule("R-110", r -> r.putObject("condition")
                                .put("field", "employment_type").put("op", "matches").put("value", longPattern)),
                        ValidationCode.DSL_SCHEMA, "/rules/3/condition/value", "maxLength"),
                variant("a matches operand that is not a string",
                        b -> b.rule("R-110", r -> r.putObject("condition")
                                .put("field", "employment_type").put("op", "matches").put("value", 5)),
                        ValidationCode.DSL_SCHEMA, "/rules/3/condition/value", "type"));
    }

    private static Arguments variant(
            String name, Consumer<RuleSetBuilder> change, ValidationCode code, String pointer, String keyword) {
        return arguments(name, change, code, pointer, keyword);
    }
}
