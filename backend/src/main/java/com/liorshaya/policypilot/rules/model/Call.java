package com.liorshaya.policypilot.rules.model;

import java.util.List;

/** A function node, {@code { "fn", "args" }} (Document 3, Expressions). */
public record Call(Function fn, List<Expression> args) implements Value, Expression {

    public Call {
        args = List.copyOf(args);
    }
}
