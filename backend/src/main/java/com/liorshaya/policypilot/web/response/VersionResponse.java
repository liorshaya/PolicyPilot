package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.ruleset.service.Acknowledgement;
import com.liorshaya.policypilot.ruleset.service.Review;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * One rule set version: the DSL document, the status and the findings the validator reports on it (Document 2, API
 * Surface: "a rule set version with rules, findings and status"), and the review a draft carries once it has one
 * (Document 2, Flow 1). An edit that forked a protected rule set answers with the ids of the sandbox's own copy and
 * {@code forkedFromId}.
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
        @JsonProperty(required = true) List<FindingResponse> findings,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReviewResponse review) {

    /** One validation finding in the Document 3 reporting shape. */
    public record FindingResponse(
            @JsonProperty(required = true) String code,
            @JsonProperty(required = true) String severity,
            @JsonProperty(required = true) String path,
            @JsonProperty(required = true) String message,
            @JsonProperty(required = true) List<String> ruleIds,
            @JsonProperty(required = true) List<String> fieldNames) {

        /** The findings of a generation stream are the same shape, so this is visible to the web layer. */
        public static FindingResponse of(Finding finding) {
            return new FindingResponse(finding.code().name(), finding.severity().name().toLowerCase(java.util.Locale.ROOT),
                    finding.path(), finding.message(), finding.ruleIds(), finding.fieldNames());
        }
    }

    /** A draft's review (Document 2, ruleset_version.review_json): status DONE, FAILED or STALE, and its findings. */
    public record ReviewResponse(
            @JsonProperty(required = true) @Schema(allowableValues = {"DONE", "FAILED", "STALE"}) String status,
            @JsonProperty(required = true) String promptVersion,
            @JsonProperty(required = true) List<ReviewFindingResponse> findings,
            @JsonProperty(required = true) Map<String, List<String>> coverage) {

        static ReviewResponse of(Review review) {
            return new ReviewResponse(review.status().name(), review.promptVersion(),
                    review.findings().stream().map(ReviewFindingResponse::of).toList(), review.coverage());
        }
    }

    /**
     * One review finding (Document 4, Findings contract) with its id, whether it blocks publishing until it is
     * acknowledged, and the acknowledgement once there is one.
     */
    public record ReviewFindingResponse(
            @JsonProperty(required = true) String id,
            @JsonProperty(required = true)
            @Schema(allowableValues = {"ambiguity", "conflict", "unsupported", "gap", "duplicate", "injection"})
            String kind,
            @JsonProperty(required = true) @Schema(allowableValues = {"error", "warning"}) String severity,
            @JsonProperty(required = true) List<String> ruleIds,
            @JsonProperty(required = true) List<Integer> paragraphIndexes,
            @JsonProperty(required = true) String message,
            @JsonProperty(required = true) String suggestion,
            @JsonProperty(required = true) double confidence,
            @JsonProperty(required = true) boolean blocking,
            @JsonInclude(JsonInclude.Include.NON_NULL) AcknowledgementResponse acknowledgement) {

        static ReviewFindingResponse of(ReviewFinding finding) {
            Acknowledgement ack = finding.acknowledgement();
            return new ReviewFindingResponse(finding.id(), finding.kind().json(), finding.severity(),
                    finding.ruleIds(), finding.paragraphIndexes(), finding.message(), finding.suggestion(),
                    finding.confidence(), finding.blocking(),
                    ack == null ? null : new AcknowledgementResponse(
                            ack.resolution() == null ? null : ack.resolution().json(), ack.note(), ack.at()));
        }
    }

    /** How the analyst acknowledged a finding: a gap's resolution, an error's note, and when. */
    public record AcknowledgementResponse(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(allowableValues = {"rule_added", "flag_added", "interpretation"})
            String resolution,
            @JsonInclude(JsonInclude.Include.NON_NULL) String note,
            @JsonProperty(required = true) Instant at) {}

    public static VersionResponse of(VersionView version) {
        return new VersionResponse(version.rulesetId(), version.name(), version.domain(), version.isProtected(),
                version.forkedFromId(), version.versionId(), version.versionNo(), version.status().name(),
                version.policyVersionId(), version.parentVersionId(), version.publishedAt(), version.publishedBy(),
                version.document(), version.findings().stream().map(FindingResponse::of).toList(),
                version.review() == null ? null : ReviewResponse.of(version.review()));
    }
}
