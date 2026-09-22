package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Document 4: "Citation accuracy --- answers whose markers are all valid and include the expected source". An
 * answer is counted when both halves hold: every marker it wrote resolves against the version it was asked about,
 * and every marker the labeled question expects is among them.
 *
 * <p>A marker resolves when it names a paragraph the policy has, a rule the version has, or a decision or
 * simulation a tool actually returned in that turn --- which the recording keeps as the calls the model made. A
 * rule id or a paragraph the version does not have is exactly what this is here to catch, and it is the same
 * grammar {@code MarkerResolver} enforces when the answer streams.
 */
final class CitationScoring {

    /** Document 4's four marker kinds, as {@code MarkerResolver} writes them. */
    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[(p:[1-9]\\d{0,3}|r:R-\\d{2,4}|d:[1-9]\\d{0,8}"
                    + "|sim:d[1-9]\\d{0,8}:[a-z][a-z0-9_]*=[^\\],\\s]+(?:,[a-z][a-z0-9_]*=[^\\],\\s]+)*)]]");
    /** The labeled set writes {@code [[sim:*]]} for "any simulation marker" (fixtures/eval/questions.json). */
    private static final String ANY_SIMULATION = "[[sim:*]]";

    private CitationScoring() {}

    /** One answer, and why it was or was not counted. */
    record Scored(String questionId, boolean counted, String note) {}

    /**
     * @param question one labeled question, with its {@code expectedMarkers}
     * @param answer the raw text the model streamed, before the resolver ran
     * @param steps the tool calls the recording kept, in order
     * @param ruleSet the version the question was asked about
     * @param paragraphs how many paragraphs its policy has
     */
    static Scored score(JsonNode question, String answer, JsonNode steps, RuleSet ruleSet, int paragraphs) {
        String id = question.required("id").asString();
        List<String> written = markersOf(answer);
        Set<String> suppliable = suppliable(steps, ruleSet, paragraphs);

        List<String> invalid = written.stream().filter(marker -> !suppliable.contains(marker)).toList();
        if (!invalid.isEmpty()) {
            return new Scored(id, false, "cited " + String.join(", ", invalid)
                    + ", which this version cannot supply");
        }
        List<String> missing = new ArrayList<>();
        for (JsonNode expected : question.required("expectedMarkers")) {
            String marker = expected.asString();
            boolean present = ANY_SIMULATION.equals(marker)
                    ? written.stream().anyMatch(cited -> cited.startsWith("sim:"))
                    : written.contains(marker.substring(2, marker.length() - 2));
            if (!present) {
                missing.add(marker);
            }
        }
        return missing.isEmpty()
                ? new Scored(id, true, "cited " + String.join(", ", written))
                : new Scored(id, false, "did not cite " + String.join(", ", missing));
    }

    /** The markers an answer wrote, in order and each once, without their brackets. */
    private static List<String> markersOf(String answer) {
        List<String> markers = new ArrayList<>();
        Matcher found = MARKER.matcher(answer);
        while (found.find()) {
            if (!markers.contains(found.group(1))) {
                markers.add(found.group(1));
            }
        }
        return markers;
    }

    /** Every id this turn could have supplied: the version's rules, the policy's paragraphs, the tools' results. */
    private static Set<String> suppliable(JsonNode steps, RuleSet ruleSet, int paragraphs) {
        Set<String> ids = new LinkedHashSet<>();
        ruleSet.rules().stream().map(Rule::id).forEach(ruleId -> ids.add("r:" + ruleId));
        for (int paragraph = 1; paragraph <= paragraphs; paragraph++) {
            ids.add("p:" + paragraph);
        }
        for (JsonNode step : steps) {
            String tool = step.path("tool").asString("");
            JsonNode arguments = argumentsOf(step);
            int application = arguments.path("applicationNumber").asInt(0);
            if (application > 0 && "getDecision".equals(tool)) {
                ids.add("d:" + application);
            }
            if (application > 0 && "simulate".equals(tool)) {
                // the id names its overrides in name order, as ToolResults writes it
                ids.add("sim:d" + application + ":" + overridesText(arguments.path("overrides")));
            }
        }
        return ids;
    }

    private static JsonNode argumentsOf(JsonNode step) {
        JsonNode arguments = step.path("arguments");
        return arguments.isString() ? JSON_ARGUMENTS.readTree(arguments.asString()) : arguments;
    }

    private static final tools.jackson.databind.json.JsonMapper JSON_ARGUMENTS =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private static String overridesText(JsonNode overrides) {
        List<String> pairs = new ArrayList<>();
        overrides.properties().forEach(entry -> pairs.add(entry.getKey() + "="
                + (entry.getValue().isString() ? entry.getValue().asString() : entry.getValue().toString())));
        pairs.sort(String::compareTo);
        return String.join(",", pairs);
    }
}
