package com.liorshaya.policypilot.ai.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The provider's variant of a canonical JSON Schema (Document 4, Output discipline; Document 6, Unit level:
 * "optional properties become nullable, unsupported keywords dropped, nulls stripped on the way back").
 *
 * <p>A structured-output mode is stricter than JSON Schema: every property must be required, no object may allow
 * extra properties, and most validation keywords are ignored or refused. The canonical schema in
 * {@code schemas/} stays the authority — it is what the answer is validated against locally — and this class
 * derives what the provider will accept, without ever changing the canonical file.
 */
public final class ProviderSchemaVariant {

    /**
     * Keywords a strict structured-output mode does not accept. They are dropped from the variant only; the
     * canonical schema still enforces every one of them when the answer comes back.
     */
    private static final Set<String> UNSUPPORTED = Set.of(
            "minLength", "maxLength", "pattern", "format",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "minItems", "maxItems", "uniqueItems", "minProperties", "maxProperties",
            "if", "then", "else", "not", "oneOf", "allOf", "dependentSchemas", "dependentRequired",
            "default", "examples", "$comment", "contentEncoding", "contentMediaType", "patternProperties");

    /** Keywords whose value is itself a schema. */
    private static final Set<String> NESTED_SCHEMA = Set.of("items", "additionalItems", "contains", "propertyNames");

    /** Keywords whose value is a map of name to schema. */
    private static final Set<String> SCHEMA_MAP = Set.of("properties", "$defs", "definitions");

    /** Keywords whose value is a list of schemas. */
    private static final Set<String> SCHEMA_LIST = Set.of("anyOf", "prefixItems");

    private ProviderSchemaVariant() {}

    /** The variant of a canonical schema; the canonical node is not touched. */
    public static ObjectNode of(ObjectNode canonical) {
        return (ObjectNode) convert(canonical.deepCopy());
    }

    private static JsonNode convert(JsonNode node) {
        if (!node.isObject()) {
            return node;
        }
        ObjectNode schema = (ObjectNode) node;
        UNSUPPORTED.forEach(schema::remove);

        for (String keyword : SCHEMA_MAP) {
            JsonNode map = schema.get(keyword);
            if (map instanceof ObjectNode members) {
                members.propertyNames().forEach(name -> members.set(name, convert(members.get(name))));
            }
        }
        for (String keyword : NESTED_SCHEMA) {
            JsonNode nested = schema.get(keyword);
            if (nested != null) {
                schema.set(keyword, convert(nested));
            }
        }
        for (String keyword : SCHEMA_LIST) {
            JsonNode list = schema.get(keyword);
            if (list instanceof ArrayNode members) {
                for (int i = 0; i < members.size(); i++) {
                    members.set(i, convert(members.get(i)));
                }
            }
        }

        JsonNode properties = schema.get("properties");
        if (properties instanceof ObjectNode declared) {
            requireEveryProperty(schema, declared);
            schema.put("additionalProperties", false);
        }
        return schema;
    }

    /**
     * Every declared property is listed as required, and a property the canonical schema left optional is made
     * nullable, so "absent" is expressible as "null" and the provider's own check still passes.
     */
    private static void requireEveryProperty(ObjectNode schema, ObjectNode properties) {
        Set<String> wasRequired = requiredNames(schema.get("required"));
        ArrayNode required = schema.putArray("required");
        properties.propertyNames().forEach(name -> {
            required.add(name);
            if (!wasRequired.contains(name) && properties.get(name) instanceof ObjectNode property) {
                allowNull(property);
            }
        });
    }

    private static Set<String> requiredNames(JsonNode required) {
        if (!(required instanceof ArrayNode names)) {
            return Set.of();
        }
        List<String> declared = new ArrayList<>();
        names.forEach(name -> declared.add(name.asString()));
        return Set.copyOf(declared);
    }

    private static void allowNull(ObjectNode property) {
        JsonNode type = property.get("type");
        if (type == null || type.isArray() && containsNull((ArrayNode) type)) {
            return;
        }
        if (type.isArray()) {
            ((ArrayNode) type).add("null");
            return;
        }
        ArrayNode both = property.putArray("type");
        both.add(type.asString());
        both.add("null");
    }

    private static boolean containsNull(ArrayNode type) {
        for (JsonNode name : type) {
            if ("null".equals(name.asString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The answer with every null-valued property removed, so a document the provider had to fill with nulls
     * validates against the canonical schema that never asked for them.
     */
    public static JsonNode stripNulls(JsonNode answer) {
        if (answer instanceof ObjectNode object) {
            List<String> nulls = new ArrayList<>();
            object.propertyNames().forEach(name -> {
                JsonNode value = object.get(name);
                if (value.isNull()) {
                    nulls.add(name);
                } else {
                    stripNulls(value);
                }
            });
            nulls.forEach(object::remove);
        } else if (answer instanceof ArrayNode array) {
            array.forEach(ProviderSchemaVariant::stripNulls);
        }
        return answer;
    }
}
