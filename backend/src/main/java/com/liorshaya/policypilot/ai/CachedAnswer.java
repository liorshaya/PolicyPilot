package com.liorshaya.policypilot.ai;

import java.util.List;

/**
 * A scripted chat answer as the cache keeps it (Document 4, Serving the scripted questions from the cache): the text
 * shown, and every tool call the answer made with the exact result the tool returned, in order, so a replay can run
 * the same calls again and serve the text only when they return the same.
 *
 * @param text the answer as it was shown, markers included
 * @param steps the tool calls, in the order they ran
 */
public record CachedAnswer(String text, List<ToolStep> steps) {

    public CachedAnswer {
        steps = List.copyOf(steps);
    }

    /** One tool call: the tool's name, the arguments as the model wrote them, and what the tool returned. */
    public record ToolStep(String tool, String arguments, String result) {}
}
