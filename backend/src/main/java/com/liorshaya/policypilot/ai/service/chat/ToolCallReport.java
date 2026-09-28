package com.liorshaya.policypilot.ai.service.chat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One tool call as the chat stream reports it when it ends, before the answer's first token (Document 2, the
 * {@code tool} event, added 2026-09-28 for Register phase 4; Document 4, Prompt 4): the tool, what it ran on as the
 * model asked, how long it took, and for a decision or a simulation what the engine decided; a refused call names its
 * reason instead.
 *
 * @param applicationNumber the application the model named, or null
 * @param overrides a simulation's overrides as its citation id writes them, {@code has_guarantor=true}, or null
 * @param tag the tag a rule listing was asked for, or null
 * @param micros how long the call took, its lookup included
 * @param decided what the engine decided, for a decision or a simulation that answered; null otherwise
 * @param refused the reason of a refused call, {@code not_found}, {@code invalid_arguments} or {@code limit}, or null
 */
public record ToolCallReport(String tool, @Nullable Integer applicationNumber, @Nullable String overrides,
        @Nullable String tag, long micros, @Nullable Decided decided, @Nullable String refused) {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String REFUSED = "refused:";

    /**
     * What a decision object of Document 3 says: its outcome, or {@code error} for one that could not decide, the rule
     * that decided it, and the codes of the flags raised, in the order they were raised.
     */
    public record Decided(String outcome, @Nullable String decidingRuleId, List<String> flags) {

        public Decided {
            flags = List.copyOf(flags);
        }

        public static Decided of(JsonNode decision) {
            String outcome = "OK".equals(decision.path("status").asString(null))
                    ? decision.path("outcome").asString() : "error";
            return new Decided(outcome, decision.path("decidingRuleId").asString(null),
                    decision.path("flags").valueStream().map(flag -> flag.path("code").asString()).toList());
        }
    }

    /**
     * The report of a call as it was recorded: its arguments are read as far as they go, since a refused call's may
     * be wrong, and what it decided is kept only for a call that was answered.
     */
    static ToolCallReport of(ChatTurn.ToolCallRecord call, long micros, @Nullable Decided decided) {
        JsonNode arguments = parsed(call.arguments());
        JsonNode number = arguments.path("applicationNumber");
        JsonNode overrides = arguments.path("overrides");
        JsonNode tag = arguments.path("tag");
        String refused = call.outcome().startsWith(REFUSED) ? call.outcome().substring(REFUSED.length()) : null;
        return new ToolCallReport(call.tool(),
                number.isIntegralNumber() && number.canConvertToInt() ? number.intValue() : null,
                overrides instanceof ObjectNode object && !object.isEmpty() ? ToolResults.overridesText(object) : null,
                tag.isString() ? tag.asString() : null, micros, refused == null ? decided : null, refused);
    }

    private static JsonNode parsed(String arguments) {
        try {
            return JSON.readTree(arguments);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }
}
