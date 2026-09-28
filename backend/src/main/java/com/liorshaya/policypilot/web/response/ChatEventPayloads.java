package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ToolCallReport;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The data of each event of {@code POST /chat/sessions/{id}/messages} (Document 2, API Surface): {@code tool},
 * {@code token}, {@code citations}, {@code usage}, {@code done}, or {@code error} in place of the rest.
 */
public final class ChatEventPayloads {

    private ChatEventPayloads() {}

    /**
     * A tool call as it ended (Document 2, added 2026-09-28 for Register phase 4): the tool, what it ran on, the
     * session's version and the call's time; for a decision or a simulation the outcome, the deciding rule and the
     * flags by code; a refused call names its reason, and has no outcome.
     */
    public record Tool(String tool, @Nullable Integer applicationNumber, @Nullable String overrides,
            @Nullable String tag, int versionNo, long micros, @Nullable String outcome,
            @Nullable String decidingRuleId, List<String> flags, @Nullable String refused) {

        public static Tool of(ToolCallReport call, int versionNo) {
            ToolCallReport.Decided decided = call.decided();
            return new Tool(call.tool(), call.applicationNumber(), call.overrides(), call.tag(), versionNo,
                    call.micros(), decided == null ? null : decided.outcome(),
                    decided == null ? null : decided.decidingRuleId(), decided == null ? List.of() : decided.flags(),
                    call.refused());
        }
    }

    /** A piece of the answer as it may be shown, markers included. */
    public record Token(String text) {}

    /** The sources the answer cited, each once, in the order it first cited them. */
    public record Citations(List<Cite> citations) {

        public static Citations of(List<ChatCitation> citations) {
            return new Citations(citations.stream().map(Cite::of).toList());
        }
    }

    /** One source: a paragraph, a rule, a decision or a simulation. */
    public record Cite(String id, String kind,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer paragraph,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String ruleId,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String label,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer applicationNumber,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String outcome,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String detail) {

        static Cite of(ChatCitation citation) {
            return new Cite(citation.id(), citation.kind().name(), citation.paragraph(), citation.ruleId(),
                    citation.label(), citation.applicationNumber(), citation.outcome(), citation.detail());
        }
    }

    /** What the answer cost and how many tool calls it made. */
    public record Usage(int inputTokens, int outputTokens, int toolCalls) {}

    /** The stored answer, and the fixed sentence it is, {@code not_covered} or {@code tool_limit}, or null. */
    public record Done(UUID messageId, @Nullable String fixed) {}

    /** Why the stream ended without an answer: an error code of Document 2. */
    public record Failed(String code) {}
}
