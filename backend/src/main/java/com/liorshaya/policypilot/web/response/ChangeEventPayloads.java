package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.change.service.ChangeRequestView;
import com.liorshaya.policypilot.web.response.VersionResponse.FindingResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The data of each event of {@code POST /rulesets/{id}/versions/{no}/changes} (Document 2, API Surface):
 * {@code analyzing}, {@code proposing}, {@code validating}, {@code regression}, then {@code proposal}, or
 * {@code error} in its place. Every event after {@code analyzing} says which stage it ended.
 */
public final class ChangeEventPayloads {

    private ChangeEventPayloads() {}

    /**
     * The stage an event ended (Document 2, added 2026-09-28 for Register phase 4): its name, as its own event is
     * named, its milliseconds, and the tokens the answers to its prompts spent, null for a stage that got no answer.
     */
    public record Ended(String stage, long ms, @Nullable Long tokens) {}

    /**
     * A stage that works on the whole version, analyzing, validating or regression: how many rules it has, and the
     * stage it ended, null for analyzing, the first.
     */
    public record Stage(int rules, @Nullable Ended ended) {}

    /** The rules the model is shown, in evaluation order, and the request's fields (Document 4, Prompt 5). */
    public record Proposing(List<String> candidates, List<String> fields, Ended ended) {}

    /**
     * The stored proposal (Document 2, {@code change_request}): a PROPOSED request, numbered in its sandbox, whose id
     * every pending provenance of its patches carries, the candidates the model was shown, the diff of the base
     * against the patched copy and the regression report.
     */
    public record Proposal(UUID id, int number, String status, UUID baseVersionId, String summary,
            JsonNode patches, JsonNode untouched, String notes, List<String> candidates, List<String> fields,
            DiffResponse diff, RegressionResponse regression, Instant createdAt, Ended ended) {

        public static Proposal of(ChangeRequestView request, Ended ended) {
            return new Proposal(request.id(), request.number(), request.status(), request.baseVersionId(),
                    request.proposal().required("summary").asString(), request.proposal().required("patches"),
                    request.proposal().required("untouched"), request.proposal().required("notes").asString(),
                    request.candidates().ruleIds(), request.candidates().fields(), DiffResponse.of(request.diff()),
                    RegressionResponse.of(request.regression()), request.createdAt(), ended);
        }
    }

    /**
     * Why the stream ended without a proposal, as {@link StreamFailure} has it for every stream that judges a document
     * (the code, the findings and the model's last document), with the stage it ended.
     */
    public record Failed(String code, List<FindingResponse> findings, @Nullable Object document,
            @Nullable Ended ended) {

        public Failed {
            findings = List.copyOf(findings);
        }
    }
}
