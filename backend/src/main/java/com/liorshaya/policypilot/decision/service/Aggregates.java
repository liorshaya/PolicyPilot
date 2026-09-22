package com.liorshaya.policypilot.decision.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome counts, the top deciding rules and the flag counts (Document 2, decide and stats), in the shape the
 * fixture's summary uses: the counts of {@code approve}, {@code reject} and {@code refer}, the five rules that
 * decided most, by count and then by rule id, and how many of the same decisions carry each flag code.
 */
public record Aggregates(Map<String, Integer> outcomes, int errors, List<TopRule> topDecidingRules, int decisions,
        Map<String, Integer> flagCounts) {

    /** How many rules the statistics name (the fixture's summary lists five). */
    public static final int TOP_RULES = 5;

    public Aggregates {
        outcomes = Map.copyOf(new LinkedHashMap<>(outcomes));
        topDecidingRules = List.copyOf(topDecidingRules);
        flagCounts = Map.copyOf(new LinkedHashMap<>(flagCounts));
    }

    /** One deciding rule and how many decisions it made. */
    public record TopRule(String ruleId, int count) {}
}
