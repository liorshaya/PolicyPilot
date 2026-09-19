package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.liorshaya.policypilot.decision.service.BatchResult;
import com.liorshaya.policypilot.decision.service.CaseSummary;
import java.util.List;
import java.util.UUID;

/**
 * What a batch of cases answers (Brief FR-9; Document 2, decide): the aggregates and one summary per case, whose
 * full trace is read back at {@code GET /decisions/{id}}.
 */
public record BatchResponse(AggregatesResponse aggregates, List<Result> results) {

    /** One case of the batch; {@code caseNo} appears only for a case of a seeded fixture set. */
    public record Result(UUID id, @JsonInclude(JsonInclude.Include.NON_NULL) Integer caseNo, String status,
            String outcome, String decidingRuleId, List<String> flags) {}

    public static BatchResponse of(BatchResult batch) {
        return new BatchResponse(AggregatesResponse.of(batch.aggregates()), batch.results().stream()
                .map(BatchResponse::result)
                .toList());
    }

    private static Result result(CaseSummary summary) {
        return new Result(summary.id(), summary.caseNo(), summary.status(), summary.outcome(),
                summary.decidingRuleId(), summary.flags());
    }
}
