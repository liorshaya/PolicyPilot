package com.liorshaya.policypilot.rag.service;

/**
 * Document 4, Retrieval Pipeline, Threshold: a question is not covered when its best chunk's cosine similarity is
 * below {@code policypilot.rag.min-score} and it names no rule id, field name or decision number of the version. The
 * fused score cannot serve: with {@code k = 60} it never exceeds 2/61.
 */
public record NotCoveredThreshold(double minScore) {

    public boolean covers(double bestCosine, QuestionSignals signals) {
        return bestCosine >= minScore || signals.namesSomething();
    }
}
