package com.liorshaya.policypilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Batches: a list of cases or {@code fixtureSet: "cases-200"} (Brief FR-9; Document 2, decide; Document 5, Batch
 * decide: 500 cases per request and 5 requests per minute per sandbox). Every expected number comes from
 * {@code cases-expected.json}, which the Python reference produced.
 */
@Requirement("FR-9")
class BatchDecisionIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FIXTURE_SET = "{\"fixtureSet\":\"cases-200\"}";

    @Autowired
    private JdbcClient jdbc;

    private Decisions decisions;
    private String session;

    @BeforeEach
    void logIn() {
        session = api().login();
        decisions = new Decisions(api(), session);
    }

    // FR-9 first proof (Document 6 matrix). Expected: the summary of cases-expected.json
    @Test
    void fixtureSetCases200AggregatesEqualTheExpectedSummary() {
        HttpResponse<String> response = decisions.decide(FIXTURE_SET);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode summary = Decisions.expected().required("summary");
        JsonNode aggregates = JSON.readTree(response.body()).required("aggregates");
        assertThat(aggregates.required("outcomes")).isEqualTo(summary.required("outcomes"));
        assertThat(aggregates.required("topDecidingRules")).isEqualTo(summary.required("topDecidingRules"));
        assertThat(aggregates.required("errors").asInt()).isZero();
        assertThat(aggregates.required("decisions").asInt()).isEqualTo(Decisions.cases().size());
    }

    // FR-9. Expected: the outcome, deciding rule and flags of every case in cases-expected.json
    @Test
    void everyCaseMatchesItsExpectedOutcomeDecidingRuleAndFlags() {
        String body = decisions.decide(FIXTURE_SET).body();

        Map<Integer, String> decided = JSON.readTree(body).required("results").valueStream()
                .collect(Collectors.toMap(result -> result.required("caseNo").asInt(), BatchDecisionIT::summary));
        Map<Integer, String> expected = Decisions.expected().required("cases").valueStream()
                .collect(Collectors.toMap(line -> line.required("id").asInt(), BatchDecisionIT::expectedSummary));
        assertThat(decided).isEqualTo(expected);
    }

    // FR-10: every decision of the batch is stored and names its case. Expected: 200 rows with their case numbers
    @Test
    void theBatchStoresTwoHundredDecisionsLinkedToTheSeededCases() {
        decisions.decide(FIXTURE_SET);

        List<Integer> numbers = jdbc.sql("""
                select c.case_no from decision d join case_fixture c on c.id = d.case_id
                where d.sandbox_id = :sandbox order by c.case_no
                """).param("sandbox", sandbox()).query(Integer.class).list();
        assertThat(numbers).hasSize(Decisions.cases().size()).startsWith(1, 2, 3).endsWith(200);
    }

    // FR-9, FR-10: a decision of the fixture set names its case. Expected: case 17 of cases-200.json
    @Test
    void aStoredDecisionOfTheFixtureSetNamesItsCase() {
        String body = decisions.decide(FIXTURE_SET).body();
        List<String> ids = JsonPath.read(body, "$.results[?(@.caseNo == " + Decisions.DEMO_CASE + ")].id");
        String id = ids.getFirst();

        String stored = decisions.decision(id).body();

        assertThat((Integer) JsonPath.read(stored, "$.caseNo")).isEqualTo(Decisions.DEMO_CASE);
        assertThat((String) JsonPath.read(stored, "$.decidingRuleId"))
                .isEqualTo(Decisions.expected(Decisions.DEMO_CASE).required("decidingRuleId").asString());
    }

    // NFR-1, conformance C-26 through the API. Expected: the stored decisions of the two runs are byte-identical
    @Test
    void twoRunsGiveByteIdenticalTraces() {
        String first = storedTracesOf(decisions.decide(FIXTURE_SET).body());
        String second = storedTracesOf(decisions.decide(FIXTURE_SET).body());

        assertThat(second).isEqualTo(first);
    }

    // Document 2: a list of cases. Expected: the outcomes of cases-expected.json, in request order
    @Test
    void aListOfCasesIsDecidedInOrder() {
        String body = decisions.decide("{\"cases\":[" + Decisions.input(1) + "," + Decisions.input(17) + ","
                + Decisions.input(2) + "]}").body();

        assertThat(JsonPath.<List<String>>read(body, "$.results[*].outcome")).containsExactly(
                outcomeOf(1), outcomeOf(17), outcomeOf(2));
        // a case of its own carries no case number: only a seeded fixture has one (Document 2, case_fixture)
        assertThat(JsonPath.<List<Object>>read(body, "$.results[*].caseNo")).isEmpty();
    }

    // Document 5, Availability: 500 cases per request. Expected: refused before anything is evaluated or stored
    @Test
    void moreThan500CasesIsRefusedBeforeAnyEvaluation() {
        HttpResponse<String> response = decisions.decide(tooManyCases());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/cases");
        assertThat(storedDecisions()).isZero();
    }

    // Document 3, step 1: nothing is stored when a case is invalid. Expected: 422 and no decision at all
    @Test
    void aBatchWithOneInvalidCaseIs422AndStoresNothing() {
        String body = "{\"cases\":[" + Decisions.input(1) + ","
                + Decisions.input(2).deepCopy().put("term_months", 0) + "]}";

        HttpResponse<String> response = decisions.decide(body);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/cases/1/term_months");
        assertThat(storedDecisions()).isZero();
    }

    // Document 5, Query parameters: enumerated values. Expected: 400 REQUEST_INVALID, nothing stored
    @Test
    void anUnknownFixtureSetIsRefused() {
        HttpResponse<String> response = decisions.decide("{\"fixtureSet\":\"cases-999\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/fixtureSet");
        assertThat(storedDecisions()).isZero();
    }

    // Document 2, decide: one case, a list or a fixture set. Expected: 400 REQUEST_INVALID
    @Test
    void aRequestNeedsExactlyOneOfCaseCasesAndFixtureSet() {
        HttpResponse<String> both = decisions.decide("{\"case\":" + Decisions.input(1) + ",\"fixtureSet\":\"cases-200\"}");
        HttpResponse<String> neither = decisions.decide("{}");

        assertThat(both.statusCode()).isEqualTo(400);
        assertThat(neither.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(neither.body(), "$.code")).isEqualTo("REQUEST_INVALID");
    }

    // Document 5, Batch decide: 5 requests per minute per sandbox. Expected: 429 with Retry-After
    @Test
    void theSixthBatchInAMinuteFromOneSandboxIs429WithRetryAfter() {
        String oneCase = "{\"cases\":[" + Decisions.input(1) + "]}";
        for (int i = 0; i < 5; i++) {
            assertThat(decisions.decide(oneCase).statusCode()).isEqualTo(200);
        }

        HttpResponse<String> sixth = decisions.decide(oneCase);

        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(sixth.headers().firstValue("Retry-After")).isPresent();
        assertThat((String) JsonPath.read(sixth.body(), "$.code")).isEqualTo("RATE_LIMITED");
    }

    // Document 5: the limit counts batches. Expected: a single case still decides after five batches
    @Test
    void singleCaseDecisionsDoNotCountAgainstTheBatchLimit() {
        String oneCase = "{\"cases\":[" + Decisions.input(1) + "]}";
        for (int i = 0; i < 5; i++) {
            decisions.decide(oneCase);
        }

        HttpResponse<String> single = decisions.decide("{\"case\":" + Decisions.input(1) + "}");

        assertThat(single.statusCode()).isEqualTo(200);
    }

    // Document 5: per sandbox, not per IP. Expected: another session decides its batch from the same address
    @Test
    void theBatchLimitIsPerSandbox() {
        String oneCase = "{\"cases\":[" + Decisions.input(1) + "]}";
        for (int i = 0; i < 5; i++) {
            decisions.decide(oneCase);
        }

        Decisions other = new Decisions(api(), api().login());

        assertThat(other.decide(oneCase).statusCode()).isEqualTo(200);
    }

    private UUID sandbox() {
        return UUID.fromString(session.substring(0, session.indexOf('.')));
    }

    private int storedDecisions() {
        return jdbc.sql("select count(*) from decision where sandbox_id = :sandbox")
                .param("sandbox", sandbox()).query(Integer.class).single();
    }

    private static String tooManyCases() {
        StringBuilder body = new StringBuilder("{\"cases\":[");
        for (int i = 0; i <= 500; i++) {
            body.append(i > 0 ? "," : "").append(Decisions.input(1));
        }
        return body.append("]}").toString();
    }

    private static String outcomeOf(int caseNo) {
        return Decisions.expected(caseNo).required("outcome").stringValue();
    }

    private static String summary(JsonNode result) {
        return result.required("outcome").asString() + "|" + result.required("decidingRuleId").asString() + "|"
                + result.required("flags").valueStream().map(JsonNode::asString).sorted().toList();
    }

    private static String expectedSummary(JsonNode line) {
        return line.required("outcome").asString() + "|" + line.required("decidingRuleId").asString() + "|"
                + line.required("flags").valueStream().map(JsonNode::asString).sorted().toList();
    }

    /** The stored decisions of one batch, in case order: what determinism compares between two runs. */
    private String storedTracesOf(String batch) {
        List<UUID> ids = JsonPath.<List<String>>read(batch, "$.results[*].id").stream().map(UUID::fromString).toList();
        return jdbc.sql("""
                select string_agg(d.trace_json::text, '|' order by c.case_no)
                from decision d join case_fixture c on c.id = d.case_id
                where d.id in (:ids)
                """).param("ids", ids).query(String.class).single();
    }
}
