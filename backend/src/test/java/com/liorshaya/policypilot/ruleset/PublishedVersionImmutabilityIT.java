package com.liorshaya.policypilot.ruleset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.node.ObjectNode;

/**
 * Published rule set versions are immutable at the database (Brief FR-7; Document 2, Data Model: "a DB trigger
 * forbids updates once status = PUBLISHED"; Document 6, Database grants and triggers are tested, not assumed).
 */
@Requirement({"FR-7", "NFR-3"})
class PublishedVersionImmutabilityIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private JdbcClient jdbc;

    private RulesetFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new RulesetFixtures(policies, rulesets);
    }

    // FR-7 first proof (Document 6 matrix). Expected: a database error from the trigger
    @Test
    void updateOfAPublishedVersionFailsAtTheDatabase() {
        UUID version = fixtures.seededVersion(UUID.randomUUID()).versionId();

        assertThatThrownBy(() -> jdbc.sql("update ruleset_version set status = 'DRAFT' where id = :id")
                .param("id", version).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("cannot change");
    }

    // FR-7. Expected: fixtures/policies/consumer-lending/ruleset.v1.json
    @Test
    void aRefusedUpdateLeavesThePublishedRulesEqualToTheFixture() {
        UUID sandbox = UUID.randomUUID();
        UUID version = fixtures.seededVersion(sandbox).versionId();

        try {
            jdbc.sql("update ruleset_version set rules_json = '{}'::jsonb where id = :id").param("id", version).update();
        } catch (DataAccessException expected) {
            // the trigger refuses it; the assertion below is that nothing changed
        }

        ObjectNode stored = fixtures.seededVersion(sandbox).document();
        assertThat(stored).isEqualTo(Fixtures.lendingV1());
    }

    // Document 2: the trigger applies once status = PUBLISHED. Expected: the new value stored
    @Test
    void updateOfADraftVersionIsAllowed() {
        VersionView draft = fixtures.draft(UUID.randomUUID());

        int updated = jdbc.sql("update ruleset_version set retired_ids = '[\"R-999\"]'::jsonb where id = :id")
                .param("id", draft.versionId()).update();

        assertThat(updated).isEqualTo(1);
    }

    // Document 5, principle 1: the API role cannot delete published versions. Expected: a PostgreSQL error
    @Test
    void deleteOfAPublishedVersionIsRefusedByTheDatabase() {
        UUID version = fixtures.seededVersion(UUID.randomUUID()).versionId();

        assertThatThrownBy(() -> jdbc.sql("delete from ruleset_version where id = :id").param("id", version).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }
}
