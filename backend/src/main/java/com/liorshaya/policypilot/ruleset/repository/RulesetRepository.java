package com.liorshaya.policypilot.ruleset.repository;

import com.liorshaya.policypilot.ruleset.entity.RulesetEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Rule sets, always looked up with the caller's sandbox (Document 5, Authorization (sandbox)): a rule set is visible
 * to its own sandbox and, when protected, to every sandbox; any other id reads as absent.
 */
public interface RulesetRepository extends JpaRepository<RulesetEntity, UUID> {

    @Query("""
            select r from RulesetEntity r
            where r.id = :id and (r.sandboxId = :sandboxId or r.protectedRow = true)
            """)
    Optional<RulesetEntity> findVisible(@Param("id") UUID id, @Param("sandboxId") UUID sandboxId);

    /** Every rule set the sandbox sees: the protected ones first, then its own, each oldest first. */
    @Query("""
            select r from RulesetEntity r
            where r.sandboxId = :sandboxId or r.protectedRow = true
            order by r.protectedRow desc, r.createdAt, r.id
            """)
    List<RulesetEntity> findAllVisible(@Param("sandboxId") UUID sandboxId);

    /** The sandbox's copy of a protected rule set, if it has one. */
    Optional<RulesetEntity> findBySandboxIdAndForkedFromId(UUID sandboxId, UUID forkedFromId);

    /** The seeded demo rule sets, oldest first. */
    List<RulesetEntity> findByProtectedRowTrueOrderByCreatedAt();
}
