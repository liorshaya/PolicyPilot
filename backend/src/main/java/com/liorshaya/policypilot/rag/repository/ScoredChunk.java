package com.liorshaya.policypilot.rag.repository;

/** A chunk one of the two queries ranked, with that query's score: cosine similarity, or the lexical rank. */
public record ScoredChunk(String kind, String refId, String text, double score) {}
