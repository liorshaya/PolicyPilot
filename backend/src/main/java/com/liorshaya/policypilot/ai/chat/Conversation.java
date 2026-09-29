package com.liorshaya.policypilot.ai.chat;

import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.FixedAnswer;
import com.liorshaya.policypilot.ai.service.chat.ToolCallReport;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A conversation as it was shown (Document 2, {@code GET /chat/sessions/{id}}): the session, the language its answers
 * are written in, when it was opened, and its turns oldest first, each the question, the answer as the stream sent it,
 * the sources it cited, the tool calls it made as the {@code tool} event reported them, and the fixed sentence it is.
 */
public record Conversation(ChatSessionView session, String language, Instant openedAt, List<Turn> turns) {

    public Conversation {
        turns = List.copyOf(turns);
    }

    /** One exchange: the question and the answer shown for it, with what the answer rested on. */
    public record Turn(int turn, String question, Instant askedAt, String answer, Instant answeredAt,
            List<ChatCitation> citations, List<ToolCallReport> toolCalls, @Nullable FixedAnswer fixed) {

        public Turn {
            citations = List.copyOf(citations);
            toolCalls = List.copyOf(toolCalls);
        }
    }
}
