package com.liorshaya.policypilot.policy.service;

import java.util.List;
import java.util.UUID;

/**
 * One policy version as a rule set cites it (Document 2, Data Model: {@code ruleset_version.policy_version_id} and
 * {@code rule.paragraph_id}): its row id, its document, and its paragraphs with their row ids and indexes.
 */
public record PolicyVersionRef(UUID id, UUID documentId, int versionNo, List<Paragraph> paragraphs) {

    public PolicyVersionRef {
        paragraphs = List.copyOf(paragraphs);
    }

    /** The paragraph texts in index order, as the validator takes them (paragraph {@code n} at {@code n - 1}). */
    public List<String> texts() {
        return paragraphs.stream().map(Paragraph::text).toList();
    }

    /** One paragraph: its row id, its index from 1 and its text. */
    public record Paragraph(UUID id, int index, String text) {}
}
