package com.liorshaya.policypilot.ai.prompt;

import com.liorshaya.policypilot.ai.ModelRole;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * One prompt as the registry loaded it: its metadata from {@code prompt.yml} and the two templates of the active
 * version (Document 4, Prompt Registry and Versioning).
 *
 * @param name the registry name, for example {@code author}
 * @param version the active version, for example {@code v1}
 * @param system the system template, which every version shares with the conduct skeleton already included
 * @param user the user template
 * @param examples the few-shot examples as JSON text, or null when the prompt has none
 * @param outputSchema the classpath location of the JSON Schema the answer must satisfy, or null for text
 * @param role which configured model runs it
 * @param temperature the sampling temperature of Document 4's table, or null when the file says
 *     {@code default}, which is how a prompt asks for the model's own (some models accept no other)
 * @param maxOutputTokens the cap on the answer
 * @param timeout how long one call may take
 * @param repairs how many repair attempts this prompt allows after a validation failure
 * @param cache which caching policy applies
 * @param languages the languages this version has been evaluated in
 */
public record PromptDefinition(
        String name,
        String version,
        PromptTemplate system,
        PromptTemplate user,
        @Nullable String examples,
        @Nullable String outputSchema,
        ModelRole role,
        @Nullable Double temperature,
        int maxOutputTokens,
        Duration timeout,
        int repairs,
        CachePolicy cache,
        java.util.List<String> languages) {

    /** How an answer of this prompt may be reused (Document 4, {@code prompt.yml} keys). */
    public enum CachePolicy {
        BY_INPUT_HASH,
        BY_DECISION,
        SCRIPTED_ONLY,
        NONE
    }
}
