package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.change.entity.ChangeRequestEntity;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A change request once a person decided on it (Document 2, approve and reject): APPROVED with the version the approval
 * published, or REJECTED with none.
 *
 * @param result the published version, in the sandbox's own copy of the rule set when the base was protected
 */
public record ChangeDecision(UUID id, String status, Instant decidedAt, @Nullable Result result) {

    /** The version an approval published. */
    public record Result(UUID rulesetId, int versionNo, UUID versionId) {}

    static ChangeDecision of(ChangeRequestEntity request, @Nullable VersionView published) {
        return new ChangeDecision(request.getId(), request.getStatus(), request.getDecidedAt(), published == null
                ? null : new Result(published.rulesetId(), published.versionNo(), published.versionId()));
    }
}
