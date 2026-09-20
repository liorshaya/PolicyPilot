package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The provider's variant of a canonical schema (Document 4, Output discipline; Document 6, Unit level). The
 * canonical schema is the committed {@code schemas/ruleset-1.0.schema.json}, so the test is about the real
 * document the author prompt must answer, not a toy.
 */
class ProviderSchemaVariantTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ObjectNode canonical() {
        try (InputStream stream = ProviderSchemaVariantTest.class.getClassLoader()
                .getResourceAsStream("schemas/ruleset-1.0.schema.json")) {
            return (ObjectNode) JSON.readTree(stream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Every node that is a schema, walked the way a schema is built: the values of {@code properties} and
     * {@code $defs} are schemas, the container that holds them is not, so a DSL field named {@code minimum}
     * is never mistaken for the keyword of the same name.
     */
    private static List<JsonNode> everySchemaIn(JsonNode schema) {
        List<JsonNode> all = new ArrayList<>();
        collectSchema(schema, all);
        return all;
    }

    private static void collectSchema(JsonNode schema, List<JsonNode> all) {
        if (!schema.isObject()) {
            return;
        }
        all.add(schema);
        for (String container : List.of("properties", "$defs", "definitions")) {
            JsonNode members = schema.get(container);
            if (members != null) {
                members.propertyNames().forEach(name -> collectSchema(members.get(name), all));
            }
        }
        for (String nested : List.of("items", "contains", "additionalItems", "propertyNames",
                "if", "then", "else", "not")) {
            JsonNode child = schema.get(nested);
            if (child != null) {
                collectSchema(child, all);
            }
        }
        for (String list : List.of("anyOf", "allOf", "oneOf", "prefixItems")) {
            JsonNode members = schema.get(list);
            if (members != null) {
                members.forEach(child -> collectSchema(child, all));
            }
        }
    }

    @Test
    void dropsEveryKeywordAStrictModeWouldRefuse() {
        ObjectNode variant = ProviderSchemaVariant.of(canonical());

        for (JsonNode schema : everySchemaIn(variant)) {
            assertThat(schema.propertyNames())
                    .doesNotContain("minLength", "maxLength", "pattern", "minimum", "maximum",
                            "minItems", "maxItems", "if", "then", "oneOf", "not", "default");
        }
    }

    @Test
    void leavesTheCanonicalSchemaUntouched() {
        ObjectNode canonical = canonical();
        String before = canonical.toString();

        ProviderSchemaVariant.of(canonical);

        assertThat(canonical.toString()).isEqualTo(before);
        // and the canonical file still carries what the local validator enforces
        assertThat(before).contains("\"maxLength\"").contains("\"pattern\"");
    }

    @Test
    void requiresEveryPropertyOfEveryObject() {
        ObjectNode variant = ProviderSchemaVariant.of(canonical());

        for (JsonNode schema : everySchemaIn(variant)) {
            JsonNode properties = schema.get("properties");
            if (properties != null) {
                List<String> declared = new ArrayList<>();
                properties.propertyNames().forEach(declared::add);
                List<String> required = new ArrayList<>();
                schema.get("required").forEach(name -> required.add(name.asString()));
                assertThat(required).containsExactlyInAnyOrderElementsOf(declared);
                assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
            }
        }
    }

    @Test
    void makesAPropertyTheCanonicalSchemaLeftOptionalNullable() {
        ObjectNode canonical = (ObjectNode) JSON.readTree("""
                {"type":"object","required":["id"],
                 "properties":{"id":{"type":"string"},"unit":{"type":"string"},"tags":{"type":"array"}}}""");

        ObjectNode variant = ProviderSchemaVariant.of(canonical);

        assertThat(variant.get("properties").get("id").get("type").asString()).isEqualTo("string");
        assertThat(variant.get("properties").get("unit").get("type").toString())
                .isEqualTo("[\"string\",\"null\"]");
        assertThat(variant.get("properties").get("tags").get("type").toString())
                .isEqualTo("[\"array\",\"null\"]");
    }

    @Test
    void leavesAPropertyThatCouldAlreadyBeNullAlone() {
        ObjectNode canonical = (ObjectNode) JSON.readTree("""
                {"type":"object","properties":{"paragraph":{"type":["integer","null"]},"free":{}}}""");

        ObjectNode variant = ProviderSchemaVariant.of(canonical);

        assertThat(variant.get("properties").get("paragraph").get("type").toString())
                .isEqualTo("[\"integer\",\"null\"]");
        assertThat(variant.get("properties").get("free").propertyNames()).isEmpty();
    }

    @Test
    void keepsAChoiceTheStrictModeCanExpress() {
        // provenance, condition and action are all oneOf in the canonical schema, and all three must survive
        ObjectNode variant = ProviderSchemaVariant.of(canonical());

        for (String definition : List.of("provenance", "condition", "action")) {
            JsonNode derived = variant.get("$defs").get(definition);
            assertThat(derived.propertyNames()).doesNotContain("oneOf");
            assertThat(derived.get("anyOf")).as(definition).isNotNull();
            assertThat(derived.get("anyOf")).isNotEmpty();
        }
    }

    @Test
    void namesTheTypeWhereOnlyAConstOrAnEnumSaidIt() {
        ObjectNode canonical = (ObjectNode) JSON.readTree("""
                {"type":"object","properties":{
                  "kind":{"const":"quoted"},
                  "op":{"enum":["eq","ne"]},
                  "paragraph":{"type":"integer"},
                  "terminal":{"const":true},
                  "weight":{"enum":[1,2]}}}""");

        ObjectNode variant = ProviderSchemaVariant.of(canonical);

        JsonNode properties = variant.get("properties");
        assertThat(properties.get("kind").get("type").toString()).isEqualTo("[\"string\",\"null\"]");
        assertThat(properties.get("op").get("type").toString()).isEqualTo("[\"string\",\"null\"]");
        assertThat(properties.get("terminal").get("type").toString()).isEqualTo("[\"boolean\",\"null\"]");
        assertThat(properties.get("weight").get("type").toString()).isEqualTo("[\"integer\",\"null\"]");
    }

    @Test
    void everySchemaOfTheCanonicalDocumentEndsWithATypeOrAReference() {
        ObjectNode variant = ProviderSchemaVariant.of(canonical());

        // a strict mode refuses a schema whose type it has to infer, at the root as much as anywhere
        assertThat(variant.get("type").asString()).isEqualTo("object");
        for (JsonNode schema : everySchemaIn(variant)) {
            boolean saysWhatItIs = schema.has("type") || schema.has("$ref") || schema.has("anyOf");
            assertThat(saysWhatItIs)
                    .as("every schema says what it is: %s", schema)
                    .isTrue();
        }
    }

    @Test
    void stripsTheNullsTheProviderHadToWrite() {
        JsonNode answered = JSON.readTree("""
                {"id":"consumer-lending","description":null,
                 "fields":[{"name":"age","unit":null,"values":null}],
                 "rules":[{"id":"R-100","tags":null,"provenance":{"confidence":0.9,"note":null}}]}""");

        JsonNode stripped = ProviderSchemaVariant.stripNulls(answered);

        assertThat(stripped.toString())
                .isEqualTo("{\"id\":\"consumer-lending\",\"fields\":[{\"name\":\"age\"}],"
                        + "\"rules\":[{\"id\":\"R-100\",\"provenance\":{\"confidence\":0.9}}]}");
    }

    @Test
    void keepsWhatTheModelActuallyAnswered() {
        JsonNode answered = JSON.readTree("{\"enabled\":false,\"count\":0,\"label\":\"\"}");

        // false, zero and the empty string are answers; only null is absence
        assertThat(ProviderSchemaVariant.stripNulls(answered).toString())
                .isEqualTo("{\"enabled\":false,\"count\":0,\"label\":\"\"}");
    }
}
