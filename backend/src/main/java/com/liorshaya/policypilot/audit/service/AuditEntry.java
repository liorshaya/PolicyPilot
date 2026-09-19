package com.liorshaya.policypilot.audit.service;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/** One audit entry as other modules see it; {@code details} is the entry's JSON (Document 2, audit_entry). */
public record AuditEntry(UUID id, Instant at, String actor, AuditAction action, UUID rulesetVersionId,
        ObjectNode details) {}
