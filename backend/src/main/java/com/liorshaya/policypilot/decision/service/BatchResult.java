package com.liorshaya.policypilot.decision.service;

import java.util.List;

/** What a batch of cases produced (Brief FR-9): the aggregates and one summary per case, in case order. */
public record BatchResult(Aggregates aggregates, List<CaseSummary> results) {

    public BatchResult {
        results = List.copyOf(results);
    }
}
