package com.liorshaya.policypilot.decision.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome counts and the top deciding rules (Document 2, decide and stats), in the shape the fixture's summary uses:
 * the counts of {@code approve}, {@code reject} and {@code refer}, and the five rules that decided most, by count and
 * then by rule id.
 */
public record Aggregates(Map<String, Integer> outcomes, int errors, List<TopRule> topDecidingRules, int decisions) {

    /** How many rules the statistics name (the fixture's summary lists five). */
    public static final int TOP_RULES = 5;

    public Aggregates {
        outcomes = Map.copyOf(new LinkedHashMap<>(outcomes));
        topDecidingRules = List.copyOf(topDecidingRules);
    }

    /** One deciding rule and how many decisions it made. */
    public record TopRule(String ruleId, int count) {}
}
