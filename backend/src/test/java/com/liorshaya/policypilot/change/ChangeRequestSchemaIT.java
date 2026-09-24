package com.liorshaya.policypilot.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The V10 schema as Document 2 describes it (Data Model, {@code change_request}) and as the API role reaches it: every
 * statement runs through the application pool, whose connections {@code SET ROLE policypilot_app}, so a missing or an
 * extra grant fails here and not first on Railway.
 */
@Requirement("FR-17")
class ChangeRequestSchemaIT extends ApiIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RulesetService rulesets;

    // Document 2, change_request: the columns in the order the Data Model lists them
    @Test
    void changeRequestHasTheColumnsDocumentTwoNames() {
        List<String> columns = jdbc.sql("""
                select column_name from information_schema.columns where table_name = 'change_request'
                order by ordinal_position""").query(String.class).list();

        assertThat(columns).containsExactly("id", "sandbox_id", "base_version_id", "request_text", "status",
                "patches_json", "rationale_json", "regression_json", "result_version_id", "created_at", "decided_at",
                "actor");
    }

    // Document 2: status PROPOSED, APPROVED or REJECTED. Expected: a check violation for a fourth value, on a row
    // stamped as decided so that the status check is the only one it breaks
    @Test
    void statusTakesOnlyTheThreeDocumentedValues() {
        assertThatThrownBy(() -> insert(UUID.randomUUID(), "DRAFT", true))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("change_request_status_check");
    }

    // Document 2: "the API role may insert and update rows but never delete one". Expected: the insert and the
    // decision go through, and the delete is refused by the database
    @Test
    void theApiRoleInsertsAndDecidesButNeverDeletes() {
        UUID id = UUID.randomUUID();

        int inserted = insert(id, "PROPOSED", false);
        int decided = jdbc.sql("update change_request set status = 'REJECTED', decided_at = now() where id = :id")
                .param("id", id).update();

        assertThat(inserted).isEqualTo(1);
        assertThat(decided).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("delete from change_request where id = :id").param("id", id).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }

    // Document 2: decided_at is the time of the decision, so a decided request has one and a proposed one has none.
    // Expected: a check violation for a decision without its time
    @Test
    void aDecisionCarriesItsTime() {
        UUID id = UUID.randomUUID();
        insert(id, "PROPOSED", false);

        assertThatThrownBy(() -> jdbc.sql("update change_request set status = 'APPROVED' where id = :id")
                .param("id", id).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("change_request_decided_is_stamped");
    }

    // Document 2, audit_entry.change_request_id: the request an entry is about. Expected: a foreign key violation for
    // an id no change request has
    @Test
    void anAuditEntryNamesOnlyAStoredChangeRequest() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into audit_entry (id, at, actor, action, ruleset_version_id, change_request_id, details_json)
                values (:id, now(), 'schema-check', 'CHANGE_PROPOSED', :version, :request, '{}')""")
                .param("id", UUID.randomUUID()).param("version", seededVersionId())
                .param("request", UUID.randomUUID()).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("audit_entry_change_request_fk");
    }

    private int insert(UUID id, String status, boolean decided) {
        return jdbc.sql("""
                insert into change_request (id, sandbox_id, base_version_id, request_text, status, patches_json,
                        rationale_json, created_at, decided_at, actor)
                values (:id, :sandbox, :version, 'raise the threshold', :status, '[]', '{}', now(),
                        case when :decided then now() end, 'schema-check')""")
                .param("id", id).param("sandbox", UUID.randomUUID()).param("version", seededVersionId())
                .param("status", status).param("decided", decided).update();
    }

    private UUID seededVersionId() {
        return rulesets.version(Seeded.lendingRuleset(rulesets).id(), 1, UUID.randomUUID())
                .orElseThrow().versionId();
    }
}
