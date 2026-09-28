package com.liorshaya.policypilot.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.liorshaya.policypilot.support.Requirement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The database the cloud site already has: its change requests were stored before V12 gave them a number (Document 2,
 * change_request, added 2026-09-28 for Register phase 4). V12 numbers each sandbox's requests from 1 in the order they
 * were stored, and from then on a number is taken once in a sandbox and every request has one. The migrations run on a
 * database of their own, stopped at V11, and then to the end.
 */
@Requirement("FR-17")
class ChangeRequestNumberBackfillIT extends PostgresContainerSupport {

    private static final UUID SANDBOX_A = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID SANDBOX_B = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    /** The earliest request of A has the highest id, so an order by id alone would number it last. */
    private static final UUID FIRST_OF_A = UUID.fromString("00000000-0000-0000-0000-000000000a03");
    private static final UUID SECOND_OF_A = UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID THIRD_OF_A = UUID.fromString("00000000-0000-0000-0000-000000000a02");
    private static final UUID FIRST_OF_B = UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private static final Instant MORNING = Instant.parse("2026-09-24T09:00:00Z");
    private static final Instant NOON = Instant.parse("2026-09-24T12:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-09-24T18:00:00Z");

    private static SingleConnectionDataSource data;
    private static JdbcClient jdbc;
    private static UUID version;

    @BeforeAll
    static void storeRequestsBeforeV12ThenMigrate() throws SQLException {
        String url = freshDatabase();
        flyway(url).target("11").load().migrate();
        data = new SingleConnectionDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword(), true);
        jdbc = JdbcClient.create(data);
        version = aPublishedVersion();
        store(THIRD_OF_A, SANDBOX_A, EVENING);
        store(FIRST_OF_B, SANDBOX_B, NOON);
        store(SECOND_OF_A, SANDBOX_A, EVENING);
        store(FIRST_OF_A, SANDBOX_A, MORNING);

        flyway(url).load().migrate();
    }

    @AfterAll
    static void close() {
        data.destroy();
    }

    // Expected: each sandbox's requests from 1 by the time they were stored, the lower id first of two stored at one
    // instant, and another sandbox's first request 1 as well
    @Test
    void v12NumbersEachSandboxsRequestsFromOneInTheOrderTheyWereStored() {
        Map<UUID, Integer> numbers = Map.of(FIRST_OF_A, 1, SECOND_OF_A, 2, THIRD_OF_A, 3, FIRST_OF_B, 1);

        assertThat(numbers.keySet().stream().map(id -> Map.entry(id, numberOf(id))))
                .containsExactlyInAnyOrderElementsOf(numbers.entrySet());
    }

    // Expected: a number the sandbox already has is refused, and so is a request with no number
    @Test
    void aNumberIsTakenOnceInASandboxAndEveryRequestHasOne() {
        assertThatThrownBy(() -> storeNumbered(UUID.randomUUID(), SANDBOX_A, EVENING, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> store(UUID.randomUUID(), SANDBOX_B, EVENING))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.sql("select count(*) from change_request").query(Long.class).single()).isEqualTo(4);
    }

    private static int numberOf(UUID id) {
        return jdbc.sql("select number from change_request where id = :id").param("id", id).query(Integer.class)
                .single();
    }

    /** A PROPOSED request as the API stored one before V12, which named no number. */
    private static void store(UUID id, UUID sandbox, Instant at) {
        jdbc.sql("""
                insert into change_request (id, sandbox_id, base_version_id, request_text, status, patches_json,
                                            rationale_json, regression_json, created_at, actor)
                values (:id, :sandbox, :version, 'request', 'PROPOSED', '[]', '{}', '{}', :at, :actor)""")
                .param("id", id).param("sandbox", sandbox).param("version", version).param("at", Timestamp.from(at))
                .param("actor", sandbox.toString()).update();
    }

    /** The same request with its number, as the API stores one from V12 on. */
    private static void storeNumbered(UUID id, UUID sandbox, Instant at, int number) {
        jdbc.sql("""
                insert into change_request (id, sandbox_id, base_version_id, request_text, status, patches_json,
                                            rationale_json, regression_json, created_at, actor, number)
                values (:id, :sandbox, :version, 'request', 'PROPOSED', '[]', '{}', '{}', :at, :actor, :number)""")
                .param("id", id).param("sandbox", sandbox).param("version", version).param("at", Timestamp.from(at))
                .param("actor", sandbox.toString()).param("number", number).update();
    }

    private static UUID aPublishedVersion() {
        UUID document = UUID.randomUUID();
        UUID policyVersion = UUID.randomUUID();
        UUID ruleset = UUID.randomUUID();
        UUID published = UUID.randomUUID();
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
                .param("id", published).param("rs", ruleset).param("pv", policyVersion).update();
        return published;
    }

    private static FluentConfiguration flyway(String url) {
        return Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("embedding-dimension", "1536"));
    }

    /** A database of its own in the shared container, which lives as long as this JVM, so the name is fixed. */
    private static String freshDatabase() throws SQLException {
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement statement = admin.createStatement()) {
            statement.execute("create database v12_backfill");
        }
        return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/v12_backfill$1");
    }
}
