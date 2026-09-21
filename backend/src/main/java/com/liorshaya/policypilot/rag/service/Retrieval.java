package com.liorshaya.policypilot.rag.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What retrieval found for one question on one version (Document 4, Retrieval Pipeline): the fused chunks in rank
 * order and their citations, or, when the Threshold stops it, no chunk and the fixed sentence of the language.
 */
public record Retrieval(boolean covered, double bestCosine, List<RetrievedChunk> chunks, List<Citation> citations,
        @Nullable String notCovered) {

    public Retrieval {
        chunks = List.copyOf(chunks);
        citations = List.copyOf(citations);
    }
}
