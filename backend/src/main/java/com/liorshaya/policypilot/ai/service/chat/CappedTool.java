package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.ChatTool;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A chat tool held to its turn (Document 5, Tool call volume: four calls a turn, one {@code simulate}): a call past a
 * cap is refused and marks the turn overrun, a refusal of the body becomes a refusal the model reads, every refusal
 * is reported, and every call is recorded with how it ended.
 */
public final class CappedTool implements ChatTool {

    private final String name;
    private final String description;
    private final String schema;
    private final ChatTurn turn;
    private final boolean simulation;
    private final Function<String, String> body;
    private final BiConsumer<String, String> refused;

    /**
     * @param body the tool itself: the arguments as the model wrote them, to its result; it throws a {@link
     *     ToolRefusedException} to refuse
     * @param refused told the tool's name and the reason of every refusal, to count it
     */
    public CappedTool(String name, String description, String schema, ChatTurn turn, boolean simulation,
            Function<String, String> body, BiConsumer<String, String> refused) {
        this.name = name;
        this.description = description;
        this.schema = schema;
        this.turn = turn;
        this.simulation = simulation;
        this.body = body;
        this.refused = refused;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String inputSchema() {
        return schema;
    }

    @Override
    public String call(String argumentsJson) {
        String result;
        try {
            if (!turn.admit(simulation)) {
                throw new ToolRefusedException("limit", "This turn has used all the lookups it may make.");
            }
            result = body.apply(argumentsJson);
        } catch (ToolRefusedException e) {
            refused.accept(name, e.reason());
            result = ToolResults.refusal(e.reason(), e.getMessage());
        }
        turn.record(new ChatTurn.ToolCallRecord(name, argumentsJson, ToolResults.outcomeOf(result)));
        return result;
    }
}
