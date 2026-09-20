package com.liorshaya.policypilot.ai;

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
}
