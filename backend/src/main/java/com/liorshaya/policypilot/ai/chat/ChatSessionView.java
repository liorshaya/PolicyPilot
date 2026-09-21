package com.liorshaya.policypilot.ai.chat;

import java.util.UUID;

/**
 * A chat session as the API returns it: its id, its sandbox, and the version it is bound to, by rule set, DSL
 * document id and number (Document 4, Scoping).
 */
public record ChatSessionView(UUID id, UUID sandboxId, UUID rulesetId, String domain, int versionNo, UUID versionId) {}
