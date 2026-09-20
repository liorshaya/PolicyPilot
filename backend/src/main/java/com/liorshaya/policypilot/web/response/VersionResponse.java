package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * One rule set version: the DSL document, the status and the findings the validator reports on it (Document 2, API
 * Surface: "a rule set version with rules, findings and status"). An edit that forked a protected rule set answers
 * with the ids of the sandbox's own copy and {@code forkedFromId}.
 */
public record VersionResponse(
        @JsonProperty(required = true) UUID rulesetId,
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) String domain,
        @JsonProperty(value = "protected", required = true) boolean isProtected,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID forkedFromId,
        @JsonProperty(required = true) UUID versionId,
        @JsonProperty(required = true) int versionNo,
        @JsonProperty(required = true) String status,
        @JsonProperty(required = true) UUID policyVersionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID parentVersionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant publishedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String publishedBy,
        @JsonProperty(required = true)
        @Schema(implementation = Object.class, description = "The whole DSL document (Document 3)")
        ObjectNode ruleSet,
        @JsonProperty(required = true) List<FindingResponse> findings) {

    /** One validation finding in the Document 3 reporting shape. */
    public record FindingResponse(
            @JsonProperty(required = true) String code,
            @JsonProperty(required = true) String severity,
            @JsonProperty(required = true) String path,
            @JsonProperty(required = true) String message,
            @JsonProperty(required = true) List<String> ruleIds,
            @JsonProperty(required = true) List<String> fieldNames) {

        static FindingResponse of(Finding finding) {
            return new FindingResponse(finding.code().name(), finding.severity().name().toLowerCase(java.util.Locale.ROOT),
                    finding.path(), finding.message(), finding.ruleIds(), finding.fieldNames());
        }
    }

    public static VersionResponse of(VersionView version) {
        return new VersionResponse(version.rulesetId(), version.name(), version.domain(), version.isProtected(),
                version.forkedFromId(), version.versionId(), version.versionNo(), version.status().name(),
                version.policyVersionId(), version.parentVersionId(), version.publishedAt(), version.publishedBy(),
                version.document(), version.findings().stream().map(FindingResponse::of).toList());
    }
}
