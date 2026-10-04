package com.liorshaya.policypilot.rag.service;

/**
 * Document 4, Retrieval Pipeline, Threshold: a question is not covered when its best chunk's cosine similarity is
 * below {@code policypilot.rag.min-score} and it names no rule id, field name or decision number of the version. The
 * fused score cannot serve: with {@code k = 60} it never exceeds 2/61. A question it would stop is first read as a
 * follow-up (Follow-up): it takes the names its conversation carries, and is covered when there are any.
 */
public record NotCoveredThreshold(double minScore) {

    public boolean covers(double bestCosine, QuestionSignals signals) {
        return bestCosine >= minScore || signals.namesSomething();
    }

    /**
     * The names retrieval goes by: the question's own when its cosine or they keep it covered, so a question covered
     * on its own takes nothing from the conversation; otherwise the names the conversation carries.
     */
    public QuestionSignals namesOf(double bestCosine, QuestionSignals asked, QuestionSignals carried) {
        return covers(bestCosine, asked) ? asked : carried;
    }
}
