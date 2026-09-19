package com.liorshaya.policypilot.rules.model;

/** Comparison operators (Document 3, Conditions). */
public enum Operator {
    EQ("eq"),
    NE("ne"),
    LT("lt"),
    LTE("lte"),
    GT("gt"),
    GTE("gte"),
    IN("in"),
    NOT_IN("not_in"),
    BETWEEN("between"),
    MATCHES("matches"),
    PRESENT("present"),
    ABSENT("absent");

    private final String json;

    Operator(String json) {
        this.json = json;
    }

    /** The value as the DSL writes it. */
    public String json() {
        return json;
    }
}
