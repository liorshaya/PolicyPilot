package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.change.service.ChangeDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A change request a person decided on (Document 2, approve and reject): APPROVED with the version the approval
 * published, or REJECTED without one.
 *
 * @param result the published version, in the sandbox's own copy of the rule set when the base was protected; absent
 *     for a rejection
 */
public record ChangeDecisionResponse(
        @JsonProperty(required = true) UUID id,
        @JsonProperty(required = true) @Schema(allowableValues = {"APPROVED", "REJECTED"}) String status,
        @JsonProperty(required = true) Instant decidedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Result result) {

    public static ChangeDecisionResponse of(ChangeDecision decision) {
        ChangeDecision.Result result = decision.result();
        return new ChangeDecisionResponse(decision.id(), decision.status(), decision.decidedAt(), result == null
                ? null : new Result(result.rulesetId(), result.versionNo(), result.versionId()));
    }

    /** The version an approval published. */
    public record Result(
            @JsonProperty(required = true) UUID rulesetId,
            @JsonProperty(required = true) int versionNo,
            @JsonProperty(required = true) UUID versionId) {}
}
