package com.liorshaya.policypilot.support;

import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Starts from a rule set document and changes one thing, so a test shows only what matters to it (Document 6,
 * Fixtures and builders): {@code RuleSetBuilder.lendingV1().rule("R-320", r -> r.put("priority", 210)).build()}.
 * It edits the JSON tree, so it can also build documents the schema rejects.
 */
public final class RuleSetBuilder {

    private final ObjectNode document;

    private RuleSetBuilder(ObjectNode document) {
        this.document = document;
    }

    /** The published lending rule set, version 1. */
    public static RuleSetBuilder lendingV1() {
        return new RuleSetBuilder(Fixtures.lendingV1());
    }

    /** Any rule set document; the builder works on a copy. */
    public static RuleSetBuilder from(JsonNode document) {
        return new RuleSetBuilder((ObjectNode) document.deepCopy());
    }

    /** Changes the top-level document. */
    public RuleSetBuilder document(Consumer<ObjectNode> change) {
        change.accept(document);
        return this;
    }

    /** Changes the rule with the given id. */
    public RuleSetBuilder rule(String id, Consumer<ObjectNode> change) {
        change.accept(find("rules", "id", id));
        return this;
    }

    /** Changes the field with the given name. */
    public RuleSetBuilder field(String name, Consumer<ObjectNode> change) {
        change.accept(find("fields", "name", name));
        return this;
    }

    public ObjectNode build() {
        return document.deepCopy();
    }

    private ObjectNode find(String collection, String key, String value) {
        for (JsonNode element : document.get(collection)) {
            if (value.equals(element.path(key).asString())) {
                return (ObjectNode) element;
            }
        }
        throw new IllegalArgumentException("no " + collection + " entry with " + key + " " + value);
    }
}
