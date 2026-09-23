package com.liorshaya.policypilot.ai.service;

import com.liorshaya.policypilot.ai.prompt.PromptDefinition;
import com.liorshaya.policypilot.ai.prompt.Sections;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.patch.PatchProblem;
import com.liorshaya.policypilot.rules.validation.Finding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The repair user prompt of Document 4 (Repair Loop), for the two prompts that are repaired, author and change: only
 * the errors go back, each in the reporting shape of Document 3, with the full text of the paragraphs the failing
 * rules quote, which is what makes the next attempt succeed almost always (Document 4, Error list shape).
 */
final class RepairPrompt {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RepairPrompt() {}

    /** One error as the repair prompt lists it; only errors are ever sent back, so its severity is always error. */
    record Error(String code, String path, String message, List<String> ruleIds, List<String> fieldNames) {

        static Error of(Finding finding) {
            return new Error(finding.code().name(), finding.path(), finding.message(), finding.ruleIds(),
                    finding.fieldNames());
        }

        static Error of(PatchProblem problem) {
            return new Error(problem.code().name(), problem.path(), problem.message(), problem.ruleIds(),
                    problem.fieldNames());
        }
    }

    /**
     * @param rules the rules of the answer that failed, which say which paragraphs the failing ones quote
     * @param document the answer that failed, as the prompt shows it back
     */
    static String render(PromptDefinition repair, List<Error> errors, Iterable<JsonNode> rules,
            List<PolicyVersionRef.Paragraph> paragraphs, String document) {
        return repair.user().render(Map.of(
                "count", String.valueOf(errors.size()),
                "errors", errorList(errors),
                "paragraphTexts", paragraphTexts(errors, rules, paragraphs),
                "document", document));
    }

    private static String errorList(List<Error> errors) {
        StringBuilder text = new StringBuilder();
        for (Error error : errors) {
            ObjectNode entry = JSON.createObjectNode();
            entry.put("code", error.code());
            entry.put("severity", "error");
            entry.put("path", error.path());
            entry.put("message", error.message());
            entry.set("ruleIds", JSON.valueToTree(error.ruleIds()));
            entry.set("fieldNames", JSON.valueToTree(error.fieldNames()));
            text.append(entry).append('\n');
        }
        return text.toString().stripTrailing();
    }

    /** The full text of every paragraph a failing rule quotes; every paragraph when none of them quotes one. */
    private static String paragraphTexts(List<Error> errors, Iterable<JsonNode> rules,
            List<PolicyVersionRef.Paragraph> paragraphs) {
        Set<String> failing = new LinkedHashSet<>();
        errors.forEach(error -> failing.addAll(error.ruleIds()));
        List<Integer> cited = new ArrayList<>();
        for (JsonNode rule : rules) {
            JsonNode paragraph = rule.path("provenance").path("paragraph");
            if (failing.contains(rule.path("id").asString("")) && paragraph.isInt()
                    && !cited.contains(paragraph.asInt())) {
                cited.add(paragraph.asInt());
            }
        }
        List<PolicyVersionRef.Paragraph> shown = cited.isEmpty() ? paragraphs
                : paragraphs.stream().filter(paragraph -> cited.contains(paragraph.index())).toList();
        return Sections.numbered(shown);
    }
}
