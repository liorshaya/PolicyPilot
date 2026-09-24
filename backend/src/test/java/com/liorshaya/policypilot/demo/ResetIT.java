package com.liorshaya.policypilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.demo.repository.SandboxPurge;
import com.liorshaya.policypilot.demo.service.ResetJob;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.EcsLogCapture;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.ServerSentEvents;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo reset (Document 2, {@code demo.ResetJob}; Document 5, Availability, Nightly reset, and Data Protection,
 * Deletion; Work Plan day 15: "the protected rule set's checksum equals the fixture after a reset; sandboxes past
 * retention removed; the RESET audit entry"). A stale sandbox is built through the API on a clock 30 hours back: the
 * scripted change proposed on the seeded version and approved into the sandbox's copy, the 200 cases decided, a chat
 * session. Isolated: the test moves the clock, and a reset deletes whatever is stale in the shared database.
 */
@Import(RecordedModel.class)
@Isolated
@Requirement("NFR-3")
class ResetIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration PAST_RETENTION = Duration.ofHours(30);
    private static final String PRESENTER = "presenter-of-reset-it";

    @Autowired
    private RecordedGateway model;

    @Autowired
    private ResetJob reset;

    @Autowired
    private SandboxPurge purge;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entities;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private JdbcClient jdbc;

    private UUID seeded;
    private UUID seededVersion;

    @BeforeEach
    void findTheSeededVersion() {
        model.reset();
        seeded = jdbc.sql("select id from ruleset where protected").query(UUID.class).single();
        seededVersion = jdbc.sql("select id from ruleset_version where ruleset_id = :id and version_no = 1")
                .param("id", seeded).query(UUID.class).single();
        awaitReady(List.of(seededVersion));
    }

    // Document 5, Deletion: a sandbox idle for 24 hours goes "with everything it holds". Expected: before the reset
    // the sandbox holds rows in every table the test filled; after it, none in any table
    @Test
    void aStaleSandboxIsDeletedWithEverythingItHolds() {
        UUID sandbox = sandboxAt(START.minus(PAST_RETENTION));

        Map<String, Integer> before = holdings(sandbox);
        reset.reset(ResetJob.Trigger.MANUAL, PRESENTER);

        assertThat(before).containsEntry("decisions", 200).containsEntry("chatSessions", 1)
                .containsEntry("chatMessages", 1).containsEntry("changeRequests", 1).containsEntry("rulesets", 1);
        assertThat(before.get("versions")).isEqualTo(2);
        assertThat(before.get("rules")).isPositive();
        assertThat(before.get("chunks")).isPositive();
        assertThat(before.get("auditEntries")).isGreaterThanOrEqualTo(3);
        assertThat(holdings(sandbox)).allSatisfy((table, rows) -> assertThat(rows).as(table).isZero());
    }

    // Document 2: "deletes every sandbox whose newest row is older than 24 hours". Expected: a sandbox made 30 hours
    // ago that opened a chat session an hour ago keeps every row
    @Test
    void aSandboxActiveWithinTheWindowIsKept() {
        UUID sandbox = sandboxAt(START.minus(PAST_RETENTION));
        clock.set(START.minus(Duration.ofHours(1)));
        jdbc.sql("""
                insert into chat_session (id, sandbox_id, ruleset_version_id, created_at)
                values (:id, :sandbox, :version, :at)""").param("id", UUID.randomUUID()).param("sandbox", sandbox)
                .param("version", seededVersion).param("at", Timestamp.from(clock.instant())).update();
        clock.set(START);

        Map<String, Integer> before = holdings(sandbox);
        reset.reset(ResetJob.Trigger.MANUAL, PRESENTER);

        assertThat(holdings(sandbox)).isEqualTo(before);
    }

    // Document 5, principle 1 and Nightly reset: a protected row is never deleted, and the protected rule set stays
    // the fixture. Expected: the seeded version's document equals ruleset.v1.json, the 200 protected cases and the
    // seed's PUBLISH entry by demo-analyst are still there, and the seed had nothing to re-seed
    @Test
    void theProtectedRowsStayAndTheRuleSetIsStillTheFixture() {
        sandboxAt(START.minus(PAST_RETENTION));

        ResetJob.Result result = reset.reset(ResetJob.Trigger.MANUAL, PRESENTER);

        assertThat(rulesets.version(seeded, 1, UUID.randomUUID()).orElseThrow().document())
                .isEqualTo(Fixtures.lendingV1());
        assertThat(jdbc.sql("select count(*) from case_fixture where protected").query(Integer.class).single())
                .isEqualTo(200);
        assertThat(jdbc.sql("""
                select count(*) from audit_entry
                where ruleset_version_id = :v and action = 'PUBLISH' and actor = 'demo-analyst'""")
                .param("v", seededVersion).query(Integer.class).single()).isEqualTo(1);
        assertThat(result.reseeded()).isFalse();
    }

    // Document 5, Nightly reset: "re-seeds if the protected rows are missing". The API role cannot delete a
    // protected policy or rule set at all, but it can delete the protected cases. Expected: with them gone, the
    // reset reports a re-seed and the 200 are back (flushed, so the count sees them); the whole test rolls back
    @Test
    void theMissingProtectedCasesAreReseeded() {
        Integer[] restored = new Integer[1];
        boolean[] reseeded = new boolean[1];

        transactions.executeWithoutResult(status -> {
            jdbc.sql("delete from decision where case_id in (select id from case_fixture where protected)").update();
            jdbc.sql("delete from case_fixture where protected").update();
            reseeded[0] = reset.reset(ResetJob.Trigger.MANUAL, PRESENTER).reseeded();
            entities.flush();
            restored[0] = jdbc.sql("select count(*) from case_fixture where protected").query(Integer.class).single();
            status.setRollbackOnly();
        });

        assertThat(reseeded[0]).isTrue();
        assertThat(restored[0]).isEqualTo(200);
    }

    // Document 2, POST /admin/reset: "the response cache, the token ledger and the model call log are left as they
    // are". Expected: a cached answer and a call of 30 hours ago, and today's ledger row, the same after the reset
    @Test
    void theResponseCacheTheLedgerAndTheCallLogStay() {
        String key = "reset-it-" + UUID.randomUUID();
        Instant old = START.minus(PAST_RETENTION);
        jdbc.sql("""
                insert into model_response_cache (key, prompt_name, response_json, created_at)
                values (:key, 'answer', '{"text":"שמור"}'::jsonb, :at)""").param("key", key)
                .param("at", Timestamp.from(old)).update();
        UUID call = UUID.randomUUID();
        jdbc.sql("""
                insert into model_call (id, at, prompt_name, prompt_version, model, provider, attempt, input_tokens,
                    output_tokens, latency_ms, validation_result, cache_hit)
                values (:id, :at, 'answer', 'v1', 'test-model', 'openai', 1, 10, 5, 100, 'VALID', false)""")
                .param("id", call).param("at", Timestamp.from(old)).update();
        String ledger = "select coalesce(json_agg(t order by day)::text, '[]') from token_ledger t";
        String ledgerBefore = jdbc.sql(ledger).query(String.class).single();

        reset.reset(ResetJob.Trigger.MANUAL, PRESENTER);

        assertThat(jdbc.sql("select response_json->>'text' from model_response_cache where key = :key")
                .param("key", key).query(String.class).single()).isEqualTo("שמור");
        assertThat(jdbc.sql("select count(*) from model_call where id = :id").param("id", call)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql(ledger).query(String.class).single()).isEqualTo(ledgerBefore);
    }

    // Document 5, principle 1: the procedure measures the 24 hours against the earlier of the API's clock and the
    // database's, so the API cannot widen the window. Expected: a sandbox active at the database's now survives a
    // purge asked for ten years after the test clock
    @Test
    void theApiCannotWidenTheWindow() {
        UUID sandbox = UUID.randomUUID();
        jdbc.sql("""
                insert into chat_session (id, sandbox_id, ruleset_version_id, created_at)
                values (:id, :sandbox, :version, now())""").param("id", UUID.randomUUID())
                .param("sandbox", sandbox).param("version", seededVersion).update();

        transactions.executeWithoutResult(status -> purge.purge(START.plus(3650, ChronoUnit.DAYS)));

        assertThat(holdings(sandbox)).containsEntry("chatSessions", 1);
    }

    // Document 2, audit_entry and POST /admin/reset: one RESET entry, about no version, naming its actor and the
    // counts. Expected: the second of two resets in a row finds nothing stale, so its entry says 0 sandboxes and
    // no re-seed; the first's entry counts the stale sandbox's 200 decisions and its change request
    @Test
    void eachResetWritesOneResetEntryWithItsActorAndCounts() {
        sandboxAt(START.minus(PAST_RETENTION));
        String first = PRESENTER + "-" + UUID.randomUUID();
        String second = PRESENTER + "-" + UUID.randomUUID();

        reset.reset(ResetJob.Trigger.MANUAL, first);
        reset.reset(ResetJob.Trigger.NIGHTLY, second);

        JsonNode firstEntry = resetEntry(first);
        JsonNode secondEntry = resetEntry(second);
        assertThat(firstEntry.required("trigger").asString()).isEqualTo("manual");
        assertThat(firstEntry.required("sandboxesDeleted").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(firstEntry.required("decisionsDeleted").asInt()).isGreaterThanOrEqualTo(200);
        assertThat(firstEntry.required("changeRequestsDeleted").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(secondEntry.required("trigger").asString()).isEqualTo("nightly");
        assertThat(secondEntry.required("sandboxesDeleted").asInt()).isZero();
        assertThat(secondEntry.required("auditEntriesDeleted").asInt()).isZero();
        assertThat(secondEntry.required("reseeded").asBoolean()).isFalse();
        assertThat(jdbc.sql("""
                select count(*) from audit_entry
                where action = 'RESET' and actor in (:actors) and ruleset_version_id is null""")
                .param("actors", List.of(first, second)).query(Integer.class).single()).isEqualTo(2);
    }

    // Document 5, Security Logging: "Nightly reset | Trigger (nightly or manual), sandboxes deleted, re-seeded flag |
    // demo.reset". Expected: one ECS line with the three fields, for a reset that found nothing to delete
    @Test
    void aResetIsLoggedAsDemoReset() {
        reset.reset(ResetJob.Trigger.MANUAL, PRESENTER);
        try (EcsLogCapture log = EcsLogCapture.of(ResetJob.class)) {
            reset.reset(ResetJob.Trigger.NIGHTLY, ResetJob.NIGHTLY_ACTOR);

            EcsLogCapture.Line line = log.await("demo.reset", event -> true, Duration.ofSeconds(5)).orElseThrow();

            assertThat(line.refused()).isNull();
            assertThat(line.json().required("trigger").asString()).isEqualTo("nightly");
            assertThat(line.json().required("sandboxesDeleted").asInt()).isZero();
            assertThat(line.json().required("reseeded").asBoolean()).isFalse();
        }
    }

    /**
     * A sandbox built through the API at {@code at}: the scripted change proposed on the seeded version and approved,
     * both versions of its copy embedded, the 200 cases decided and a chat session with one message. The clock goes
     * back to {@link #START} after.
     */
    private UUID sandboxAt(Instant at) {
        clock.set(at);
        String cookie = api().login();
        UUID sandbox = UUID.fromString(cookie.substring(0, cookie.indexOf('.')));
        model.willAnswer(ChangeRequests.scriptedPatches().toString());
        String change = ServerSentEvents.parse(api().post("/api/v1/rulesets/" + seeded + "/versions/1/changes").web()
                .cookie(cookie).json(JSON.createObjectNode().put("text", ChangeRequests.scripted()).toString()).send()
                .body()).first("proposal").required("id").asString();
        assertThat(api().post("/api/v1/changes/" + change + "/approve").web().cookie(cookie).send().statusCode())
                .isEqualTo(200);
        String batch = api().post("/api/v1/rulesets/" + seeded + "/versions/1/decide").web().cookie(cookie)
                .json("{\"fixtureSet\":\"cases-200\"}").send().body();
        assertThat((List<?>) JsonPath.read(batch, "$.results")).hasSize(200);
        UUID session = UUID.randomUUID();
        Timestamp stamp = Timestamp.from(at);
        jdbc.sql("""
                insert into chat_session (id, sandbox_id, ruleset_version_id, created_at)
                values (:id, :sandbox, :version, :at)""").param("id", session).param("sandbox", sandbox)
                .param("version", seededVersion).param("at", stamp).update();
        jdbc.sql("""
                insert into chat_message (id, session_id, turn, role, content, at)
                values (:id, :session, 1, 'USER', 'שאלה של ארגז חול ישן', :at)""").param("id", UUID.randomUUID())
                .param("session", session).param("at", stamp).update();
        awaitReady(jdbc.sql("""
                select v.id from ruleset_version v join ruleset r on r.id = v.ruleset_id where r.sandbox_id = :s""")
                .param("s", sandbox).query(UUID.class).list());
        clock.set(START);
        return sandbox;
    }

    /** The rows a sandbox holds, per table, counted the way Document 5's Deletion row lists them. */
    private Map<String, Integer> holdings(UUID sandbox) {
        String versions = "select v.id from ruleset_version v join ruleset r on r.id = v.ruleset_id "
                + "where r.sandbox_id = :s";
        Map<String, String> counts = Map.ofEntries(
                Map.entry("policies", "select count(*) from policy_document where sandbox_id = :s"),
                Map.entry("rulesets", "select count(*) from ruleset where sandbox_id = :s"),
                Map.entry("versions", "select count(*) from (" + versions + ") v"),
                Map.entry("rules", "select count(*) from rule where ruleset_version_id in (" + versions + ")"),
                Map.entry("chunks", "select count(*) from chunk where ruleset_version_id in (" + versions + ")"),
                Map.entry("decisions", "select count(*) from decision where sandbox_id = :s"),
                Map.entry("chatSessions", "select count(*) from chat_session where sandbox_id = :s"),
                Map.entry("chatMessages", "select count(*) from chat_message m join chat_session c "
                        + "on c.id = m.session_id where c.sandbox_id = :s"),
                Map.entry("changeRequests", "select count(*) from change_request where sandbox_id = :s"),
                Map.entry("auditEntries", "select count(*) from audit_entry where ruleset_version_id in ("
                        + versions + ") or change_request_id in "
                        + "(select id from change_request where sandbox_id = :s)"));
        return counts.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                count -> jdbc.sql(count.getValue()).param("s", sandbox).query(Integer.class).single()));
    }

    private JsonNode resetEntry(String actor) {
        return JSON.readTree(jdbc.sql("""
                select details_json::text from audit_entry where action = 'RESET' and actor = :a""")
                .param("a", actor).query(String.class).single());
    }

    private void awaitReady(List<UUID> versions) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (jdbc.sql("""
                select count(*) from ruleset_version
                where id in (:ids) and embedding_status is distinct from 'READY'""")
                .param("ids", versions).query(Integer.class).single() > 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("a version never became READY");
            }
            Thread.onSpinWait();
        }
    }
}
