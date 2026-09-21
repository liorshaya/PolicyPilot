package com.liorshaya.policypilot.rag.service;

import org.jspecify.annotations.Nullable;

/**
 * One chunk retrieval kept (Document 4, Retrieval Pipeline, Fusion): its id, its text, its fused score and where each
 * list ranked it, with its cosine similarity to the question when the vector list found it.
 */
public record RetrievedChunk(String id, Chunk.Kind kind, String refId, String text, double score,
        @Nullable Double cosine, @Nullable Integer vectorRank, @Nullable Integer lexicalRank) {}
