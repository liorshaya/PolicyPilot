package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.ai.chat.Conversation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A conversation as it was shown (Document 2, {@code GET /chat/sessions/{id}}): the session, its version and its
 * language, when it was opened, and its turns oldest first, each with what the stream sent for it.
 */
public record ChatConversationResponse(
        @JsonProperty(required = true) UUID id,
        @JsonProperty(required = true) UUID rulesetId,
        @JsonProperty(required = true) int versionNo,
        @JsonProperty(required = true) String language,
        @JsonProperty(required = true) Instant openedAt,
        @JsonProperty(required = true) List<Turn> turns) {

    public static ChatConversationResponse of(Conversation conversation) {
        int versionNo = conversation.session().versionNo();
        return new ChatConversationResponse(conversation.session().id(), conversation.session().rulesetId(), versionNo,
                conversation.language(), conversation.openedAt(), conversation.turns().stream()
                        .map(turn -> new Turn(turn.turn(), turn.question(), turn.askedAt(), turn.answer(),
                                turn.answeredAt(), turn.citations().stream().map(ChatEventPayloads.Cite::of).toList(),
                                turn.toolCalls().stream().map(call -> ChatEventPayloads.Tool.of(call, versionNo))
                                        .toList(),
                                turn.fixed() == null ? null : turn.fixed().json()))
                        .toList());
    }

    /**
     * One exchange: the question and the answer as it was shown, markers included, its sources as the
     * {@code citations} event sent them, its tool calls as the {@code tool} events reported them, and the fixed
     * sentence it is, {@code not_covered} or {@code tool_limit}, or null for an answer the model wrote.
     */
    public record Turn(
            @JsonProperty(required = true) int turn,
            @JsonProperty(required = true) String question,
            @JsonProperty(required = true) Instant askedAt,
            @JsonProperty(required = true) String answer,
            @JsonProperty(required = true) Instant answeredAt,
            @JsonProperty(required = true) List<ChatEventPayloads.Cite> citations,
            @JsonProperty(required = true) List<ChatEventPayloads.Tool> toolCalls,
            @JsonProperty(required = true) @Schema(nullable = true) @Nullable String fixed) {}
}
