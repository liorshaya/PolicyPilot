package com.liorshaya.policypilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.common.Csv;
import com.liorshaya.policypilot.decision.service.DecisionCsv;
import com.liorshaya.policypilot.decision.service.DecisionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The CSV exporter (Document 2, export: one row per trace step; Document 5, CSV and formula injection: "cells
 * starting with =, +, -, @ are prefixed with a single quote"; Security Test Plan, unit: CSV formula prefixing). The
 * decision it writes is the reference's own, {@code sample-decision.json}; the hostile labels are the ones an
 * analyst could type into the decision table.
 */
class DecisionCsvTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Document 5, CSV and formula injection. Expected: the cell reaches the spreadsheet with a leading quote
    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@SUM(A1)"})
    void aLabelThatReadsAsAFormulaIsPrefixedWithAQuote(String label) {
        String csv = DecisionCsv.of(decisionWithLabel(label));

        assertThat(firstRowCells(csv).get(6)).isEqualTo("'" + label);
    }

    // Document 5. Expected: a label that cannot be read as a formula is written as it is
    @Test
    void otherLabelsAreWrittenUnchanged() {
        String csv = DecisionCsv.of(decisionWithLabel("Reject applicants under 21"));

        assertThat(firstRowCells(csv).get(6)).isEqualTo("Reject applicants under 21");
    }

    // RFC 4180. Expected: the field in double quotes, inner quotes doubled
    @Test
    void quotesCommasAndNewlinesAreQuoted() {
        assertThat(firstRowCells(DecisionCsv.of(decisionWithLabel("age, income"))).get(6)).isEqualTo("\"age, income\"");
        assertThat(firstRowCells(DecisionCsv.of(decisionWithLabel("say \"no\""))).get(6))
                .isEqualTo("\"say \"\"no\"\"\"");
        assertThat(DecisionCsv.of(decisionWithLabel("two\nlines"))).contains("\"two\nlines\"");
    }

    // NFR-5 Hebrew support. Expected: the label of the fixture's first rule, unchanged, after the byte order mark
    @Test
    void hebrewLabelsAreWrittenUnchangedAfterTheByteOrderMark() {
        String label = Fixtures.lendingV1().withArray("rules").get(0).required("label").stringValue();

        String csv = DecisionCsv.of(decisionWithLabel(label));

        assertThat(csv).startsWith(Csv.BYTE_ORDER_MARK);
        assertThat(firstRowCells(csv).get(6)).isEqualTo(label);
    }

    // Document 2, export. Expected: the documented header and one row per step of sample-decision.json
    @Test
    void theHeaderAndOneRowPerTraceStep() {
        JsonNode sample = Fixtures.json("policies/consumer-lending/sample-decision.json");

        String csv = DecisionCsv.of(decision((ObjectNode) sample));

        List<String> lines = List.of(csv.split("\r\n"));
        assertThat(lines.getFirst()).isEqualTo(Csv.BYTE_ORDER_MARK
                + "decision_id,ruleset_version,outcome,deciding_rule_id,step,rule_id,label,priority,status,"
                + "comparisons,actions");
        assertThat(lines).hasSize(sample.required("trace").size() + 1);
        assertThat(firstRowCells(csv).subList(1, 6)).containsExactly("consumer-lending v1",
                sample.required("outcome").stringValue(), sample.required("decidingRuleId").stringValue(), "1",
                sample.required("trace").get(0).required("ruleId").stringValue());
    }

    /** The cells of the first row after the header, split on commas outside quotes. */
    private static List<String> firstRowCells(String csv) {
        String row = csv.split("\r\n")[1];
        return List.of(row.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1));
    }

    /** The reference's decision of case 17 with one rule's label replaced by what the test is about. */
    private static DecisionView decisionWithLabel(String label) {
        ObjectNode sample = (ObjectNode) Fixtures.json("policies/consumer-lending/sample-decision.json");
        ((ObjectNode) sample.required("trace").get(0)).put("label", label);
        return decision(sample);
    }

    private static DecisionView decision(ObjectNode sample) {
        return new DecisionView(UUID.fromString("0f4c1c9e-0000-4000-8000-000000000017"), 17, "consumer-lending", 1,
                UUID.fromString("0f4c1c9e-0000-4000-8000-000000000001"), ApiIntegrationTest.START, 412,
                (ObjectNode) JSON.readTree(sample.toString()));
    }
}
