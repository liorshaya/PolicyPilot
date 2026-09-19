package com.liorshaya.policypilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.JsonSubset;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * One case decided through the API and stored (Brief FR-8, FR-10; Document 2, Flow 2 and the decision table;
 * Document 3, steps 1 and 7). Expected outcomes come from the committed fixtures, which the Python reference
 * produced.
 */
@Requirement({"FR-8", "FR-10"})
class DecisionServiceIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcClient jdbc;

    private Decisions decisions;
    private String session;

    @BeforeEach
    void logIn() {
        session = api().login();
        decisions = new Decisions(api(), session);
    }

    // FR-10 first proof (Document 6 matrix). Expected: the decision columns of Document 2 and the test clock
    @Test
    void decideOneCaseStoresVersionInputSnapshotTraceAndTimestamp() {
        HttpResponse<String> response = decisions.decide(single(Decisions.DEMO_CASE));

        assertThat(response.statusCode()).isEqualTo(200);
        UUID id = UUID.fromString(JsonPath.read(response.body(), "$.id"));
        Map<String, Object> row = jdbc.sql("""
                select status, outcome, deciding_rule_id, decided_at, input_json ->> 'monthly_income' as income,
                       ruleset_version_id, trace_json -> 'trace' -> 0 ->> 'ruleId' as first_step
                from decision where id = :id
                """).param("id", id).query().singleRow();
        assertThat(row.get("status")).isEqualTo("OK");
        assertThat(row.get("outcome")).isEqualTo(Decisions.expected(Decisions.DEMO_CASE).required("outcome").asString());
        assertThat(row.get("deciding_rule_id"))
                .isEqualTo(Decisions.expected(Decisions.DEMO_CASE).required("decidingRuleId").asString());
        assertThat(((java.sql.Timestamp) row.get("decided_at")).toInstant()).isEqualTo(START);
        assertThat(row.get("income"))
                .isEqualTo(Decisions.input(Decisions.DEMO_CASE).required("monthly_income").asString());
        assertThat(row.get("first_step")).isEqualTo(Fixtures.lendingV1().withArray("rules").get(0).required("id").asString());
    }

    // FR-8. Expected: fixtures/policies/consumer-lending/sample-decision.json
    @Test
    void theStoredDecisionOfCase17EqualsTheReferencesSampleDecision() {
        String body = decisions.decide(single(Decisions.DEMO_CASE)).body();

        JsonNode expected = Fixtures.json("policies/consumer-lending/sample-decision.json");
        assertThat(JsonSubset.mismatch(expected, JSON.readTree(body))).isNull();
    }

    // FR-10: "replay equals stored". Expected: the stored decision, deciding again on the stored input
    @Test
    void replayOfAStoredDecisionEqualsTheStoredDecision() {
        String first = decisions.decide(single(Decisions.DEMO_CASE)).body();
        String storedInput = jdbc.sql("select input_json from decision where id = :id")
                .param("id", UUID.fromString(JsonPath.read(first, "$.id"))).query(String.class).single();

        String replay = decisions.decide("{\"case\":" + storedInput + "}").body();

        assertThat(enginePart(replay)).isEqualTo(enginePart(first));
    }

    // Document 3, step 1: a case error is not a decision and nothing is stored. Expected: C-30's CASE_OUT_OF_RANGE
    @Test
    void aCaseErrorIs422AndStoresNothing() {
        int before = storedDecisions();
        ObjectNode outOfRange = Decisions.input(Decisions.DEMO_CASE).deepCopy().put("term_months", 0);

        HttpResponse<String> response = decisions.decide("{\"case\":" + outOfRange + "}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("CASE_INVALID");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/case/term_months");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].problem"))
                .isEqualTo(problemOf("conformance/C-30.json"));
        assertThat(storedDecisions()).isEqualTo(before);
    }

    // Document 5, Error responses: a message never repeats the request. Expected: the value is nowhere in the body
    @Test
    void aCaseErrorNamesTheFieldButNeverItsValue() {
        ObjectNode outOfRange = Decisions.input(Decisions.DEMO_CASE).deepCopy().put("existing_monthly_debt", -5000);

        HttpResponse<String> response = decisions.decide("{\"case\":" + outOfRange + "}");

        assertThat(response.body()).doesNotContain("-5000");
    }

    // Brief FR-7: only published versions decide cases. Expected: 409 VERSION_STATUS_CONFLICT, nothing stored
    @Test
    void decidingOnADraftIsRefused() {
        int before = storedDecisions();
        String draft = draftOfItsOwn();

        HttpResponse<String> response = decisions.decide(draft, single(Decisions.DEMO_CASE));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("VERSION_STATUS_CONFLICT");
        assertThat(storedDecisions()).isEqualTo(before);
    }

    // Document 2, GET /decisions/{id}. Expected: the stored decision with its trace
    @Test
    void getDecisionReturnsTheStoredDecisionWithItsTrace() {
        String stored = decisions.decide(single(Decisions.DEMO_CASE)).body();
        String id = JsonPath.read(stored, "$.id");

        HttpResponse<String> response = decisions.decision(id);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree(stored));
        assertThat((List<?>) JsonPath.read(response.body(), "$.trace"))
                .hasSize(Fixtures.lendingV1().withArray("rules").size());
    }

    // Document 2: the API adds the version, the timing and the stored id to the engine's decision
    @Test
    void theApiAddsTheVersionTheTimingAndTheStoredId() {
        String body = decisions.decide(single(Decisions.DEMO_CASE)).body();

        assertThat((String) JsonPath.read(body, "$.rulesetVersion.id"))
                .isEqualTo(Fixtures.lendingV1().get("id").stringValue());
        assertThat((Integer) JsonPath.read(body, "$.rulesetVersion.versionNo")).isEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.decidedAt")).isEqualTo(START.toString());
        assertThat((Integer) JsonPath.read(body, "$.durationMicros")).isNotNegative();
    }

    /** A draft rule set of this session: its own copy of the seeded one, made by editing the protected version. */
    private String draftOfItsOwn() {
        String body = api().method("PUT", decisions.versionPath() + "/rules").web().cookie(session)
                .json(Fixtures.lendingV1().toString()).send().body();
        return Decisions.RULESETS + "/" + JsonPath.read(body, "$.rulesetId") + "/versions/1";
    }

    /** How many decisions this session's sandbox has; test classes run in parallel, so the count is scoped. */
    private int storedDecisions() {
        return jdbc.sql("select count(*) from decision where sandbox_id = :sandbox")
                .param("sandbox", UUID.fromString(session.substring(0, session.indexOf('.'))))
                .query(Integer.class).single();
    }

    /** The engine's part of a response: everything the API added afterwards is dropped. */
    private static JsonNode enginePart(String body) {
        ObjectNode decision = (ObjectNode) JSON.readTree(body);
        decision.remove(List.of("id", "caseNo", "rulesetVersion", "decidedAt", "durationMicros"));
        return decision;
    }

    /** The problem code a conformance fixture expects for its case error. */
    private static String problemOf(String fixture) {
        return Fixtures.json(fixture).required("checks").get(0).required("expected").required("problems").get(0)
                .required("code").stringValue();
    }

    private static String single(int caseNo) {
        return "{\"case\":" + Decisions.input(caseNo) + "}";
    }
}
