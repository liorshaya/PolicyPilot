package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.PostgresContainerSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The database the cloud site already has: its seeded version 1 was published before V7 existed. V7 must leave that
 * version {@code PENDING}, so the embedding job takes it at the next start, and a DRAFT with no status (Document 2,
 * RAG pipeline, Embedding). The migrations run on a database of their own, stopped at V6, and then to the end.
 */
class EmbeddingStatusBackfillIT extends PostgresContainerSupport {

    private static final UUID PUBLISHED = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID DRAFT = UUID.fromString("00000000-0000-0000-0000-00000000a002");

    // Document 2: a published version that has never been embedded is PENDING; a DRAFT has no status
    @Test
    void v7LeavesVersionsPublishedBeforeItPendingAndDraftsWithoutAStatus() throws SQLException {
        String url = freshDatabase();
        flyway(url).target("6").load().migrate();
        try (SingleConnectionDataSource data = new SingleConnectionDataSource(url, POSTGRES.getUsername(),
                POSTGRES.getPassword(), true)) {
            JdbcClient jdbc = JdbcClient.create(data);
            seedAPublishedVersionAndADraft(jdbc);

            flyway(url).load().migrate();

            assertThat(status(jdbc, PUBLISHED)).isEqualTo("PENDING");
            assertThat(status(jdbc, DRAFT)).isNull();
        }
    }

    private static String status(JdbcClient jdbc, UUID version) {
        return jdbc.sql("select embedding_status from ruleset_version where id = :id").param("id", version)
                .query(String.class).optional().orElse(null);
    }

    private static void seedAPublishedVersionAndADraft(JdbcClient jdbc) {
        UUID document = UUID.randomUUID();
        UUID policyVersion = UUID.randomUUID();
        UUID ruleset = UUID.randomUUID();
        jdbc.sql("""
                insert into policy_document (id, protected, title, language, created_at)
                values (:id, true, 'Lending', 'he', now())""").param("id", document).update();
        jdbc.sql("""
                insert into policy_version (id, document_id, version_no, raw_text, created_at)
                values (:id, :doc, 1, 'text', now())""").param("id", policyVersion).param("doc", document).update();
        jdbc.sql("""
                insert into ruleset (id, protected, name, domain, default_outcome, created_at)
                values (:id, true, 'Lending', 'lending', 'refer', now())""").param("id", ruleset).update();
        jdbc.sql("""
                insert into ruleset_version (id, ruleset_id, version_no, status, policy_version_id, rules_json,
                                             field_schema_json, published_at, published_by)
                values (:id, :rs, 1, 'PUBLISHED', :pv, '{}', '[]', now(), 'demo-analyst')""")
                .param("id", PUBLISHED).param("rs", ruleset).param("pv", policyVersion).update();
        jdbc.sql("""
                insert into ruleset_version (id, ruleset_id, version_no, status, policy_version_id, rules_json,
                                             field_schema_json)
                values (:id, :rs, 2, 'DRAFT', :pv, '{}', '[]')""")
                .param("id", DRAFT).param("rs", ruleset).param("pv", policyVersion).update();
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(String url) {
        return Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("embedding-dimension", "1536"));
    }

    /** A database of its own in the shared container, which lives as long as this JVM, so the name is fixed. */
    private static String freshDatabase() throws SQLException {
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement statement = admin.createStatement()) {
            statement.execute("create database v7_backfill");
        }
        return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/v7_backfill$1");
    }
}
