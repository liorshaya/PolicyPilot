package com.liorshaya.policypilot.decision.service;

import com.liorshaya.policypilot.common.Csv;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A decision as CSV (Document 2, export: one row per trace step; Document 5, CSV and formula injection), written by
 * {@link Csv}: formula-prefixed, quoted per RFC 4180, and after a byte order mark so spreadsheets read the Hebrew
 * labels as UTF-8.
 */
public final class DecisionCsv {

    static final List<String> HEADER = List.of("decision_id", "ruleset_version", "outcome", "deciding_rule_id",
            "step", "rule_id", "label", "priority", "status", "comparisons", "actions");

    private DecisionCsv() {}

    /** The decision as a CSV document: the header, then one row per trace step. */
    public static String of(DecisionView decision) {
        List<List<String>> rows = new ArrayList<>();
        JsonNode trace = decision.decision().path("trace");
        for (int step = 0; step < trace.size(); step++) {
            JsonNode traceStep = trace.get(step);
            rows.add(List.of(
                    decision.id().toString(),
                    decision.rulesetId() + " v" + decision.versionNo(),
                    decision.decision().path("outcome").asString(""),
                    decision.decision().path("decidingRuleId").asString(""),
                    String.valueOf(step + 1),
                    traceStep.path("ruleId").asString(""),
                    traceStep.path("label").asString(""),
                    traceStep.path("priority").asString(""),
                    traceStep.path("status").asString(""),
                    json(traceStep, "comparisons"),
                    json(traceStep, "actions")));
        }
        return Csv.document(HEADER, rows);
    }

    private static String json(JsonNode step, String property) {
        JsonNode value = step.path(property);
        return value.isMissingNode() || value.isNull() ? "" : value.toString();
    }
}
