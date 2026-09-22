package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.rules.model.Field;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The argument validators of the chat's tools (Document 2, Tools available to the answer prompt; Document 5: model
 * output is untrusted input, parsed and checked before it is used, never used to address another sandbox's data).
 * An application number is one positive whole number of at most nine digits; a simulation's overrides name declared
 * fields of the version; a rule listing's tag is one plain word, the shape a rule set's tags have. Values are the
 * engine's to judge, as they are for {@code POST .../simulate}.
 */
public final class ToolArguments {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NUMBER = "applicationNumber";
    private static final String OVERRIDES = "overrides";
    private static final String TAG = "tag";
    private static final long LARGEST = 999_999_999L;
    /** A tag as Document 3 writes them: a short plain word, which is also all a listing may be narrowed by. */
    private static final Pattern PLAIN_WORD = Pattern.compile("[a-z][a-z0-9_]{0,39}");

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

    /** {@code {}} and nothing else: the arguments of a tool that takes none. */
    public static void none(String arguments) {
        JsonNode node = object(arguments);
        for (String name : node.propertyNames()) {
            throw new ToolArgumentException("unexpected argument " + name);
        }
    }

    /** The tag of {@code {"tag": "credit_history"}}, or {@code null} when the listing was asked for unnarrowed. */
    public static @Nullable String tag(String arguments) {
        JsonNode node = object(arguments);
        for (String name : node.propertyNames()) {
            if (!TAG.equals(name)) {
                throw new ToolArgumentException("unexpected argument " + name);
            }
        }
        JsonNode tag = node.get(TAG);
        if (tag == null || tag.isNull()) {
            return null;
        }
        if (!tag.isString() || !PLAIN_WORD.matcher(tag.stringValue()).matches()) {
            throw new ToolArgumentException("tag must be one plain word, as a rule set's tags are written");
        }
        return tag.stringValue();
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
