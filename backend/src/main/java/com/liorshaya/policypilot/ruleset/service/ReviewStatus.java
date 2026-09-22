package com.liorshaya.policypilot.ruleset.service;

/**
 * Where a draft's review stands (Document 2, ruleset_version.review_json): done, failed because the call failed, or
 * stale because the document was edited after it. Only a {@code DONE} review lets a draft be published.
 */
public enum ReviewStatus {
    DONE,
    FAILED,
    STALE
}
