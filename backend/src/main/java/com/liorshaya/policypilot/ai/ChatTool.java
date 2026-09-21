package com.liorshaya.policypilot.ai;

/**
 * A tool the answer prompt may call (Document 2, Tools available to the {@code answer} prompt; Document 4, Prompt 4).
 * It is plain Java here, because {@code ai} may not import Spring AI; {@code ai.adapter} registers it with the
 * provider as a tool callback. A tool never writes: it reads, or asks the engine for a simulation.
 */
public interface ChatTool {

    /** The name the model calls it by. */
    String name();

    /** What the model is told the tool is for. */
    String description();

    /** The JSON Schema of the arguments, as the provider sends them. */
    String inputSchema();

    /**
     * Runs the tool on the arguments as the model wrote them and returns what the model reads. Arguments the tool
     * refuses come back as a result that says so, never as an exception the model would not see.
     */
    String call(String argumentsJson);
}
