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

    public static PolicyView view(PolicyDocumentEntity document) {
        List<PolicyView.Version> versions = document.getVersions().stream()
                .map(version -> new PolicyView.Version(version.getVersionNo(), version.getCreatedAt(),
                        version.getParagraphs().stream()
                                .map(paragraph -> new PolicyView.Paragraph(paragraph.getIndex(), paragraph.getText()))
                                .toList()))
                .toList();
        return new PolicyView(document.getId(), document.getTitle(),
                PolicyLanguage.fromCode(document.getLanguage()).orElseThrow(), document.isProtectedRow(),
                document.getCreatedAt(), versions);
    }
}
