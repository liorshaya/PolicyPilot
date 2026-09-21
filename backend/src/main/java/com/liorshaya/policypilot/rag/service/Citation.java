package com.liorshaya.policypilot.rag.service;

import org.jspecify.annotations.Nullable;

/**
 * Where a retrieved chunk points (Document 2, RAG pipeline, Prompting: the API resolves ids to paragraph or rule
 * links): a paragraph by its index, or a rule by its id with its label and the paragraph its provenance quotes.
 */
public record Citation(String id, Kind kind, @Nullable Integer paragraph, @Nullable String ruleId,
        @Nullable String label) {

    public enum Kind {
        PARAGRAPH,
        RULE
    }
}
