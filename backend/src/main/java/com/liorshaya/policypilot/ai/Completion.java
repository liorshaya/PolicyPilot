package com.liorshaya.policypilot.ai;

/**
 * One structured-output answer: the parsed value with the usage of the call that produced it (Document 2, AI Layer
 * Design). The usage travels with the value so a shared gateway never has to be asked what it did last.
 *
 * @param value the model's JSON, parsed into the type the caller asked for
 * @param usage the tokens the call spent, or {@link TokenUsage#NONE} when the answer came from the cache
 * @param cacheHit whether the answer came from the response cache instead of the provider
 */
public record Completion<T>(T value, TokenUsage usage, boolean cacheHit) {

    public static <T> Completion<T> fromProvider(T value, TokenUsage usage) {
        return new Completion<>(value, usage, false);
    }

    public static <T> Completion<T> fromCache(T value) {
        return new Completion<>(value, TokenUsage.NONE, true);
    }
}
