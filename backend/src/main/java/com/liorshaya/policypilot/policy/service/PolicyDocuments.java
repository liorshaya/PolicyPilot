package com.liorshaya.policypilot.policy.service;

import com.liorshaya.policypilot.policy.entity.PolicyDocumentEntity;
import com.liorshaya.policypilot.policy.entity.PolicyParagraphEntity;
import com.liorshaya.policypilot.policy.entity.PolicyVersionEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds policy documents and reads them back as views, without the database: the text is split into paragraphs,
 * checked against the limits of Document 5 and becomes version 1 with its paragraphs numbered from 1.
 */
public class PolicyDocuments {

    private final Clock clock;

    public PolicyDocuments(Clock clock) {
        this.clock = clock;
    }

    /** A new document; {@code sandboxId} is null exactly when it is protected (the V2 check constraint). */
    public PolicyDocumentEntity build(UUID sandboxId, boolean protectedRow, String title, PolicyLanguage language,
            String text) {
        List<String> paragraphs = ParagraphSplitter.split(text);
        List<PolicyTextException.Violation> violations = PolicyTextLimits.check(text, paragraphs);
        if (!violations.isEmpty()) {
            throw new PolicyTextException(violations);
        }
        Instant now = clock.instant();
        PolicyDocumentEntity document = new PolicyDocumentEntity(
                UUID.randomUUID(), sandboxId, protectedRow, title, language.code(), now);
        PolicyVersionEntity version = new PolicyVersionEntity(UUID.randomUUID(), document, 1, text, now);
        for (int i = 0; i < paragraphs.size(); i++) {
            version.addParagraph(new PolicyParagraphEntity(UUID.randomUUID(), version, i + 1, paragraphs.get(i)));
        }
        document.addVersion(version);
        return document;
    }

    /**
     * A sandbox's copy of a protected document (Document 5, Authorization: a write against a protected row forks a
     * sandbox copy instead): the same title, language, versions, texts and paragraph indexes, so every provenance
     * that cites the original cites the copy the same way.
     */
    public PolicyDocumentEntity copy(PolicyDocumentEntity original, UUID sandboxId) {
        Instant now = clock.instant();
        PolicyDocumentEntity copy = new PolicyDocumentEntity(
                UUID.randomUUID(), sandboxId, false, original.getTitle(), original.getLanguage(), now);
        copy.setForkedFromId(original.getId());
        for (PolicyVersionEntity version : original.getVersions()) {
            PolicyVersionEntity copied = new PolicyVersionEntity(
                    UUID.randomUUID(), copy, version.getVersionNo(), version.getRawText(), now);
            for (PolicyParagraphEntity paragraph : version.getParagraphs()) {
                copied.addParagraph(new PolicyParagraphEntity(
                        UUID.randomUUID(), copied, paragraph.getIndex(), paragraph.getText()));
            }
            copy.addVersion(copied);
        }
        return copy;
    }

    public static PolicyView view(PolicyDocumentEntity document) {
        List<PolicyView.Version> versions = document.getVersions().stream()
                .map(version -> new PolicyView.Version(version.getVersionNo(), version.getCreatedAt(),
                        version.getParagraphs().stream()
                                .map(paragraph -> new PolicyView.Paragraph(paragraph.getIndex(), paragraph.getText()))
                                .toList()))
                .toList();
        return new PolicyView(document.getId(), document.getTitle(),
                PolicyLanguage.fromCode(document.getLanguage()).orElseThrow(), document.isProtectedRow(),
                document.getForkedFromId(), document.getCreatedAt(), versions);
    }
}
