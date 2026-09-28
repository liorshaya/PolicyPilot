package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.TokenUsage;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Where an answer goes as it happens (Document 2, {@code POST /chat/sessions/{id}/messages}): each tool call as it
 * ends, the text as it may be shown, then the citations, the usage and the stored message with the fixed sentence it
 * is, if it is one. The web layer turns each call into an SSE event.
 */
public interface ChatEvents {

    void tool(ToolCallReport call);

    void token(String text);

    void citations(List<ChatCitation> citations);

    void usage(TokenUsage usage, int toolCalls);

    /** @param fixed the fixed sentence the answer is, or null for an answer a model wrote */
    void done(UUID messageId, @Nullable FixedAnswer fixed);
}
