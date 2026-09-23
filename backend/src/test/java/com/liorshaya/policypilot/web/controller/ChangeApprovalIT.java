package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.ServerSentEvents;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * A person's decision on a stored proposal (Document 2, {@code POST /changes/{id}/approve} and {@code .../reject};
 * Document 3, Provenance and Version lineage; Work Plan day 13). The proposal is the scripted request on the seeded
 * lending version, answered with change-request-1.json's patches; the approval, the fork, the audit entry and the
 * decisions are the real ones. Approving on the protected version publishes version 2 into the sandbox's own copy of
 * the rule set and leaves version 1 as it was (the owner's decision of 2026-09-24, Document 2).
 */
@Requirement({"FR-19", "FR-20"})
@Import(RecordedModel.class)
@Isolated
class ChangeApprovalIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RuleSetMapper DSL = new RuleSetMapper();
    private static final String APPROVE = "/api/v1/changes/{id}/approve";
    private static final String REJECT = "/api/v1/changes/{id}/reject";
    private static final String NOTE = "אושר בוועדת האשראי";

    @Autowired
    private RecordedGateway model;

    @Autowired
    private JdbcClient jdbc;

    private String session;
    private String seeded;
    private OpenApiContract contract;

    @BeforeEach
    void signInAndWaitForTheSeededCorpus() {
        model.reset();
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                "$.rulesets[?(@.protected == true)].id");
        seeded = ids.getFirst();
        awaitReady("select v.embedding_status from ruleset_version v join ruleset r on r.id = v.ruleset_id "
                + "where r.protected and v.version_no = 1", Map.of());
    }

    // Document 2, approve on a protected base: version 1 of the sandbox's copy is the base as published, version 2
    // the change, and the protected version never changes. Expected: all three as the fixtures have them, the copy
    // forked from the seeded rule set, and the request APPROVED with version 2 as its result
    @Test
    void approvingOnTheSeededVersionPublishesVersionTwoInTheSandboxsCopy() {
        String id = propose(seeded, 1);

        HttpResponse<String> approved = decide(APPROVE, id, NOTE);

        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", APPROVE, 200, approved.body())).isEmpty();
        JsonNode decision = JSON.readTree(approved.body());
        assertThat(decision.required("status").asString()).isEqualTo("APPROVED");
        String copy = decision.required("result").required("rulesetId").asString();
        assertThat(copy).isNotEqualTo(seeded);
        assertThat(decision.required("result").required("versionNo").asInt()).isEqualTo(2);
        List<String> forkedFrom = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                "$.rulesets[?(@.id == '" + copy + "')].forkedFromId");
        assertThat(forkedFrom).containsExactly(seeded);
        assertThat(version(copy, 1).required("ruleSet")).isEqualTo(Fixtures.lendingV1());
        assertThat(version(copy, 1).required("status").asString()).isEqualTo("PUBLISHED");
        assertThat(version(copy, 2).required("status").asString()).isEqualTo("PUBLISHED");
        assertThat(version(seeded, 1).required("ruleSet")).isEqualTo(Fixtures.lendingV1());
        assertThat(jdbc.sql("select status from change_request where id = :id").param("id", UUID.fromString(id))
                .query(String.class).single()).isEqualTo("APPROVED");
        assertThat(jdbc.sql("select result_version_id from change_request where id = :id")
                .param("id", UUID.fromString(id)).query(UUID.class).single())
                .isEqualTo(UUID.fromString(decision.required("result").required("versionId").asString()));
    }

    // Document 3: every pending becomes analyst, the approver as actor, "Change request <id>: <request text>" and the
    // rationale as note, the id kept. Expected: R-170 and R-410 of version 2 with the fixture's conditions so
    @Test
    void versionTwoCarriesTheApproversProvenance() {
        String id = propose(seeded, 1);

        String copy = JSON.readTree(decide(APPROVE, id, NOTE).body()).required("result").required("rulesetId")
                .asString();

        JsonNode expected = Fixtures.json("policies/consumer-lending/change-request-1.json").required("expected")
                .required("patches");
        for (JsonNode patch : expected) {
            JsonNode rule = rule(version(copy, 2).required("ruleSet"), patch.required("ruleId").asString());
            JsonNode provenance = rule.required("provenance");
            assertThat(rule.required("condition")).isEqualTo(patch.required("rule").required("condition"));
            assertThat(provenance.required("kind").asString()).isEqualTo("analyst");
            assertThat(provenance.required("actor").asString()).isEqualTo(sandboxOf(session));
            assertThat(provenance.required("changeRequestId").asString()).isEqualTo(id);
            assertThat(provenance.required("note").asString())
                    .startsWith("Change request " + id + ": " + ChangeRequests.scripted() + "\n");
        }
    }

    // Document 2: one CHANGE_APPROVED entry on the new version with the actor, the request text, the note, the diff
    // and the regression report; Document 3: the diff is stored and never recomputed. Expected: the entry so, its
    // twelve flips (change-request-1.json), and its diff the one the diff route gives for versions 1 and 2
    @Test
    void theApprovalIsOneAuditEntryWithTheRequestTheNoteTheDiffAndTheReport() {
        decideTheFixtureSet();
        String id = propose(seeded, 1);

        JsonNode result = JSON.readTree(decide(APPROVE, id, NOTE).body()).required("result");

        var entry = jdbc.sql("""
                select actor, ruleset_version_id, details_json::text from audit_entry
                where change_request_id = :id and action = 'CHANGE_APPROVED'""")
                .param("id", UUID.fromString(id)).query().singleRow();
        assertThat(entry.get("actor")).isEqualTo(sandboxOf(session));
        assertThat(entry.get("ruleset_version_id")).isEqualTo(UUID.fromString(result.required("versionId")
                .asString()));
        JsonNode details = JSON.readTree((String) entry.get("details_json"));
        assertThat(details.required("requestText").asString()).isEqualTo(ChangeRequests.scripted());
        assertThat(details.required("note").asString()).isEqualTo(NOTE);
        assertThat(details.required("regression").required("flips")).hasSize(
                Fixtures.json("policies/consumer-lending/change-request-1.json").required("expected")
                        .required("regression").required("flips").asInt());
        String copy = result.required("rulesetId").asString();
        HttpResponse<String> diff = api().get("/api/v1/rulesets/" + copy + "/versions/1/diff/2").cookie(session)
                .send();
        assertThat(details.required("diff")).isEqualTo(JSON.readTree(diff.body()));
        assertThat(details.required("diff").required("rules").required("modified").valueStream()
                .map(rule -> rule.required("id").asString())).containsExactly("R-170", "R-410");
    }

    // Work Plan day 13, Done when: "the version 1 decision for case 17 is unchanged". Expected: the sandbox's
    // decisions on the seeded version, case 17 referred (Document 3's worked example), the same after the approval
    @Test
    void theDecisionsOfVersionOneAreUnchanged() {
        decideTheFixtureSet();
        String before = decisionsOnTheSeededVersion();
        String id = propose(seeded, 1);

        decide(APPROVE, id, NOTE);

        assertThat(decisionsOnTheSeededVersion()).isEqualTo(before);
        assertThat(jdbc.sql("""
                select d.outcome from decision d join case_fixture c on c.id = d.case_id
                where d.sandbox_id = :sandbox and c.case_no = 17""")
                .param("sandbox", UUID.fromString(sandboxOf(session))).query(String.class).single())
                .isEqualTo("refer");
    }

    // Document 2: 409 unless the request is PROPOSED. Expected: a second approval and a rejection after it refused
    @Test
    void aRequestIsDecidedOnce() {
        String id = propose(seeded, 1);
        decide(APPROVE, id, null);

        HttpResponse<String> again = decide(APPROVE, id, null);
        HttpResponse<String> rejected = decide(REJECT, id, null);

        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", APPROVE, 409, again.body())).isEmpty();
        assertThat(rejected.statusCode()).isEqualTo(409);
    }

    // Document 2: 409 when the sandbox already has a copy of the protected rule set; the next change is proposed on
    // the copy. Expected: a second change on the seeded version refused at its approval
    @Test
    void aSecondChangeOnTheProtectedVersionIsRefusedOnceTheSandboxHasItsCopy() {
        decide(APPROVE, propose(seeded, 1), null);
        String second = propose(seeded, 1);

        assertThat(decide(APPROVE, second, null).statusCode()).isEqualTo(409);
        assertThat(jdbc.sql("select status from change_request where id = :id").param("id", UUID.fromString(second))
                .query(String.class).single()).isEqualTo("PROPOSED");
    }

    // Document 2: 409 when the base is no longer the latest published version of its rule set. Expected: two changes
    // proposed on version 2 of the copy, the first approved as version 3, the second refused
    @Test
    void aChangeOnAVersionThatIsNoLongerTheLatestIsRefused() {
        String copy = JSON.readTree(decide(APPROVE, propose(seeded, 1), null).body()).required("result")
                .required("rulesetId").asString();
        awaitReady("select embedding_status from ruleset_version where ruleset_id = :id and version_no = 2",
                Map.of("id", UUID.fromString(copy)));
        String first = propose(copy, 2);
        String second = propose(copy, 2);

        HttpResponse<String> approved = decide(APPROVE, first, null);
        HttpResponse<String> stale = decide(APPROVE, second, null);

        assertThat(JSON.readTree(approved.body()).required("result").required("versionNo").asInt()).isEqualTo(3);
        assertThat(JSON.readTree(approved.body()).required("result").required("rulesetId").asString())
                .isEqualTo(copy);
        assertThat(stale.statusCode()).isEqualTo(409);
    }

    // Work Plan day 13: "reject publishes nothing". Expected: REJECTED with no result, no copy of the rule set, and a
    // CHANGE_REJECTED entry with the note on the base version
    @Test
    void rejectingPublishesNothing() {
        String id = propose(seeded, 1);

        HttpResponse<String> rejected = decide(REJECT, id, NOTE);

        assertThat(rejected.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", REJECT, 200, rejected.body())).isEmpty();
        assertThat(JSON.readTree(rejected.body()).required("status").asString()).isEqualTo("REJECTED");
        assertThat(JSON.readTree(rejected.body()).has("result")).isFalse();
        assertThat(jdbc.sql("select count(*) from ruleset where sandbox_id = :sandbox")
                .param("sandbox", UUID.fromString(sandboxOf(session))).query(Long.class).single()).isZero();
        var entry = jdbc.sql("""
                select ruleset_version_id, details_json::text from audit_entry
                where change_request_id = :id and action = 'CHANGE_REJECTED'""")
                .param("id", UUID.fromString(id)).query().singleRow();
        assertThat(entry.get("ruleset_version_id")).isEqualTo(seededVersionId());
        assertThat(JSON.readTree((String) entry.get("details_json")).required("note").asString()).isEqualTo(NOTE);
    }

    // Document 5, no existence oracle. Expected: another sandbox's approval and rejection are 404 and change nothing
    @Test
    void anotherSandboxsRequestIsNotFound() {
        String id = propose(seeded, 1);
        String stranger = api().login("198.51.100.31");

        HttpResponse<String> approved = api().post(APPROVE.replace("{id}", id)).web().cookie(stranger).send();
        HttpResponse<String> rejected = api().post(REJECT.replace("{id}", id)).web().cookie(stranger).send();

        assertThat(approved.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", APPROVE, 404, approved.body())).isEmpty();
        assertThat(rejected.statusCode()).isEqualTo(404);
        assertThat(jdbc.sql("select status from change_request where id = :id").param("id", UUID.fromString(id))
                .query(String.class).single()).isEqualTo("PROPOSED");
    }

    // Document 5, Input limits: the note is held like a chat message. Expected: a control character is 400 at /note
    @Test
    void aNoteWithAControlCharacterIsRefused() {
        String id = propose(seeded, 1);

        HttpResponse<String> approved = decide(APPROVE, id, "אושר" + Character.toString(7));

        assertThat(approved.statusCode()).isEqualTo(400);
        assertThat(contract.violations("post", APPROVE, 400, approved.body())).isEmpty();
        assertThat((String) JsonPath.read(approved.body(), "$.details[0].path")).isEqualTo("/note");
    }

    /** The scripted request on a version, answered with the fixture's patches; the stored request's id. */
    private String propose(String rulesetId, int versionNo) {
        model.willAnswer(ChangeRequests.scriptedPatches().toString());
        HttpResponse<String> stream = api().post("/api/v1/rulesets/" + rulesetId + "/versions/" + versionNo
                + "/changes").web().cookie(session).json(JSON.createObjectNode()
                .put("text", ChangeRequests.scripted()).toString()).send();
        assertThat(stream.statusCode()).isEqualTo(200);
        return ServerSentEvents.parse(stream.body()).first("proposal").required("id").asString();
    }

    private HttpResponse<String> decide(String route, String id, String note) {
        var call = api().post(route.replace("{id}", id)).web().cookie(session);
        return (note == null ? call : call.json(JSON.createObjectNode().put("note", note).toString())).send();
    }

    /** A version as the API answers it, read like the fixtures, whose decimals are exact. */
    private JsonNode version(String rulesetId, int versionNo) {
        return DSL.readTree(api().get("/api/v1/rulesets/" + rulesetId + "/versions/" + versionNo).cookie(session)
                .send().body());
    }

    private void decideTheFixtureSet() {
        assertThat(api().post("/api/v1/rulesets/" + seeded + "/versions/1/decide").web().cookie(session)
                .json("{\"fixtureSet\":\"cases-200\"}").send().statusCode()).isEqualTo(200);
    }

    /** Every decision of this sandbox on the seeded version, as one text to compare before and after. */
    private String decisionsOnTheSeededVersion() {
        return jdbc.sql("""
                select string_agg(id || ':' || outcome, ',' order by id) from decision
                where sandbox_id = :sandbox and ruleset_version_id = :version""")
                .param("sandbox", UUID.fromString(sandboxOf(session))).param("version", seededVersionId())
                .query(String.class).single();
    }

    private UUID seededVersionId() {
        return jdbc.sql("select id from ruleset_version where ruleset_id = :id and version_no = 1")
                .param("id", UUID.fromString(seeded)).query(UUID.class).single();
    }

    private static JsonNode rule(JsonNode document, String id) {
        return document.required("rules").valueStream().filter(rule -> rule.required("id").asString().equals(id))
                .findFirst().orElseThrow();
    }

    /** The cookie is {@code sandboxId.issuedAt.signature}: the sandbox is the actor Document 5 records. */
    private static String sandboxOf(String cookie) {
        return cookie.substring(0, cookie.indexOf('.'));
    }

    private void awaitReady(String sql, Map<String, Object> params) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (true) {
            var query = jdbc.sql(sql);
            for (var param : params.entrySet()) {
                query = query.param(param.getKey(), param.getValue());
            }
            if ("READY".equals(query.query(String.class).single())) {
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the version never became READY");
            }
            Thread.onSpinWait();
        }
    }
}
