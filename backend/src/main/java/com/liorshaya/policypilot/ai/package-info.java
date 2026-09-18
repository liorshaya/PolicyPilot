/**
 * The AI layer (Document 2, AI Layer Design; Document 4): the {@code LlmGateway} and {@code EmbeddingGateway}
 * interfaces, the prompt registry, structured output contracts, the validation loop with bounded repair, the
 * marker resolver, tool argument validation and the five use cases (author, review, explain, answer, change).
 *
 * <p>Allowed dependencies: {@code rules}, {@code engine} (read-only, for regression), {@code policy},
 * {@code decision}, {@code rag}, plus {@code config} and {@code common}. Model output enters the system only as a
 * validated proposal; nothing here decides a case. Built from day 7.
 */
package com.liorshaya.policypilot.ai;
