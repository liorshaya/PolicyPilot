package com.liorshaya.policypilot.rules.model;

/** The right-hand side of a comparison: a value, or a list of literals (Document 3, Conditions). */
public sealed interface Operand permits Value, LiteralList {}
