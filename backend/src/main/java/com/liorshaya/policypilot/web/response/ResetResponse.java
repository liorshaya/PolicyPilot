package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.demo.service.ResetJob;

/**
 * What {@code POST /admin/reset} did (Document 2, API Surface): the stale sandboxes it deleted and whether the
 * protected rows had to be re-seeded.
 */
public record ResetResponse(@JsonProperty(required = true) int sandboxesDeleted,
        @JsonProperty(required = true) boolean reseeded) {

    public static ResetResponse of(ResetJob.Result result) {
        return new ResetResponse(result.sandboxesDeleted(), result.reseeded());
    }
}
