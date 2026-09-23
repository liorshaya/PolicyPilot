package com.liorshaya.policypilot.ai.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

    /**
     * The canonical schema with every reference into another schema file copied in, because a structured-output
     * schema is one document: the other file's definitions join this one's {@code $defs}, and the reference points at
     * them there ({@code ruleset-1.0.json#/$defs/rule} becomes {@code #/$defs/rule}). The canonical node is not
     * touched.
     *
     * @param schemaOf the schema file a reference names, by the name the reference gives it
     */
    public static ObjectNode bundled(ObjectNode canonical, Function<String, ObjectNode> schemaOf) {
        ObjectNode bundled = canonical.deepCopy();
        Map<String, ObjectNode> files = new LinkedHashMap<>();
        pointInside(bundled, files, schemaOf);
        ObjectNode definitions = bundled.has("$defs") ? (ObjectNode) bundled.get("$defs") : bundled.putObject("$defs");
        files.forEach((file, schema) -> schema.required("$defs").properties().forEach(definition -> {
            if (definitions.has(definition.getKey())) {
                throw new IllegalStateException("both the schema and " + file + " define " + definition.getKey());
            }
            definitions.set(definition.getKey(), definition.getValue());
        }));
        return bundled;
    }

    /** Every reference into another file, pointed at the definition it names in this one; the files it read. */
    private static void pointInside(JsonNode node, Map<String, ObjectNode> files,
            Function<String, ObjectNode> schemaOf) {
        if (node instanceof ObjectNode object) {
            JsonNode reference = object.get("$ref");
            if (reference != null && reference.isString() && reference.asString().indexOf('#') > 0) {
                String[] parts = reference.asString().split("#", 2);
                files.computeIfAbsent(parts[0], schemaOf);
                object.put("$ref", "#" + parts[1]);
            }
            object.properties().forEach(member -> pointInside(member.getValue(), files, schemaOf));
        } else if (node instanceof ArrayNode array) {
            array.forEach(element -> pointInside(element, files, schemaOf));
        }
    }

    private static JsonNode convert(JsonNode node) {
        if (!node.isObject()) {
            return node;
        }
        ObjectNode schema = (ObjectNode) node;
        // a strict mode knows anyOf but not oneOf; the canonical schema still says oneOf, and it is what
        // validates the answer, so the variant only weakens "exactly one of" to "one of"
        JsonNode oneOf = schema.get("oneOf");
        if (oneOf != null && !schema.has("anyOf")) {
            schema.set("anyOf", oneOf);
        }
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
            // a schema with properties is an object, and a strict mode wants that said rather than implied
            if (!schema.has("type")) {
                schema.put("type", "object");
            }
        }
        nameTheType(schema);
        return schema;
    }

    /**
     * A strict mode wants a {@code type} on every schema, and the canonical schema leaves it out where a
     * {@code const} or an {@code enum} already says everything: the type is read back out of those values.
     */
    private static void nameTheType(ObjectNode schema) {
        if (schema.has("type") || schema.has("$ref") || schema.has("anyOf")) {
            return;
        }
        JsonNode values = schema.has("const") ? schema.get("const") : schema.get("enum");
        if (values == null) {
            return;
        }
        JsonNode first = values.isArray() ? values.get(0) : values;
        if (first == null) {
            return;
        }
        if (first.isBoolean()) {
            schema.put("type", "boolean");
        } else if (first.isIntegralNumber()) {
            schema.put("type", "integer");
        } else if (first.isNumber()) {
            schema.put("type", "number");
        } else if (first.isString()) {
            schema.put("type", "string");
        }
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
        JsonNode reference = property.get("$ref");
        if (reference != null) {
            // a reference has no type to add null to, so the property becomes "that or null"
            property.remove("$ref");
            ArrayNode either = property.putArray("anyOf");
            either.addObject().set("$ref", reference);
            either.addObject().put("type", "null");
            return;
        }
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
