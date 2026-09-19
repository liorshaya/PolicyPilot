package com.liorshaya.policypilot.rules.model;

/** A string literal: a string, an enum value, a date or a {@code matches} pattern. */
public record StringLiteral(String value) implements Literal {}
