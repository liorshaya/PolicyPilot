package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.service.ReviewService;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.ruleset.service.FindingKind;
import com.liorshaya.policypilot.ruleset.service.Review;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reviews a draft and keeps the review with it (Document 2, Flow 1), for the generation stream and for
 * {@code POST .../review}. A call that fails leaves the review {@code FAILED}, so the draft is never lost and never
 * published unreviewed; an injection finding is a security event (Document 5, Security Logging).
 */
@Component
class DraftReviewer {

    private static final Logger log = LoggerFactory.getLogger(DraftReviewer.class);

    private final ReviewService reviews;
    private final PolicyService policies;
    private final RulesetService rulesets;
    private final SecurityEvents events;

    DraftReviewer(ReviewService reviews, PolicyService policies, RulesetService rulesets, SecurityEvents events) {
        this.reviews = reviews;
        this.policies = policies;
        this.rulesets = rulesets;
        this.events = events;
    }

    /**
     * Reviews the draft and answers it with its review.
     *
     * @throws LlmUnavailableException when the provider failed; the review is stored as FAILED first
     */
    VersionView review(VersionView draft, UUID sandboxId) {
        PolicyVersionRef policy = policies.version(draft.policyVersionId())
                .orElseThrow(() -> new IllegalStateException("no policy version " + draft.policyVersionId()));
        PolicyView view = policies.find(policy.documentId(), sandboxId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Review review;
        try {
            review = reviews.review(policy, view.title(), view.language().name().toLowerCase(Locale.ROOT),
                    draft.document()).review();
        } catch (LlmUnavailableException e) {
            store(draft, sandboxId, Review.failed(reviews.promptVersion()));
            throw e;
        } catch (LlmMalformedOutputException e) {
            // the provider answered, but not with findings: the analyst sees a failed review and can run it again
            log.warn("review answer malformed: {}", e.getMessage());
            return store(draft, sandboxId, Review.failed(reviews.promptVersion()));
        }
        VersionView stored = store(draft, sandboxId, review);
        for (ReviewFinding finding : review.findings()) {
            if (finding.kind() == FindingKind.INJECTION) {
                finding.paragraphIndexes().forEach(paragraph -> events.injectionFound(draft.rulesetId(), paragraph));
            }
        }
        return stored;
    }

    private VersionView store(VersionView draft, UUID sandboxId, Review review) {
        return rulesets.recordReview(draft.rulesetId(), draft.versionNo(), sandboxId, review)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
