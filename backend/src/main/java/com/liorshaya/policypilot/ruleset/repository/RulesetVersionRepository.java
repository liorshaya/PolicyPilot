package com.liorshaya.policypilot.ruleset.repository;

import com.liorshaya.policypilot.ruleset.entity.RulesetVersionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Versions of a rule set whose visibility the caller has already checked. */
public interface RulesetVersionRepository extends JpaRepository<RulesetVersionEntity, UUID> {

    Optional<RulesetVersionEntity> findByRulesetIdAndVersionNo(UUID rulesetId, int versionNo);

    List<RulesetVersionEntity> findByRulesetIdOrderByVersionNo(UUID rulesetId);

    /**
     * Moves one version's embedding status, only from one of {@code from}: the conditional update is what lets a
     * version be claimed once. The trigger allows this column, and only this column, on a published version.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RulesetVersionEntity v set v.embeddingStatus = :to
            where v.id = :id and v.embeddingStatus in :from""")
    int moveEmbeddingStatus(UUID id, Collection<String> from, String to);

    /** Every version whose embedding status is one of {@code from}, moved to {@code to}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RulesetVersionEntity v set v.embeddingStatus = :to where v.embeddingStatus in :from")
    int moveEveryEmbeddingStatus(Collection<String> from, String to);

    @Query("select v.id from RulesetVersionEntity v where v.embeddingStatus = :status order by v.publishedAt")
    List<UUID> findIdsByEmbeddingStatus(String status);
}
