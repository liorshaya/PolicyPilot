package com.liorshaya.policypilot.decision.repository;

import com.liorshaya.policypilot.decision.entity.DecisionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Decisions, always looked up with the caller's sandbox (Document 5, Authorization (sandbox)): another sandbox's
 * decision reads as absent.
 */
public interface DecisionRepository extends JpaRepository<DecisionEntity, UUID> {

    Optional<DecisionEntity> findByIdAndSandboxId(UUID id, UUID sandboxId);

    /** Every decision of this sandbox on this version, oldest first: what the statistics are counted from. */
    List<DecisionEntity> findBySandboxIdAndRulesetVersionIdOrderByDecidedAtAscIdAsc(UUID sandboxId, UUID versionId);

    /**
     * This sandbox's decisions of one fixture case on one version, newest first: the first is what a chat means by
     * "application 17" (Document 2, Tools available to the answer prompt).
     */
    @Query("""
            select d from DecisionEntity d, CaseFixtureEntity c
            where d.caseId = c.id and d.sandboxId = :sandboxId and d.rulesetVersionId = :versionId
              and c.caseNo = :caseNo
            order by d.decidedAt desc, d.id desc""")
    List<DecisionEntity> findOfCaseNewestFirst(UUID sandboxId, UUID versionId, int caseNo, Limit limit);
}
