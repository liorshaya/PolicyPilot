package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.policy.service.PolicyView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A policy with its versions and paragraphs (Document 2, API Surface: {@code POST /policies} returns the paragraph
 * split so the UI can show it immediately; {@code GET /policies/{id}}).
 */
public record PolicyResponse(
        UUID id,
        String title,
        String language,
        @JsonProperty("protected") boolean isProtected,
        Instant createdAt,
        List<Version> versions) {

    /** One version with its paragraphs. */
    public record Version(int versionNo, Instant createdAt, List<Paragraph> paragraphs) {}

    /** One paragraph; {@code index} is what a rule's provenance cites. */
    public record Paragraph(int index, String text) {}

    public static PolicyResponse of(PolicyView policy) {
        return new PolicyResponse(policy.id(), policy.title(), policy.language().code(), policy.isProtected(),
                policy.createdAt(), policy.versions().stream()
                        .map(version -> new Version(version.versionNo(), version.createdAt(), version.paragraphs().stream()
                                .map(paragraph -> new Paragraph(paragraph.index(), paragraph.text()))
                                .toList()))
                        .toList());
    }
}
