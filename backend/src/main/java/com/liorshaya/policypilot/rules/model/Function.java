package com.liorshaya.policypilot.rules.model;

/** The expression functions and their arity (Document 3, Expressions, the function table). */
public enum Function {
    ADD("add", 2, 8),
    SUB("sub", 2, 2),
    MUL("mul", 2, 8),
    DIV("div", 2, 2),
    MIN("min", 2, 8),
    MAX("max", 2, 8),
    ABS("abs", 1, 1),
    ROUND("round", 2, 2),
    POW("pow", 2, 2),
    MONTHS_BETWEEN("months_between", 2, 2);

    private final String json;
    private final int minArity;
    private final int maxArity;

    Function(String json, int minArity, int maxArity) {
        this.json = json;
        this.minArity = minArity;
        this.maxArity = maxArity;
    }

    /** The value as the DSL writes it. */
    public String json() {
        return json;
    }

    /** Whether the function takes this many arguments. */
    public boolean accepts(int arguments) {
        return arguments >= minArity && arguments <= maxArity;
    }
}
