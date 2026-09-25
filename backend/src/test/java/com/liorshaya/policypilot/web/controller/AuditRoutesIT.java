package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.common.Csv;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import com.liorshaya.policypilot.support.ServerSentEvents;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The audit routes (Document 2, {@code GET /audit?versionId=} and {@code GET /audit/export}; Document 5,
 * Authorization (sandbox)): the entries of the seeded lending version and of the sandbox's own copy, as the change
 * routes write them. Every other sandbox's proposals land on the same seeded version, so each test sees the isolation
 * rule at work: an entry about another sandbox's change request is never shown.
 */
@Requirement("FR-19")
@Import(RecordedModel.class)
@Isolated
class AuditRoutesIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String AUDIT = "/api/v1/audit";
    private static final String EXPORT = "/api/v1/audit/export";

    @Autowired
    private RecordedGateway model;

    @Autowired
    private JdbcClient jdbc;

    private String session;
    private String seeded;
    private UUID seededVersion;
    private OpenApiContract contract;

    @BeforeEach
    void signInAndWaitForTheSeededCorpus() {
        model.reset();
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                Seeded.LENDING_RULESET_ID);
        seeded = ids.getFirst();
        seededVersion = jdbc.sql("select id from ruleset_version where ruleset_id = :id and version_no = 1")
                .param("id", UUID.fromString(seeded)).query(UUID.class).single();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!"READY".equals(jdbc.sql("select embedding_status from ruleset_version where id = :id")
                .param("id", seededVersion).query(String.class).single())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the seeded version never became READY");
            }
            Thread.onSpinWait();
        }
    }

    // Document 2: "Audit entries, newest first". The seeded version is shared by every test, and the entries that
    // name no change request are everyone's to read. Its entries carry whatever clock wrote them: the seed's
    // publish the clock of whichever context started first on this database, the test clock or the real one, and
    // AuditLogIT's appends the test clock. So the proposal is made a minute after the latest of them (a fixed time
    // failed once the real clock passed it: day 15), in a session opened at that minute: a session lives 24 hours
    // (SessionCookies.MAX_AGE), and the one opened at START had expired once the real clock was a day past START
    // (day 16). Expected: this sandbox's proposal first, the seeded publish by demo-analyst among them, and every
    // entry no newer than the one before it
    @Test
    void theEntriesOfAVersionAreNewestFirst() {
        Instant latest = jdbc.sql("select max(at) from audit_entry where ruleset_version_id = :v")
                .param("v", seededVersion).query(Instant.class).single();
        clock.set(latest.plusSeconds(60));
        session = api().login();
        String id = propose(session);

        HttpResponse<String> response = api().get(AUDIT + "?versionId=" + seededVersion).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", AUDIT, 200, response.body())).isEmpty();
        List<JsonNode> entries = JSON.readTree(response.body()).required("entries").valueStream().toList();
        assertThat(entries.getFirst().required("action").asString()).isEqualTo("CHANGE_PROPOSED");
        assertThat(entries.getFirst().required("changeRequestId").asString()).isEqualTo(id);
        assertThat(entries.getFirst().required("actor").asString()).isEqualTo(sandboxOf(session));
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.required("action").asString()).isEqualTo("PUBLISH");
            assertThat(entry.required("actor").asString()).isEqualTo("demo-analyst");
        });
        assertThat(entries.stream().map(entry -> Instant.parse(entry.required("at").asString())).toList())
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    // Document 5: an entry about another sandbox's change request is never shown, on a protected version either.
    // Expected: each sandbox sees its own proposal on the seeded version and not the other's
    @Test
    void anotherSandboxsChangeRequestIsNotShown() {
        String stranger = api().login("198.51.100.41");
        String mine = propose(session);
        String theirs = propose(stranger);

        List<String> seenByMe = requestIds(session);
        List<String> seenByThem = requestIds(stranger);

        assertThat(seenByMe).contains(mine).doesNotContain(theirs);
        assertThat(seenByThem).contains(theirs).doesNotContain(mine);
    }

    // Document 5, no existence oracle. Expected: 404 for an id no version has and for a version of the copy another
    // sandbox made by approving a change, and 400 without a versionId
    @Test
    void aVersionTheSandboxCannotSeeIsNotFound() {
        String stranger = api().login("198.51.100.42");
        String copied = JSON.readTree(api().post("/api/v1/changes/" + propose(stranger) + "/approve").web()
                .cookie(stranger).send().body()).required("result").required("versionId").asString();

        HttpResponse<String> unknown = api().get(AUDIT + "?versionId=" + UUID.randomUUID()).cookie(session).send();
        HttpResponse<String> theirs = api().get(AUDIT + "?versionId=" + copied).cookie(session).send();
        HttpResponse<String> none = api().get(AUDIT).cookie(session).send();

        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(contract.violations("get", AUDIT, 404, unknown.body())).isEmpty();
        assertThat(theirs.statusCode()).isEqualTo(404);
        assertThat(none.statusCode()).isEqualTo(400);
        assertThat(contract.violations("get", AUDIT, 400, none.body())).isEmpty();
    }

    // Document 2: the audit log as CSV, formula-prefixed, with a byte order mark, as an attachment. Expected: the
    // header and one row per entry GET /audit gives this sandbox, under a name made of the version's id
    @Test
    void theExportOfAVersionIsCsvWithOneRowPerEntry() {
        propose(session);
        int entries = JSON.readTree(api().get(AUDIT + "?versionId=" + seededVersion).cookie(session).send().body())
                .required("entries").size();

        HttpResponse<String> response = api().get(EXPORT + "?versionId=" + seededVersion).cookie(session)
                .header("Accept", "text/csv").send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/csv"));
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"audit-" + seededVersion + ".csv\"");
        assertThat(response.body()).startsWith(Csv.BYTE_ORDER_MARK
                + "id,at,actor,action,ruleset_version_id,change_request_id,details\r\n");
        assertThat(response.body().split("\r\n")).hasSize(entries + 1);
    }

    // Document 2: without a versionId, every entry the sandbox can see. Expected: after an approval, the entries
    // GET /audit gives for the seeded versions (the lending one and the second domain's), and on the sandbox's copy
    // its publish and the approval
    @Test
    void theExportWithoutAVersionHoldsEveryVersionTheSandboxCanSee() {
        String id = propose(session);
        JsonNode result = JSON.readTree(api().post("/api/v1/changes/" + id + "/approve").web().cookie(session).send()
                .body()).required("result");
        List<String> seededEntries = new ArrayList<>();
        for (UUID version : jdbc.sql("""
                select v.id from ruleset_version v join ruleset r on r.id = v.ruleset_id where r.protected""")
                .query(UUID.class).list()) {
            seededEntries.addAll(ids(api().get(AUDIT + "?versionId=" + version).cookie(session).send()));
        }

        HttpResponse<String> response = api().get(EXPORT).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", EXPORT, 200, response.body())).isEmpty();
        List<JsonNode> entries = JSON.readTree(response.body()).required("entries").valueStream().toList();
        assertThat(entries.stream().map(entry -> entry.required("id").asString())).containsAll(seededEntries);
        assertThat(entries.stream().filter(entry -> !seededEntries.contains(entry.required("id").asString()))
                .map(entry -> entry.required("action").asString()))
                .containsExactlyInAnyOrder("PUBLISH", "CHANGE_APPROVED");
        assertThat(entries.stream().filter(entry -> entry.required("action").asString().equals("CHANGE_APPROVED"))
                .map(entry -> entry.required("rulesetVersionId").asString()))
                .containsExactly(result.required("versionId").asString());
    }

    /** The scripted request on the seeded version, answered with the fixture's patches; the stored request's id. */
    private String propose(String cookie) {
        model.willAnswer(ChangeRequests.scriptedPatches().toString());
        HttpResponse<String> stream = api().post("/api/v1/rulesets/" + seeded + "/versions/1/changes").web()
                .cookie(cookie).json(JSON.createObjectNode().put("text", ChangeRequests.scripted()).toString())
                .send();
        return ServerSentEvents.parse(stream.body()).first("proposal").required("id").asString();
    }

    private static List<String> ids(HttpResponse<String> audit) {
        return JSON.readTree(audit.body()).required("entries").valueStream()
                .map(entry -> entry.required("id").asString()).toList();
    }

    /** The change request ids of the seeded version's entries, as a sandbox reads them. */
    private List<String> requestIds(String cookie) {
        return JSON.readTree(api().get(AUDIT + "?versionId=" + seededVersion).cookie(cookie).send().body())
                .required("entries").valueStream().map(entry -> entry.path("changeRequestId"))
                .filter(id -> !id.isNull()).map(JsonNode::asString).toList();
    }

    /** The cookie is {@code sandboxId.issuedAt.signature}: the sandbox is the actor Document 5 records. */
    private static String sandboxOf(String cookie) {
        return cookie.substring(0, cookie.indexOf('.'));
    }
}
