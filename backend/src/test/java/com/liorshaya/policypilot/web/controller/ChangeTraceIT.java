package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
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
import java.util.HashMap;
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
 * What a proposal decides for one of the sandbox's decisions on its base version (Document 2,
 * {@code GET /changes/{id}/decisions/{decisionId}/trace}, added 2026-09-28 for Register phase 4; Document 3,
 * Regression report): the copy rebuilt from the request's stored patches, evaluated on the stored decision's input,
 * with its trace, and nothing stored. The proposal is the scripted request answered with change-request-1.json's
 * patches, over the fixture set this sandbox decided on the seeded lending version; the expectations are the Python
 * reference's.
 */
@Requirement("FR-18")
@Import(RecordedModel.class)
@Isolated
class ChangeTraceIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TRACE = "/api/v1/changes/{id}/decisions/{decisionId}/trace";

    @Autowired
    private RecordedGateway model;

    @Autowired
    private JdbcClient jdbc;

    private String session;
    private String seeded;
    private OpenApiContract contract;
    /** This sandbox's decision of each case of cases-200 on the seeded version, by case number. */
    private Map<Integer, String> decided;

    @BeforeEach
    void decideTheFixtureSetOnTheSeededVersion() {
        model.reset();
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                Seeded.LENDING_RULESET_ID);
        seeded = ids.getFirst();
        awaitSeededVersionReady();
        decided = decide(seeded, 1, session);
    }

    // Document 3: case 8 flips (change-request-1.json's flips). Expected: the Python reference's decision of case 8 on
    // the copy, rejected by R-170, whose step matched with the pending provenance of the request, every rule after it
    // skipped and no flag left; based on the stored decision, carrying the request's number, as the flip reported
    @Test
    void aFlippedCaseIsDecidedByTheCopyAsTheRegressionReported() {
        JsonNode proposal = propose();
        String id = proposal.required("id").asString();

        HttpResponse<String> response = trace(id, decided.get(8), session);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", TRACE, 200, response.body())).isEmpty();
        JsonNode decision = JSON.readTree(response.body());
        assertThat(decision.required("outcome").asString()).isEqualTo("reject");
        assertThat(decision.required("decidingRuleId").asString()).isEqualTo("R-170");
        assertThat(decision.required("flags")).isEmpty();
        JsonNode r170 = step(decision, "R-170");
        assertThat(r170.required("status").asString()).isEqualTo("fired");
        assertThat(r170.required("provenance").required("kind").asString()).isEqualTo("pending");
        assertThat(r170.required("provenance").required("changeRequestId").asString()).isEqualTo(id);
        assertThat(step(decision, "R-420").required("status").asString()).isEqualTo("skipped");
        assertThat(decision.required("basedOnDecisionId").asString()).isEqualTo(decided.get(8));
        assertThat(decision.required("changeRequestNumber").asInt()).isEqualTo(1);
        JsonNode flip = proposal.required("regression").required("flips").valueStream()
                .filter(each -> each.required("decisionId").asString().equals(decided.get(8))).findFirst()
                .orElseThrow();
        assertThat(flip.required("after").asString()).isEqualTo("reject");
    }

    // Expected: a case the change does not flip is decided the same, case 17 referred by R-330 as the Python reference
    // decides it on the copy, and asking stores nothing
    @Test
    void anUnflippedCaseIsDecidedTheSameAndNothingIsStored() {
        String id = propose().required("id").asString();
        long decisions = decisionCount();

        JsonNode decision = JSON.readTree(trace(id, decided.get(17), session).body());

        assertThat(decision.required("outcome").asString()).isEqualTo("refer");
        assertThat(decision.required("decidingRuleId").asString()).isEqualTo("R-330");
        assertThat(decisionCount()).isEqualTo(decisions);
    }

    // Document 2: the request's stored patches, whatever became of it. Expected: after the approval, a decision on the
    // base version is still answered, and one this sandbox then made on version 2 of its copy is 404
    @Test
    void anApprovedRequestStillAnswersForItsBaseVersionOnly() {
        String id = propose().required("id").asString();
        HttpResponse<String> approved = api().post("/api/v1/changes/" + id + "/approve").web().cookie(session).send();
        String copy = JSON.readTree(approved.body()).required("result").required("rulesetId").asString();
        Map<Integer, String> onTheCopy = decide(copy, 2, session);

        HttpResponse<String> base = trace(id, decided.get(8), session);
        HttpResponse<String> other = trace(id, onTheCopy.get(8), session);

        assertThat(base.statusCode()).isEqualTo(200);
        assertThat(other.statusCode()).isEqualTo(404);
        assertThat(contract.violations("get", TRACE, 404, other.body())).isEmpty();
    }

    // Document 5, no existence oracle. Expected: 404 for this sandbox's request asked by another sandbox, for another
    // sandbox's decision, and for a decision that does not exist
    @Test
    void anotherSandboxsRequestOrDecisionIsNotFound() {
        String id = propose().required("id").asString();
        String stranger = api().login();
        Map<Integer, String> theirs = decide(seeded, 1, stranger);

        HttpResponse<String> theirAsk = trace(id, decided.get(8), stranger);
        HttpResponse<String> theirDecision = trace(id, theirs.get(8), session);
        HttpResponse<String> unknown = trace(id, UUID.randomUUID().toString(), session);

        assertThat(theirAsk.statusCode()).isEqualTo(404);
        assertThat(theirDecision.statusCode()).isEqualTo(404);
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(contract.violations("get", TRACE, 404, theirAsk.body())).isEmpty();
    }

    /** The scripted request on the seeded version, answered with the fixture's patches; the proposal event. */
    private JsonNode propose() {
        model.willAnswer(ChangeRequests.scriptedPatches().toString());
        HttpResponse<String> stream = api().post("/api/v1/rulesets/" + seeded + "/versions/1/changes").web()
                .cookie(session).json(JSON.createObjectNode().put("text", ChangeRequests.scripted()).toString())
                .send();
        assertThat(stream.statusCode()).isEqualTo(200);
        return ServerSentEvents.parse(stream.body()).first("proposal");
    }

    private HttpResponse<String> trace(String changeRequestId, String decisionId, String cookie) {
        return api().get(TRACE.replace("{id}", changeRequestId).replace("{decisionId}", decisionId)).cookie(cookie)
                .send();
    }

    /** The fixture set decided on a version, each case's decision id by its number. */
    private Map<Integer, String> decide(String rulesetId, int versionNo, String cookie) {
        HttpResponse<String> response = api().post("/api/v1/rulesets/" + rulesetId + "/versions/" + versionNo
                + "/decide").web().cookie(cookie).json("{\"fixtureSet\":\"cases-200\"}").send();
        assertThat(response.statusCode()).isEqualTo(200);
        Map<Integer, String> byCase = new HashMap<>();
        JSON.readTree(response.body()).required("results").forEach(result -> byCase.put(
                result.required("caseNo").asInt(), result.required("id").asString()));
        return byCase;
    }

    private long decisionCount() {
        return jdbc.sql("select count(*) from decision where sandbox_id = :sandbox")
                .param("sandbox", UUID.fromString(session.substring(0, session.indexOf('.')))).query(Long.class)
                .single();
    }

    private static JsonNode step(JsonNode decision, String ruleId) {
        return decision.required("trace").valueStream()
                .filter(step -> step.required("ruleId").asString().equals(ruleId)).findFirst().orElseThrow();
    }

    private void awaitSeededVersionReady() {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!"READY".equals(jdbc.sql("""
                select v.embedding_status from ruleset_version v join ruleset r on r.id = v.ruleset_id
                where r.protected and r.domain = :domain and v.version_no = 1""").param("domain", Seeded.LENDING)
                .query(String.class).single())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the seeded version never became READY");
            }
            Thread.onSpinWait();
        }
    }
}
