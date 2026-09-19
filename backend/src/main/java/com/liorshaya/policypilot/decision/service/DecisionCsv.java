package com.liorshaya.policypilot.decision.service;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A decision as CSV (Document 2, export: one row per trace step; Document 5, CSV and formula injection). A cell that
 * starts with {@code =}, {@code +}, {@code -} or {@code @} is prefixed with a single quote, so a spreadsheet reads it
 * as text and never as a formula; fields are quoted per RFC 4180, and the file starts with a byte order mark so
 * spreadsheets read the Hebrew labels as UTF-8.
 */
public final class DecisionCsv {

    /** The byte order mark that makes spreadsheet programs read the file as UTF-8. */
    public static final String BYTE_ORDER_MARK = "﻿";

    static final List<String> HEADER = List.of("decision_id", "ruleset_version", "outcome", "deciding_rule_id",
            "step", "rule_id", "label", "priority", "status", "comparisons", "actions");

    private static final String FORMULA_CHARACTERS = "=+-@";

    private DecisionCsv() {}

    /** The decision as a CSV document: the header, then one row per trace step. */
    public static String of(DecisionView decision) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK).append(row(HEADER));
        JsonNode trace = decision.decision().path("trace");
        for (int step = 0; step < trace.size(); step++) {
            JsonNode traceStep = trace.get(step);
            csv.append(row(List.of(
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
                    json(traceStep, "actions"))));
        }
        return csv.toString();
    }

    /** One cell as it is written: formula-prefixed when needed, then quoted per RFC 4180. */
    private static String cell(String value) {
        String prefixed = !value.isEmpty() && FORMULA_CHARACTERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (prefixed.contains("\"") || prefixed.contains(",") || prefixed.contains("\n") || prefixed.contains("\r")) {
            return '"' + prefixed.replace("\"", "\"\"") + '"';
        }
        return prefixed;
    }

    private static String row(List<String> cells) {
        return String.join(",", cells.stream().map(DecisionCsv::cell).toList()) + "\r\n";
    }

    private static String json(JsonNode step, String property) {
        JsonNode value = step.path(property);
        return value.isMissingNode() || value.isNull() ? "" : value.toString();
    }
}
