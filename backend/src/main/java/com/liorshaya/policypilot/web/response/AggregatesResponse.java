package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.decision.service.Aggregates;
import java.util.List;
import java.util.Map;

/**
 * Outcome counts, the top deciding rules and the flag counts (Document 2, decide and stats), in the shape the
 * fixture's summary uses so the two can be compared directly.
 */
public record AggregatesResponse(
        @JsonProperty(required = true) Map<String, Integer> outcomes,
        @JsonProperty(required = true) int errors,
        @JsonProperty(required = true) List<TopRule> topDecidingRules,
        @JsonProperty(required = true) int decisions,
        @JsonProperty(required = true) Map<String, Integer> flagCounts) {

    /** One deciding rule and how many decisions it made. */
    public record TopRule(@JsonProperty(required = true) String ruleId, @JsonProperty(required = true) int count) {}

    public static AggregatesResponse of(Aggregates aggregates) {
        return new AggregatesResponse(aggregates.outcomes(), aggregates.errors(), aggregates.topDecidingRules().stream()
                .map(rule -> new TopRule(rule.ruleId(), rule.count()))
                .toList(), aggregates.decisions(), aggregates.flagCounts());
    }
}
