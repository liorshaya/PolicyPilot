package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.ai.chat.ChatSessionView;
import java.util.UUID;

/** What {@code POST /chat/sessions} returns: the session and the version it is bound to, and its language. */
public record ChatSessionResponse(UUID id, UUID rulesetId, int versionNo, String language) {

    public static ChatSessionResponse of(ChatSessionView session, String language) {
        return new ChatSessionResponse(session.id(), session.rulesetId(), session.versionNo(), language);
    }
}
