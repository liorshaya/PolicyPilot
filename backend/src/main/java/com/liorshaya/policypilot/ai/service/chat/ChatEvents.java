package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.TokenUsage;
import java.util.List;
import java.util.UUID;

/**
 * Where an answer goes as it happens (Document 2, {@code POST /chat/sessions/{id}/messages}): the text as it may be
 * shown, then the citations, the usage and the stored message. The web layer turns each call into an SSE event.
 */
public interface ChatEvents {

    void token(String text);

    void citations(List<ChatCitation> citations);

    void usage(TokenUsage usage, int toolCalls);

    void done(UUID messageId);
}
