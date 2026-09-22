package com.liorshaya.policypilot.ai;

import java.util.Optional;

/**
 * The cache of the scripted chat answers (Document 2, Gateways; Document 4, Serving the scripted questions from the
 * cache), under the key of every cached call: the prompt name and version, the model the prompt's role names, and the
 * rendered prompts. What may be kept and when a kept answer may be served is the chat's to decide; this is only where
 * the answers live. Implemented in {@code ai.adapter}, which knows the model and writes the ledger.
 */
public interface AnswerCache {

    /** The answer kept for this call, if any. Records nothing. */
    Optional<CachedAnswer> find(PromptSpec spec);

    /** Keeps an answer for this call; the first answer kept stands, and a later one is ignored. */
    void keep(PromptSpec spec, CachedAnswer answer);

    /** Records that the answer kept for this call was served: the ledger row of a cache hit, which cost nothing. */
    void served(PromptSpec spec);
}
