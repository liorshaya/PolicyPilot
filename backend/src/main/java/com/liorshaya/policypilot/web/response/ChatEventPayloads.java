package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The data of each event of {@code POST /chat/sessions/{id}/messages} (Document 2, API Surface): {@code token},
 * {@code citations}, {@code usage}, {@code done}, or {@code error} in place of the rest.
 */
public final class ChatEventPayloads {

    private ChatEventPayloads() {}

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

    /** The stored answer. */
    public record Done(UUID messageId) {}

    /** Why the stream ended without an answer: an error code of Document 2. */
    public record Failed(String code) {}
}
