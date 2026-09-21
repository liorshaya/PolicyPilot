package com.liorshaya.policypilot.ruleset.service;

/**
 * Where a published version's retrieval corpus stands (Document 2, Data Model, {@code ruleset_version.embedding_status};
 * RAG pipeline, Embedding). A DRAFT has none.
 */
public enum EmbeddingStatus {
    /** Published, not embedded yet: the job will take it. */
    PENDING,
    /** The job is embedding it now; at startup this means a restart interrupted it. */
    EMBEDDING,
    /** Its chunks are stored and retrieval may use them. */
    READY,
    /** The provider failed; it has no chunks, and the next start tries again. */
    FAILED
}
