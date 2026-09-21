/**
 * Chunking, embedding on publish, the pgvector chunk store, hybrid retrieval and citation building (Document 2, RAG
 * pipeline; Document 4, Retrieval Pipeline).
 *
 * <p>Allowed dependencies: {@code policy}, {@code rules}, {@code ruleset}, {@code ai} (the {@code EmbeddingGateway}
 * interface), persistence, {@code config}, {@code common}. Native SQL lives only here and always binds its values
 * as parameters.
 */
package com.liorshaya.policypilot.rag;
