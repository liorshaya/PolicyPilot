package com.liorshaya.policypilot.rules.model;

/** A reference to another field, {@code { "field": "name" }}. */
public record FieldRef(String field) implements Value, Expression {}
