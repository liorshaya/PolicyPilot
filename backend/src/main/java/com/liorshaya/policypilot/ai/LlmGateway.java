package com.liorshaya.policypilot.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * The one way the system talks to a language model (Document 2, AI Layer Design; NFR-4 provider independence).
 * Implementations live in {@code ai.adapter}; everything above this interface is provider-agnostic, and nothing
 * the model answers reaches storage or the engine without passing a validator first.
 */
public interface LlmGateway {

    /**
     * One structured-output call: the provider answers JSON against the spec's schema, and the answer is parsed
     * into {@code type}.
     *
     * @throws LlmUnavailableException when the provider timed out, refused or failed
     * @throws LlmMalformedOutputException when the answer was not JSON the type accepts
     */
    <T> Completion<T> complete(PromptSpec spec, Class<T> type);

    /**
     * Forgets the cached answer to this call, so the next identical call asks the provider again (Document 2, Flow 1:
     * a review run again reads the draft again, and an answer the checks refused is never served twice). Nothing
     * happens when no answer is cached.
     */
    void forget(PromptSpec spec);

    /**
     * One streamed text answer (the {@code answer} prompt): each piece of text goes to {@code tokens} as the provider
     * sends it, the tools the model calls run in between, and the call returns when the answer ends. It blocks, so a
     * caller runs it off the request thread; the generation stream already works that way.
     *
     * @return what the call cost, as the provider reported it
     * @throws LlmUnavailableException when the provider failed, the first token or the whole answer missed its
     *     deadline, or the answer was stopped by its output cap
     */
    TokenUsage stream(PromptSpec spec, List<ChatTool> tools, Consumer<String> tokens);
}
