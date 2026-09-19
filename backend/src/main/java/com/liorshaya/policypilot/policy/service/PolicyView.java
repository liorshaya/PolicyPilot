package com.liorshaya.policypilot.policy.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A policy as other modules and the API see it: the document, its versions and their paragraphs. */
public record PolicyView(
        UUID id, String title, PolicyLanguage language, boolean isProtected, Instant createdAt, List<Version> versions) {

    public PolicyView {
        versions = List.copyOf(versions);
    }

    /** One version, numbered from 1, with its paragraphs in order. */
    public record Version(int versionNo, Instant createdAt, List<Paragraph> paragraphs) {

        public Version {
            paragraphs = List.copyOf(paragraphs);
        }
    }

    /** One paragraph and its index, the unit rules cite (Document 3, provenance.paragraph). */
    public record Paragraph(int index, String text) {}
}
