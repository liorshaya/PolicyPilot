package com.liorshaya.policypilot.audit.repository;

import com.liorshaya.policypilot.audit.entity.AuditEntryEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** The audit log, append and read only: the interface has no update and no delete (Document 2, audit_entry). */
public interface AuditEntryRepository extends Repository<AuditEntryEntity, UUID> {

    AuditEntryEntity save(AuditEntryEntity entry);

    /** The entries of a version, oldest first. */
    List<AuditEntryEntity> findByRulesetVersionIdOrderByAtAscIdAsc(UUID rulesetVersionId);

    List<AuditEntryEntity> findByRulesetVersionIdInOrderByAtDescIdDesc(Collection<UUID> rulesetVersionIds);
}
