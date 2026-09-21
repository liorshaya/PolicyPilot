package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.liorshaya.policypilot.rag.service.Citation;
import com.liorshaya.policypilot.rag.service.Retrieval;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What {@code POST /rulesets/{id}/versions/{no}/retrieval} returns (Document 2, API Surface): whether the question is
 * covered, the best cosine similarity, the fused chunks with their scores and ranks, and their citations; or, for a
 * question below the Threshold, no chunk and the fixed not-covered sentence.
 */
public record RetrievalResponse(boolean covered, double bestCosine, List<Chunk> chunks, List<Cite> citations,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String notCovered) {

    public static RetrievalResponse of(Retrieval retrieval) {
        return new RetrievalResponse(retrieval.covered(), retrieval.bestCosine(),
                retrieval.chunks().stream().map(Chunk::of).toList(),
                retrieval.citations().stream().map(Cite::of).toList(), retrieval.notCovered());
    }

    /** One fused chunk: its citable id, its text, its fused score and where each list ranked it. */
    public record Chunk(String id, String kind, String text, double score,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Double cosine,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer vectorRank,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer lexicalRank) {

        static Chunk of(RetrievedChunk chunk) {
            return new Chunk(chunk.id(), chunk.kind().name(), chunk.text(), chunk.score(), chunk.cosine(),
                    chunk.vectorRank(), chunk.lexicalRank());
        }
    }

    /** Where a chunk points: a paragraph by its index, or a rule by its id with its label and quoted paragraph. */
    public record Cite(String id, String kind,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer paragraph,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String ruleId,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String label) {

        static Cite of(Citation citation) {
            return new Cite(citation.id(), citation.kind().name(), citation.paragraph(), citation.ruleId(),
                    citation.label());
        }
    }
}
