package com.liorshaya.policypilot.audit.service;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * One audit entry as other modules see it; {@code details} is the entry's JSON (Document 2, audit_entry).
 *
 * @param changeRequestId the change request the entry is about, or null
 */
public record AuditEntry(UUID id, Instant at, String actor, AuditAction action, UUID rulesetVersionId,
        @Nullable UUID changeRequestId, ObjectNode details) {}
