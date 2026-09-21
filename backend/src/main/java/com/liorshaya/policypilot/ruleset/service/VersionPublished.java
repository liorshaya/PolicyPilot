package com.liorshaya.policypilot.ruleset.service;

import java.util.UUID;

/**
 * A version was published and its transaction committed (Document 2, RAG pipeline, Embedding): what {@code rag}
 * listens for to embed it. {@code ruleset} announces it and knows nothing of who listens.
 */
public record VersionPublished(UUID versionId) {}
