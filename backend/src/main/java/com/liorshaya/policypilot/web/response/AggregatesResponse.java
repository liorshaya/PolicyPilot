package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.decision.service.Aggregates;
import java.util.List;
import java.util.Map;

/**
 * Outcome counts and the top deciding rules (Document 2, decide and stats), in the shape the fixture's summary uses
 * so the two can be compared directly.
 */
public record AggregatesResponse(Map<String, Integer> outcomes, int errors, List<TopRule> topDecidingRules,
        int decisions) {

    /** One deciding rule and how many decisions it made. */
    public record TopRule(String ruleId, int count) {}

    public static AggregatesResponse of(Aggregates aggregates) {
        return new AggregatesResponse(aggregates.outcomes(), aggregates.errors(), aggregates.topDecidingRules().stream()
                .map(rule -> new TopRule(rule.ruleId(), rule.count()))
                .toList(), aggregates.decisions());
    }
}
