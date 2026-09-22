package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.ruleset.service.Review;
import com.liorshaya.policypilot.ruleset.service.ReviewStatus;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A review with nothing to report, for the tests whose subject is what happens after publishing (Document 2, Flow 1:
 * a draft is published only after a DONE review). The tests of the review itself build their findings explicitly.
 */
public final class Reviews {

    private Reviews() {}

    /** A DONE review of the active prompt version that found nothing. */
    public static Review clean() {
        return new Review(ReviewStatus.DONE, "v1", List.of(), Map.of());
    }

    /** The draft, with a clean review stored, so it can be published. */
    public static VersionView reviewed(RulesetService rulesets, VersionView draft, UUID sandboxId) {
        return rulesets.recordReview(draft.rulesetId(), draft.versionNo(), sandboxId, clean()).orElseThrow();
    }
}
