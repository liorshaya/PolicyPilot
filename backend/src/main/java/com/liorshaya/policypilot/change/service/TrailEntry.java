package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.audit.service.AuditEntry;
import org.jspecify.annotations.Nullable;

/**
 * An audit entry as a sandbox reads it (Document 2, {@code GET /audit}): the entry, and the number of the change
 * request it is about, which the append-only log does not hold and is read from the request (added 2026-09-28,
 * Register phase 4).
 *
 * @param changeRequestNumber the number of the sandbox's request the entry is about, or null for an entry about none
 */
public record TrailEntry(AuditEntry entry, @Nullable Integer changeRequestNumber) {}
