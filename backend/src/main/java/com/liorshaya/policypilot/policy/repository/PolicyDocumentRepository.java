package com.liorshaya.policypilot.policy.repository;

import com.liorshaya.policypilot.policy.entity.PolicyDocumentEntity;
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
}
