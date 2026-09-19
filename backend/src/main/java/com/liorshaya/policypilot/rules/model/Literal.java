package com.liorshaya.policypilot.rules.model;

/** A JSON literal: a number, a string or a boolean. */
public sealed interface Literal extends Value permits NumberLiteral, StringLiteral, BooleanLiteral {}
