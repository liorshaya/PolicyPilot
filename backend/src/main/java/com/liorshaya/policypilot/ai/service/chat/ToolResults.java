package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.prompt.Sections;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * How a tool answers the model (Document 4, Prompt 4: tool results enter the context as {@code <tool_result
 * id="d:17">}, citable like chunks): a result in a section named by the id it may be cited by, or a refusal in a
 * section that says why, the body escaped either way so it cannot close the section (Document 5, RT-08).
 */
public final class ToolResults {

    private static final Pattern OUTCOME = Pattern.compile("<tool_result (?:id=\"([^\"]+)\"|error=\"([^\"]+)\")>");

    private ToolResults() {}

    public static String result(String id, JsonNode body) {
        return "<tool_result id=\"" + id + "\">" + Sections.escape(body.toString()) + "</tool_result>";
    }

    public static String refusal(String reason, String message) {
        return "<tool_result error=\"" + reason + "\">" + Sections.escape(message) + "</tool_result>";
    }

    /** What a call's record keeps: the id its result may be cited by, or {@code refused:<reason>}, not the body. */
    public static String outcomeOf(String result) {
        Matcher outcome = OUTCOME.matcher(result);
        if (!outcome.lookingAt()) {
            throw new IllegalStateException("a tool result without its section: " + result);
        }
        return outcome.group(1) != null ? outcome.group(1) : "refused:" + outcome.group(2);
    }

    /** Document 4: {@code sim:d17:has_guarantor=true}, the overridden fields in name order. */
    public static String overridesText(ObjectNode overrides) {
        TreeMap<String, JsonNode> sorted = new TreeMap<>();
        overrides.properties().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (entry.getValue().isString() ? entry.getValue().asString()
                        : entry.getValue().toString()))
                .collect(Collectors.joining(","));
    }
}
