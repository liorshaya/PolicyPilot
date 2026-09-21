package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The V7 schema as Document 2 describes it (Data Model, {@code chunk} and {@code ruleset_version}) and as the API
 * role reaches it: every statement below runs through the application pool, whose connections {@code SET ROLE
 * policypilot_app}, so a missing grant fails here and not first on Railway (Document 6, database grants are tested,
 * not assumed).
 */
@Requirement("FR-12")
class ChunkSchemaIT extends ApiIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RulesetService rulesets;

    // Document 2, Storage: vector(1536) for text-embedding-3-small; the openai profile. Expected: vector(1536)
    @Test
    void chunkEmbeddingColumnHasTheProfileDimension() {
        String type = jdbc.sql("""
                select format_type(atttypid, atttypmod) from pg_attribute
                where attrelid = 'chunk'::regclass and attname = 'embedding'""").query(String.class).single();

        assertThat(type).isEqualTo("vector(1536)");
    }

    // Document 2, chunk: "HNSW index on embedding (cosine), GIN index on tsv, B-tree index on ruleset_version_id"
    @Test
    void chunkHasTheThreeIndexesDocumentTwoNames() {
        List<String> definitions = jdbc.sql("select indexdef from pg_indexes where tablename = 'chunk'")
                .query(String.class).list();

        assertThat(definitions).anySatisfy(def -> assertThat(def).contains("USING hnsw (embedding vector_cosine_ops)"));
        assertThat(definitions).anySatisfy(def -> assertThat(def).contains("USING gin (tsv)"));
        // the unique constraint's index leads with the version, so it is the B-tree Document 2 names
        assertThat(definitions)
                .anySatisfy(def -> assertThat(def).contains("USING btree (ruleset_version_id, kind, ref_id)"));
    }

    // Document 2, chunk kinds: PARAGRAPH and RULE. Expected: a check violation for anything else
    @Test
    void chunkKindIsParagraphOrRule() {
        UUID version = seededVersionId();

        assertThatThrownBy(() -> insert(version, "DECISION", "17"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("chunk_kind_check");
    }

    // Document 5, principle 1, and the day-8 brief: the API role writes and replaces a version's chunks. Expected:
    // one row inserted, read back through the role, and deleted
    @Test
    void apiRoleInsertsReadsAndDeletesChunks() {
        UUID version = seededVersionId();
        String refId = "grant-check-" + UUID.randomUUID();

        int inserted = insert(version, "PARAGRAPH", refId);
        String text = jdbc.sql("select text from chunk where ruleset_version_id = :v and ref_id = :r")
                .param("v", version).param("r", refId).query(String.class).single();
        int deleted = jdbc.sql("delete from chunk where ruleset_version_id = :v and ref_id = :r")
                .param("v", version).param("r", refId).update();

        assertThat(inserted).isEqualTo(1);
        assertThat(text).isEqualTo("בדיקת הרשאות");
        assertThat(deleted).isEqualTo(1);
    }

    // Document 2, ruleset_version: embedding_status is PENDING, EMBEDDING, READY or FAILED. Expected: a check
    // violation for a fifth value
    @Test
    void embeddingStatusTakesOnlyTheFourDocumentedValues() {
        UUID version = seededVersionId();

        assertThatThrownBy(() -> jdbc.sql("update ruleset_version set embedding_status = 'DONE' where id = :id")
                .param("id", version).update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("ruleset_version_embedding_status_check");
    }

    private int insert(UUID version, String kind, String refId) {
        return jdbc.sql("""
                insert into chunk (id, ruleset_version_id, kind, ref_id, text, embedding, tsv, created_at)
                values (:id, :version, :kind, :ref, :text, array_fill(0.1, array[1536])::vector,
                        to_tsvector('simple', :text), now())""")
                .param("id", UUID.randomUUID()).param("version", version).param("kind", kind).param("ref", refId)
                .param("text", "בדיקת הרשאות").update();
    }

    private UUID seededVersionId() {
        return rulesets.version(rulesets.protectedRulesets().getFirst().id(), 1, UUID.randomUUID())
                .orElseThrow().versionId();
    }
}
