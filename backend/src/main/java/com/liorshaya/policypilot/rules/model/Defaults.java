package com.liorshaya.policypilot.rules.model;

/** The outcome and reason when no rule decides (Document 3, Document Structure). */
public record Defaults(Outcome outcome, String reason) {}
