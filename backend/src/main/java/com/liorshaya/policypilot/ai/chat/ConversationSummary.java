package com.liorshaya.policypilot.ai.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * One conversation of a sandbox as its list names it (Document 2, {@code GET /chat/sessions}): the session, the
 * version it is bound to, its first question, how many turns it holds, when it was opened and when it last answered.
 * A session that holds no turn yet is not a conversation and is not listed.
 */
public record ConversationSummary(UUID id, UUID rulesetId, int versionNo, String firstQuestion, int turns,
        Instant openedAt, Instant lastAt) {}
