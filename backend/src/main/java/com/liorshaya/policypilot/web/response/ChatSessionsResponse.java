package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.ai.chat.ConversationSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The sandbox's conversations, newest first (Document 2, {@code GET /chat/sessions}). */
public record ChatSessionsResponse(@JsonProperty(required = true) List<Summary> sessions) {

    public static ChatSessionsResponse of(List<ConversationSummary> conversations) {
        return new ChatSessionsResponse(conversations.stream()
                .map(one -> new Summary(one.id(), one.rulesetId(), one.versionNo(), one.firstQuestion(), one.turns(),
                        one.openedAt(), one.lastAt()))
                .toList());
    }

    /**
     * One conversation: the session, the version it is bound to, its first question, how many turns it holds, when
     * it was opened and when it last answered.
     */
    public record Summary(
            @JsonProperty(required = true) UUID id,
            @JsonProperty(required = true) UUID rulesetId,
            @JsonProperty(required = true) int versionNo,
            @JsonProperty(required = true) String firstQuestion,
            @JsonProperty(required = true) int turns,
            @JsonProperty(required = true) Instant openedAt,
            @JsonProperty(required = true) Instant lastAt) {}
}
