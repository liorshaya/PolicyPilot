package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.audit.service.AuditEntry;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.change.entity.ChangeRequestEntity;
import com.liorshaya.policypilot.change.repository.ChangeRequestRepository;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit log as one sandbox may read it (Document 2, {@code GET /audit} and {@code GET /audit/export}; Document 5,
 * Authorization (sandbox)): the entries of the versions it can see, newest first, without an entry about another
 * sandbox's change request, which the protected version every sandbox reads would otherwise show.
 */
@Service
public class AuditTrail {

    private final RulesetService rulesets;
    private final AuditLog audit;
    private final ChangeRequestRepository requests;

    public AuditTrail(RulesetService rulesets, AuditLog audit, ChangeRequestRepository requests) {
        this.rulesets = rulesets;
        this.audit = audit;
        this.requests = requests;
    }

    /** The entries of one version, newest first; empty when the sandbox cannot see the version. */
    @Transactional(readOnly = true)
    public Optional<List<AuditEntry>> ofVersion(UUID versionId, UUID sandboxId) {
        if (!rulesets.canSee(versionId, sandboxId)) {
            return Optional.empty();
        }
        return Optional.of(visible(audit.newestFirst(List.of(versionId)), sandboxId));
    }

    /** The entries of every version the sandbox can see, newest first. */
    @Transactional(readOnly = true)
    public List<AuditEntry> all(UUID sandboxId) {
        return visible(audit.newestFirst(rulesets.visibleVersionIds(sandboxId)), sandboxId);
    }

    private List<AuditEntry> visible(List<AuditEntry> entries, UUID sandboxId) {
        Set<UUID> named = entries.stream().map(AuditEntry::changeRequestId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> own = named.isEmpty() ? Set.of() : requests.findBySandboxIdAndIdIn(sandboxId, named).stream()
                .map(ChangeRequestEntity::getId).collect(Collectors.toSet());
        return entries.stream().filter(entry -> entry.changeRequestId() == null
                || own.contains(entry.changeRequestId())).toList();
    }
}
