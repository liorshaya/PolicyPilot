package com.liorshaya.policypilot.decision.repository;

import com.liorshaya.policypilot.decision.entity.CaseFixtureEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Case fixtures: the seeded sets, and any case a sandbox stored itself. */
public interface CaseFixtureRepository extends JpaRepository<CaseFixtureEntity, UUID> {

    /** A seeded set, in case order (Document 2: the 200 synthetic applicants). */
    List<CaseFixtureEntity> findByFixtureSetOrderByCaseNo(String fixtureSet);

    boolean existsByFixtureSet(String fixtureSet);
}
