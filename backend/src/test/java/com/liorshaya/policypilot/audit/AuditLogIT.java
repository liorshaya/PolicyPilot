package com.liorshaya.policypilot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditEntry;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The append-only audit log (Document 2, Data Model: audit_entry, "no update or delete grants on this table";
 * Document 5, principle 1; Document 6, Database grants and triggers are tested, not assumed).
 */
@Requirement("NFR-3")
class AuditLogIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private AuditLog audit;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private RulesetService rulesets;

    // Document 2, audit_entry columns. Expected: the columns of Document 2 and ApiIntegrationTest.START
    @Test
    void appendStoresActorActionVersionAndDetailsAtTheApiClock() {
        ObjectNode details = JSON.createObjectNode().put("note", "written by the test");

        AuditEntry entry = append("actor-1", details);

        assertThat(entry.at()).isEqualTo(START);
        assertThat(entry.actor()).isEqualTo("actor-1");
        assertThat(entry.action()).isEqualTo(AuditAction.PUBLISH);
        assertThat(entry.details()).isEqualTo(details);
        assertThat(audit.forVersion(seededVersion())).contains(entry);
    }

    // Document 6, Database grants: UPDATE through the application role. Expected: a PostgreSQL permission error
    @Test
    void updateOfAnAuditEntryIsRefusedByTheDatabase() {
        AuditEntry entry = append("actor-2", JSON.createObjectNode());

        assertThatThrownBy(() -> jdbc.sql("update audit_entry set actor = 'someone else' where id = :id")
                .param("id", entry.id()).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }

    // Document 2, audit_entry: no delete grant. Expected: a PostgreSQL permission error
    @Test
    void deleteOfAnAuditEntryIsRefusedByTheDatabase() {
        AuditEntry entry = append("actor-3", JSON.createObjectNode());

        assertThatThrownBy(() -> jdbc.sql("delete from audit_entry where id = :id").param("id", entry.id()).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }

    // Document 5, principle 1 (minimum grants); Document 2, Data Model. Expected: the role V4 creates
    @Test
    void theApiConnectsAsTheRestrictedApplicationRole() {
        String role = jdbc.sql("select current_user").query(String.class).single();

        assertThat(role).isEqualTo("policypilot_app");
    }

    /** One entry against the seeded version, written in a transaction of its own (the log needs the caller's). */
    private AuditEntry append(String actor, ObjectNode details) {
        UUID version = seededVersion();
        return transactions.execute(status -> audit.append(AuditAction.PUBLISH, actor, version, details));
    }

    /** The version of the seeded demo rule set, which every sandbox may read. */
    private UUID seededVersion() {
        UUID ruleset = rulesets.protectedRulesets().getFirst().id();
        return rulesets.version(ruleset, 1, UUID.randomUUID()).orElseThrow().versionId();
    }
}
