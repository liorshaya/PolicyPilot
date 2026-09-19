package com.liorshaya.policypilot.rules.model;

import java.math.BigDecimal;

/** A numeric literal, kept as the exact decimal the document wrote (Document 3, Decimal semantics). */
public record NumberLiteral(BigDecimal value) implements Literal, Expression {}
