package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.change.service.ChangeRequestView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The data of each event of {@code POST /rulesets/{id}/versions/{no}/changes} (Document 2, API Surface):
 * {@code analyzing}, {@code proposing}, {@code validating}, then {@code proposal}, or a {@link StreamFailure} as
 * {@code error} in its place.
 */
public final class ChangeEventPayloads {

    private ChangeEventPayloads() {}

    /** A stage that works on the whole version, analyzing or validating: how many rules the version has. */
    public record Stage(int rules) {}

    /** The rules the model is shown, in evaluation order, and the request's fields (Document 4, Prompt 5). */
    public record Proposing(List<String> candidates, List<String> fields) {}

    /**
     * The stored proposal (Document 2, {@code change_request}): a PROPOSED request whose id every pending provenance
     * of its patches carries, and the candidates the model was shown.
     */
    public record Proposal(UUID id, String status, UUID baseVersionId, String summary, JsonNode patches,
            JsonNode untouched, String notes, List<String> candidates, List<String> fields, Instant createdAt) {

        public static Proposal of(ChangeRequestView request) {
            return new Proposal(request.id(), request.status(), request.baseVersionId(),
                    request.proposal().required("summary").asString(), request.proposal().required("patches"),
                    request.proposal().required("untouched"), request.proposal().required("notes").asString(),
                    request.candidates().ruleIds(), request.candidates().fields(), request.createdAt());
        }
    }
}
