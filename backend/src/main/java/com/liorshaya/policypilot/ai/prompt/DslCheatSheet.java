package com.liorshaya.policypilot.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The DSL cheat sheet the author prompt is given (Document 4, Prompt 1: Author: "a 60-line summary of Document 3
 * generated from the schema, so it cannot drift"). Every operator, type, action and provenance kind in it is read
 * out of the committed {@code schemas/ruleset-1.0.schema.json}: a schema change shows up in the prompt without
 * anyone editing prose.
 */
@Component
public final class DslCheatSheet {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SCHEMA = "schemas/ruleset-1.0.schema.json";

    private final String text;

    public DslCheatSheet() {
        this.text = write(read());
    }

    /** The sheet, ready to be placed in the prompt's {@code cheatsheet} placeholder. */
    public String text() {
        return text;
    }

    private static String write(JsonNode schema) {
        JsonNode defs = schema.get("$defs");
        List<String> lines = new ArrayList<>();
        lines.add("The RuleSet document has: dslVersion, id, name, language, description, fields, defaults, rules.");
        lines.add("");
        lines.add("FIELD TYPES: " + String.join(", ", enumOf(defs, "field", "type")) + ".");
        lines.add("A field has: name, type, and any of unit, values (for enum), required, derived, default,");
        lines.add("minimum, maximum, exclusiveMinimum, exclusiveMaximum, description, source.");
        lines.add("");
        lines.add("COMPARISON OPERATORS: " + String.join(", ", enumOf(defs, "comparison", "op")) + ".");
        lines.add("A comparison is {\"field\": name, \"op\": operator, \"value\": literal or expression};");
        lines.add("present and absent take no value, between takes a two-element array, in and not_in take a list.");
        lines.add("COMBINATORS: all, any, not, and {\"always\": true} for a rule that always applies.");
        lines.add("");
        lines.add("EXPRESSION FUNCTIONS: " + String.join(", ", enumOf(defs, "expression", "fn")) + ".");
        lines.add("An expression is {\"fn\": name, \"args\": [...]}; an argument is a number, a string,");
        lines.add("{\"field\": name} or another expression.");
        lines.add("");
        lines.add("ACTION TYPES: " + String.join(", ", enumOf(defs, "action", "type")) + ".");
        lines.add("decide takes outcome (" + String.join(", ", enumOf(defs, "action", "outcome")) + "),");
        lines.add("terminal and reason; set takes field and value; flag takes code and message.");
        lines.add("");
        lines.add("PROVENANCE KINDS: " + String.join(", ", provenanceKinds(defs)) + ".");
        lines.add("You may only produce quoted: {\"kind\": \"quoted\", \"paragraph\": n, \"quote\": \"...\",");
        lines.add("\"confidence\": 0..1}. analyst and pending are set by people and by the change flow.");
        lines.add("");
        lines.add("A rule has: id, label, priority, condition, actions, provenance, and optionally enabled, tags.");
        lines.add("defaults has: outcome, reason.");
        return String.join("\n", lines);
    }

    /** The values of an enum somewhere under one definition, found wherever the schema declares that property. */
    private static List<String> enumOf(JsonNode defs, String definition, String property) {
        List<String> values = new ArrayList<>();
        collectEnum(defs, defs.get(definition), property, values, new HashSet<>(Set.of(definition)));
        return values;
    }

    /**
     * @param seen the definitions already walked, because a condition refers to itself (all, any, not) and the
     *     walk would otherwise never end
     */
    private static void collectEnum(
            JsonNode defs, JsonNode node, String property, List<String> values, Set<String> seen) {
        if (node == null || !node.isObject()) {
            return;
        }
        // a local $ref is followed, because the schema keeps the outcomes and the provenance kinds in their own
        // definitions and the cheat sheet must say what they are
        JsonNode ref = node.get("$ref");
        if (ref != null) {
            String name = ref.asString().replace("#/$defs/", "");
            if (seen.add(name)) {
                collectEnum(defs, defs.get(name), property, values, seen);
            }
        }
        JsonNode properties = node.get("properties");
        if (properties != null && properties.get(property) != null) {
            addValues(defs, properties.get(property), values, seen);
        }
        for (String keyword : List.of("anyOf", "oneOf", "allOf", "prefixItems")) {
            JsonNode list = node.get(keyword);
            if (list != null) {
                list.forEach(child -> collectEnum(defs, child, property, values, seen));
            }
        }
        for (String keyword : List.of("items", "then", "else", "if")) {
            collectEnum(defs, node.get(keyword), property, values, seen);
        }
        if (properties != null) {
            properties.propertyNames()
                    .forEach(name -> collectEnum(defs, properties.get(name), property, values, seen));
        }
    }

    /** The values a property may take: an enum, a const, or the same through a local $ref. */
    private static void addValues(JsonNode defs, JsonNode property, List<String> values, Set<String> seen) {
        JsonNode ref = property.get("$ref");
        if (ref != null) {
            String name = ref.asString().replace("#/$defs/", "");
            if (seen.add(name)) {
                addValues(defs, defs.get(name), values, seen);
            }
        }
        JsonNode declared = property.get("enum");
        if (declared != null) {
            declared.forEach(value -> add(values, value.asString()));
        }
        JsonNode constant = property.get("const");
        if (constant != null) {
            add(values, constant.asString());
        }
    }

    private static List<String> provenanceKinds(JsonNode defs) {
        List<String> kinds = new ArrayList<>();
        collectEnum(defs, defs.get("provenance"), "kind", kinds, new HashSet<>(Set.of("provenance")));
        return kinds;
    }

    private static void add(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static JsonNode read() {
        try (InputStream stream = DslCheatSheet.class.getClassLoader().getResourceAsStream(SCHEMA)) {
            if (stream == null) {
                throw new IllegalStateException("no " + SCHEMA + " on the classpath");
            }
            return JSON.readTree(stream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + SCHEMA, e);
        }
    }
}
