package com.liorshaya.policypilot.audit.service;

import com.liorshaya.policypilot.audit.entity.AuditEntryEntity;
import com.liorshaya.policypilot.audit.repository.AuditEntryRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The append-only audit log (Brief NFR-3; Document 2, {@code audit}: "AuditEntry, append-only log service"). An entry
 * is written in the caller's transaction, so a publish and its entry commit or roll back together; the log can only
 * be appended to and read, and the database refuses the API's role anything else.
 */
@Service
public class AuditLog {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditEntryRepository entries;
    private final Clock clock;

    public AuditLog(AuditEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    /** Appends an entry at the API's clock, inside the caller's transaction (one is required). */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry append(AuditAction action, String actor, UUID rulesetVersionId, ObjectNode details) {
        return append(action, actor, rulesetVersionId, null, details);
    }

    /** Appends an entry about no version, the RESET entry of the demo reset, inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry append(AuditAction action, String actor, ObjectNode details) {
        return append(action, actor, null, null, details);
    }

    /** Appends an entry about a change request, which it names, inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntry append(AuditAction action, String actor, @Nullable UUID rulesetVersionId,
            @Nullable UUID changeRequestId, ObjectNode details) {
        return view(entries.save(new AuditEntryEntity(UUID.randomUUID(), clock.instant(), actor, action.name(),
                rulesetVersionId, changeRequestId, JSON.writeValueAsString(details))));
    }

    /** The entries of a rule set version, oldest first. */
    @Transactional(readOnly = true)
    public List<AuditEntry> forVersion(UUID rulesetVersionId) {
        return entries.findByRulesetVersionIdOrderByAtAscIdAsc(rulesetVersionId).stream().map(AuditLog::view).toList();
    }

    /** The entries of these versions, newest first (Document 2, {@code GET /audit}). */
    @Transactional(readOnly = true)
    public List<AuditEntry> newestFirst(Collection<UUID> rulesetVersionIds) {
        return entries.findByRulesetVersionIdInOrderByAtDescIdDesc(rulesetVersionIds).stream().map(AuditLog::view)
                .toList();
    }

    private static AuditEntry view(AuditEntryEntity entry) {
        return new AuditEntry(entry.getId(), entry.getAt(), entry.getActor(), AuditAction.valueOf(entry.getAction()),
                entry.getRulesetVersionId(), entry.getChangeRequestId(),
                (ObjectNode) JSON.readTree(entry.getDetails()));
    }
}
