package com.liorshaya.policypilot.rules.model;

import java.util.List;

/** The list operand of {@code in}, {@code not_in} and {@code between}. */
public record LiteralList(List<Literal> values) implements Operand {

    public LiteralList {
        values = List.copyOf(values);
    }
}
