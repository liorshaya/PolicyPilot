package com.liorshaya.policypilot.rag.service;

/**
 * One unit of the retrieval corpus of a published version (Document 4, Retrieval Pipeline, Corpus): a policy
 * paragraph, whose {@code refId} is its index, or a rule, whose {@code refId} is its rule id. The id is the one
 * the answer prompt cites: {@code p:7}, {@code r:R-330}.
 */
public record Chunk(Kind kind, String refId, String text) {

    /** Document 2, {@code chunk.kind}. */
    public enum Kind {
        PARAGRAPH("p"),
        RULE("r");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        /** The marker prefix of Document 4's citation protocol. */
        public String prefix() {
            return prefix;
        }
    }

    /** {@code p:<index>} or {@code r:<ruleId>}. */
    public String id() {
        return kind.prefix() + ":" + refId;
    }
}
