package com.liorshaya.policypilot.ruleset.repository;

import com.liorshaya.policypilot.ruleset.entity.RuleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The rule rows of published versions. */
public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {

    List<RuleEntity> findByRulesetVersionIdOrderByPriorityAscRuleIdAsc(UUID rulesetVersionId);
}
