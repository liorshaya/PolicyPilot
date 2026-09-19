package com.liorshaya.policypilot.ruleset.service;

import java.util.List;
import java.util.UUID;

/**
 * A rule set as the API lists it (Document 2, {@code GET /rulesets}): its identity, the policy it was written from,
 * and the number and status of every version.
 */
public record RulesetView(UUID id, String name, String domain, boolean isProtected, UUID forkedFromId, UUID policyId,
        List<VersionSummary> versions) {

    public RulesetView {
        versions = List.copyOf(versions);
    }

    /** One version in the list. */
    public record VersionSummary(int versionNo, VersionStatus status) {}
}
