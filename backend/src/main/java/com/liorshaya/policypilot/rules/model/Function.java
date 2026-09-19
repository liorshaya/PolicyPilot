package com.liorshaya.policypilot.rules.model;

/** The expression functions (Document 3, Expressions, the function table). */
public enum Function {
    ADD("add"),
    SUB("sub"),
    MUL("mul"),
    DIV("div"),
    MIN("min"),
    MAX("max"),
    ABS("abs"),
    ROUND("round"),
    POW("pow"),
    MONTHS_BETWEEN("months_between");

    private final String json;

    Function(String json) {
        this.json = json;
    }

    /** The value as the DSL writes it. */
    public String json() {
        return json;
    }
}
