package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The argument validators of the chat's tools (Document 2, Tools available to the answer prompt; Document 5: model
 * output is untrusted input, parsed and checked before it is used, never used to address another sandbox's data).
 * An application number is one positive whole number of at most nine digits; a simulation's overrides name declared
 * fields of the version. Values are the engine's to judge, as they are for {@code POST .../simulate}.
 */
public final class ToolArguments {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NUMBER = "applicationNumber";
    private static final String OVERRIDES = "overrides";
    private static final long LARGEST = 999_999_999L;

    private ToolArguments() {}

    /** {@code {"applicationNumber": n}} and nothing else. */
    public static int applicationNumber(String arguments) {
        return applicationNumber(arguments, new String[0]);
    }

    /** The application number of arguments that may also carry the properties named. */
    public static int applicationNumber(String arguments, String... alsoAllowed) {
        JsonNode node = object(arguments);
        Set<String> allowed = Arrays.stream(alsoAllowed).collect(Collectors.toSet());
        allowed.add(NUMBER);
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new ToolArgumentException("unexpected argument " + name);
            }
        }
        JsonNode number = node.get(NUMBER);
        if (number == null || !number.isIntegralNumber() || number.longValue() < 1 || number.longValue() > LARGEST) {
            throw new ToolArgumentException("applicationNumber must be a whole number from 1 to " + LARGEST);
        }
        return number.intValue();
    }

    /** The overrides of {@code {"applicationNumber": n, "overrides": {...}}}: a non-empty object of declared fields. */
    public static ObjectNode overrides(String arguments, RuleSet ruleSet) {
        JsonNode node = object(arguments);
        applicationNumber(arguments, OVERRIDES);
        JsonNode overrides = node.get(OVERRIDES);
        if (overrides == null || !overrides.isObject() || overrides.isEmpty()) {
            throw new ToolArgumentException("overrides must name at least one field to change");
        }
        Set<String> declared = ruleSet.fields().stream().map(Field::name).collect(Collectors.toSet());
        for (String name : overrides.propertyNames()) {
            if (!declared.contains(name)) {
                throw new ToolArgumentException("the rule set declares no field named " + name);
            }
        }
        return (ObjectNode) overrides;
    }

    private static JsonNode object(String arguments) {
        JsonNode node;
        try {
            node = JSON.readTree(arguments);
        } catch (JacksonException e) {
            throw new ToolArgumentException("the arguments are not JSON");
        }
        if (node == null || !node.isObject()) {
            throw new ToolArgumentException("the arguments must be one JSON object");
        }
        return node;
    }
}
