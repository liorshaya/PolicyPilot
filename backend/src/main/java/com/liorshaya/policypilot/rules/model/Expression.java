package com.liorshaya.policypilot.rules.model;

/** An arithmetic expression: a number, a field reference or a function node (Document 3, Expressions). */
public sealed interface Expression permits NumberLiteral, FieldRef, Call {}
