package com.liorshaya.policypilot.rag.repository;

/**
 * One {@code chunk} row as the store writes it (Document 2, Data Model): {@code kind} is PARAGRAPH or RULE,
 * {@code lexical} the text after the lexical normalization of Document 4, which is what {@code tsv} is built from.
 */
public record ChunkRow(String kind, String refId, String text, String lexical, float[] embedding) {}
