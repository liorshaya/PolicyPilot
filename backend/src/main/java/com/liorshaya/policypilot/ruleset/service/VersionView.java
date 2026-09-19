package com.liorshaya.policypilot.ruleset.service;

import com.liorshaya.policypilot.rules.validation.Finding;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * One rule set version with its document, its status and its findings (Document 2, {@code GET
 * /rulesets/{id}/versions/{no}}: "a rule set version with rules, findings and status"). A version that lives in a
 * sandbox's copy of a protected rule set names the origin in {@code forkedFromId}.
 */
public record VersionView(UUID rulesetId, String name, String domain, boolean isProtected, @Nullable UUID forkedFromId,
        UUID versionId, int versionNo, VersionStatus status, UUID policyVersionId, @Nullable UUID parentVersionId,
        @Nullable Instant publishedAt, @Nullable String publishedBy, ObjectNode document, List<Finding> findings) {

    public VersionView {
        findings = List.copyOf(findings);
    }
}
