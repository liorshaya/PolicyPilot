package com.liorshaya.policypilot.rules.model;

/** The six field types (Document 3, Types, Values and Arithmetic). */
public enum FieldType {
    NUMBER("number"),
    INTEGER("integer"),
    BOOLEAN("boolean"),
    STRING("string"),
    ENUM("enum"),
    DATE("date");

    private final String json;

    FieldType(String json) {
        this.json = json;
    }

    /** The value as the DSL writes it. */
    public String json() {
        return json;
    }
}
