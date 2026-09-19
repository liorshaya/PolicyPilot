package com.liorshaya.policypilot.ruleset.repository;

import com.liorshaya.policypilot.ruleset.entity.RulesetVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Versions of a rule set whose visibility the caller has already checked. */
public interface RulesetVersionRepository extends JpaRepository<RulesetVersionEntity, UUID> {

    Optional<RulesetVersionEntity> findByRulesetIdAndVersionNo(UUID rulesetId, int versionNo);

    List<RulesetVersionEntity> findByRulesetIdOrderByVersionNo(UUID rulesetId);
}
