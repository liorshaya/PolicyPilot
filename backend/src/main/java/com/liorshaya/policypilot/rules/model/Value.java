package com.liorshaya.policypilot.rules.model;

/** A single value: a literal, a field reference or a function call; what a {@code set} action writes. */
public sealed interface Value extends Operand permits Literal, FieldRef, Call {}
