package com.liorshaya.policypilot.ai;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * One call to a model, fully described: which prompt and version asked, what it asked, and the settings the
 * provider must run it with (Document 4, Model Configuration per Prompt). The gateway adds nothing of its own,
 * so the same spec always produces the same request.
 *
 * @param promptName the prompt's registry name, for example {@code author}
 * @param promptVersion the active version of that prompt, for example {@code v1}
 * @param role which configured model runs it
 * @param system the rendered system prompt
 * @param user the rendered user prompt
 * @param outputSchema the JSON Schema the provider must answer against, or null for a text answer
 * @param temperature the provider's sampling temperature
 * @param maxOutputTokens the cap on the answer
 * @param timeout how long the whole call may take
 * @param attempt 1 for the first call, 2 or 3 for a repair of the same request
 */
public record PromptSpec(
        String promptName,
        String promptVersion,
        ModelRole role,
        String system,
        String user,
        @Nullable String outputSchema,
        double temperature,
        int maxOutputTokens,
        Duration timeout,
        int attempt) {

    public PromptSpec {
        if (attempt < 1) {
            throw new IllegalArgumentException("an attempt is numbered from 1");
        }
    }

    /** The same call, numbered as the next attempt, with the repair prompt's own user text. */
    public PromptSpec repairedWith(String repairUser) {
        return new PromptSpec(
                promptName,
                promptVersion,
                role,
                system,
                repairUser,
                outputSchema,
                temperature,
                maxOutputTokens,
                timeout,
                attempt + 1);
    }
}
