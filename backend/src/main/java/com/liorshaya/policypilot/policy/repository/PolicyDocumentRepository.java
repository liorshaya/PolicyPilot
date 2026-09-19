package com.liorshaya.policypilot.policy.repository;

import com.liorshaya.policypilot.policy.entity.PolicyDocumentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Policy documents, always looked up with the caller's sandbox (Document 5, Authorization (sandbox)): a document is
 * visible to its own sandbox and, when protected, to every sandbox; any other id reads as absent.
 */
public interface PolicyDocumentRepository extends JpaRepository<PolicyDocumentEntity, UUID> {

    @Query("""
            select d from PolicyDocumentEntity d
            where d.id = :id and (d.sandboxId = :sandboxId or d.protectedRow = true)
            """)
    Optional<PolicyDocumentEntity> findVisible(@Param("id") UUID id, @Param("sandboxId") UUID sandboxId);

    /** Every policy the sandbox sees: the protected ones first, then its own, each oldest first. */
    @Query("""
            select d from PolicyDocumentEntity d
            where d.sandboxId = :sandboxId or d.protectedRow = true
            order by d.protectedRow desc, d.createdAt, d.id
            """)
    List<PolicyDocumentEntity> findAllVisible(@Param("sandboxId") UUID sandboxId);

    /** The sandbox's copy of a protected policy, if it has one. */
    Optional<PolicyDocumentEntity> findBySandboxIdAndForkedFromId(UUID sandboxId, UUID forkedFromId);

    /** The seeded demo policies, oldest first. */
    List<PolicyDocumentEntity> findByProtectedRowTrueOrderByCreatedAt();
}
