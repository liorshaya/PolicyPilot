package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.policy.service.PolicyView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A policy with its versions and paragraphs (Document 2, API Surface: {@code POST /policies} returns the paragraph
 * split so the UI can show it immediately; {@code GET /policies/{id}}). {@code forkedFromId} appears only on a sandbox's
 * copy of a protected policy.
 */
public record PolicyResponse(
        @JsonProperty(required = true) UUID id,
        @JsonProperty(required = true) String title,
        @JsonProperty(required = true) String language,
        @JsonProperty(value = "protected", required = true) boolean isProtected,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID forkedFromId,
        @JsonProperty(required = true) Instant createdAt,
        @JsonProperty(required = true) List<Version> versions) {

    /** One version with its paragraphs. */
    public record Version(@JsonProperty(required = true) int versionNo, @JsonProperty(required = true) Instant createdAt,
            @JsonProperty(required = true) List<Paragraph> paragraphs) {}

    /** One paragraph; {@code index} is what a rule's provenance cites. */
    public record Paragraph(@JsonProperty(required = true) int index, @JsonProperty(required = true) String text) {}

    public static PolicyResponse of(PolicyView policy) {
        return new PolicyResponse(policy.id(), policy.title(), policy.language().code(), policy.isProtected(),
                policy.forkedFromId(), policy.createdAt(), policy.versions().stream()
                        .map(version -> new Version(version.versionNo(), version.createdAt(), version.paragraphs().stream()
                                .map(paragraph -> new Paragraph(paragraph.index(), paragraph.text()))
                                .toList()))
                        .toList());
    }
}
