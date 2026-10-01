package com.liorshaya.policypilot.decision.repository;

import com.liorshaya.policypilot.decision.entity.CaseFixtureEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Case fixtures: the seeded sets, and any case a sandbox stored itself. */
public interface CaseFixtureRepository extends JpaRepository<CaseFixtureEntity, UUID> {

    /** A seeded set, in case order (Document 2: the 200 synthetic applicants). */
    List<CaseFixtureEntity> findByFixtureSetOrderByCaseNo(String fixtureSet);

    boolean existsByFixtureSet(String fixtureSet);

    /**
     * Each field some case of a seeded set supplies, once per set (Document 2, a version's {@code fixtureSets}): the
     * keys of the cases' {@code fields_json}, read by PostgreSQL so the cases themselves stay in the database.
     */
    @Query(value = """
            SELECT DISTINCT c.fixture_set AS fixtureSet, f.name AS field
            FROM case_fixture c CROSS JOIN LATERAL jsonb_object_keys(c.fields_json) AS f(name)
            WHERE c.protected AND c.fixture_set IS NOT NULL
            """, nativeQuery = true)
    List<SuppliedField> suppliedFields();

    /** A field a seeded set supplies. */
    interface SuppliedField {

        String getFixtureSet();

        String getField();
    }
}
