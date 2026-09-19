package com.liorshaya.policypilot.rules.validation;

import static com.liorshaya.policypilot.rules.validation.Layer.SCHEMA;
import static com.liorshaya.policypilot.rules.validation.Layer.SEMANTIC;
import static com.liorshaya.policypilot.rules.validation.Layer.STRUCTURAL;
import static com.liorshaya.policypilot.rules.validation.Severity.ERROR;
import static com.liorshaya.policypilot.rules.validation.Severity.INFO;
import static com.liorshaya.policypilot.rules.validation.Severity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/** The validator conformance of Document 3: the code table and the invalid fixtures. */
@Requirement({"FR-3", "FR-4", "FR-6"})
class InvalidFixturesTest {

    /** Document 3, Static Validation: the table transcribed row by row, in its order. */
    private static final Map<String, List<Object>> DOCUMENT_TABLE = new LinkedHashMap<>();

    static {
        row("DSL_SCHEMA", SCHEMA, ERROR);
        row("DSL_VERSION_UNSUPPORTED", SCHEMA, ERROR);
        row("FIELD_DUPLICATE", SEMANTIC, ERROR);
        row("FIELD_UNKNOWN", SEMANTIC, ERROR);
        row("FIELD_TYPE_MISMATCH", SEMANTIC, ERROR);
        row("FIELD_DOMAIN_INVALID", SEMANTIC, ERROR);
        row("ENUM_VALUE_UNKNOWN", SEMANTIC, ERROR);
        row("EXPR_TYPE_MISMATCH", SEMANTIC, ERROR);
        row("EXPR_ARITY", SEMANTIC, ERROR);
        row("EXPR_DEPTH", SEMANTIC, ERROR);
        row("BETWEEN_RANGE_INVALID", SEMANTIC, ERROR);
        row("REGEX_INVALID", SEMANTIC, ERROR);
        row("RESERVED_IDENTIFIER", SEMANTIC, ERROR);
        row("RULE_ID_DUPLICATE", SEMANTIC, ERROR);
        row("DERIVED_WRITE_ONLY", SEMANTIC, ERROR);
        row("DERIVED_REQUIRED", SCHEMA, ERROR);
        row("PROVENANCE_PARAGRAPH_MISSING", SEMANTIC, ERROR);
        row("PROVENANCE_QUOTE_MISMATCH", SEMANTIC, ERROR);
        row("PROVENANCE_ANALYST_FROM_MODEL", SEMANTIC, ERROR);
        row("PROVENANCE_PENDING_FROM_MODEL", SEMANTIC, ERROR);
        row("PROVENANCE_PENDING_AT_PUBLISH", SEMANTIC, ERROR);
        row("DERIVED_CYCLE", STRUCTURAL, ERROR);
        row("DERIVED_ORDER", STRUCTURAL, ERROR);
        row("DERIVED_NEVER_SET", STRUCTURAL, WARNING);
        row("FIELD_UNUSED", STRUCTURAL, WARNING);
        row("RULE_UNREACHABLE", STRUCTURAL, WARNING);
        row("RULE_OVERLAP_CONFLICT", STRUCTURAL, WARNING);
        row("REFER_PRECEDES_REJECT", STRUCTURAL, WARNING);
        row("CANDIDATE_NEVER_WINS", STRUCTURAL, WARNING);
        row("DIVISION_BY_UNGUARDED_FIELD", STRUCTURAL, WARNING);
        row("MISSING_FIELD_UNDER_NOT", STRUCTURAL, WARNING);
        row("PRIORITY_BAND_UNUSUAL", STRUCTURAL, INFO);
        row("NO_TERMINAL_APPROVE", STRUCTURAL, INFO);
    }

    /**
     * Every {@code invalid-*} file fails in Java with the code the reference reports, with that code's severity; an
     * error stops the validator in its own layer; and the codes the file lists under {@code alsoExpected} are there.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFixtures")
    void invalidFixtureFailsWithItsCodeSeverityAndLayerStop(String file, JsonNode fixture) {
        ValidationCode code = ValidationCode.valueOf(fixture.get("code").stringValue());

        List<Finding> findings = Validations.invalidFixture(code.name());

        assertThat(findings).filteredOn(finding -> finding.code() == code).isNotEmpty()
                .allMatch(finding -> finding.severity() == code.severity());
        if (code.severity() == Severity.ERROR) {
            assertThat(findings).extracting(finding -> finding.code().layer()).containsOnly(code.layer());
        }
        for (JsonNode also : fixture.path("alsoExpected")) {
            assertThat(findings).extracting(Finding::code).contains(ValidationCode.valueOf(also.stringValue()));
        }
    }

    @Test
    void everyValidatorCodeHasAnInvalidFixture() {
        Set<String> covered = Fixtures.invalidRuleSets().stream()
                .map(path -> Fixtures.json(path).get("code").stringValue())
                .collect(Collectors.toSet());

        assertThat(covered).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(ValidationCode.values()).map(Enum::name).toList());
    }

    @Test
    void codeSeverityAndLayerMatchTheDocumentTable() {
        Map<String, List<Object>> implemented = new LinkedHashMap<>();
        Arrays.stream(ValidationCode.values())
                .forEach(code -> implemented.put(code.name(), List.of(code.layer(), code.severity())));

        assertThat(implemented).containsExactlyInAnyOrderEntriesOf(DOCUMENT_TABLE);
    }

    static Stream<Arguments> invalidFixtures() {
        return Fixtures.invalidRuleSets().stream()
                .map(path -> arguments(path.getFileName().toString(), Fixtures.json(path)));
    }

    private static void row(String code, Layer layer, Severity severity) {
        DOCUMENT_TABLE.put(code, List.of(layer, severity));
    }
}
