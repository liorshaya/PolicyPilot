package com.liorshaya.policypilot.ai.service.chat;

import org.jspecify.annotations.Nullable;

/**
 * A source an answer cited, as the {@code citations} event carries it (Document 4, Prompt 4, Marker resolution:
 * paragraph or rule links, decision links, simulation details). {@code detail} is a simulation's overrides, as its id
 * names them.
 */
public record ChatCitation(String id, Kind kind, @Nullable Integer paragraph, @Nullable String ruleId,
        @Nullable String label, @Nullable Integer applicationNumber, @Nullable String outcome,
        @Nullable String detail) {

    public enum Kind {
        PARAGRAPH,
        RULE,
        DECISION,
        SIMULATION
    }
}
