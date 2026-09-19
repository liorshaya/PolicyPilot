package com.liorshaya.policypilot.decision.service;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * A stored decision as the API returns it (Document 3, Decision object; Document 2, {@code GET /decisions/{id}}):
 * the engine's decision with the row id, the case it came from, the version that produced it and the timing the API
 * added.
 */
public record DecisionView(UUID id, @Nullable Integer caseNo, String rulesetId, int versionNo, UUID versionId,
        Instant decidedAt, long durationMicros, ObjectNode decision) {}
