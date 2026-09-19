package com.liorshaya.policypilot.decision.service;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One line of a batch result (Document 2, decide: "a batch returns aggregates plus a summary per case"); the trace of
 * each decision is read back at {@code GET /decisions/{id}}.
 */
public record CaseSummary(UUID id, @Nullable Integer caseNo, String status, @Nullable String outcome,
        @Nullable String decidingRuleId, List<String> flags) {

    public CaseSummary {
        flags = List.copyOf(flags);
    }
}
