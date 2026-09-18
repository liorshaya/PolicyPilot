/**
 * The only package that imports {@code org.springframework.ai} (NFR-4, enforced by ArchUnit):
 * {@code SpringAiLlmGateway}, {@code SpringAiEmbeddingGateway}, provider configuration per profile, schema variant
 * derivation, the token budget guard and the response cache.
 *
 * <p>Allowed dependencies: Spring AI and the {@code ai} interfaces. Coverage gate: 70% line (contract tests carry
 * the weight). Built on day 7.
 */
package com.liorshaya.policypilot.ai.adapter;
