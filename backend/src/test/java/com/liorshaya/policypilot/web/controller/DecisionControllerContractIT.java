package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The contract of the decision routes against the OpenAPI document the API serves (Document 2, API Surface;
 * Document 6, Contract level): status codes, the envelope and the shapes the web app generates its client from.
 */
@Requirement({"FR-8", "FR-9", "FR-14"})
class DecisionControllerContractIT extends ApiIntegrationTest {

    private static final String DECIDE = "/api/v1/rulesets/{id}/versions/{no}/decide";
    private static final String STATS = "/api/v1/rulesets/{id}/versions/{no}/stats";
    private static final String SIMULATE = "/api/v1/rulesets/{id}/versions/{no}/simulate";
    private static final String DECISION = "/api/v1/decisions/{id}";

    private String session;
    private OpenApiContract contract;
    private String version;

    @BeforeEach
    void logIn() {
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
        version = seededVersion();
    }

    // Document 2, decide one case. Expected: the served OpenAPI document
    @Test
    void decideOneCaseMatchesTheDocumented200() {
        HttpResponse<String> response = decide("{\"case\":" + demoCase() + "}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", DECIDE, 200, response.body())).isEmpty();
    }

    // Document 2, decide a batch. Expected: the served OpenAPI document, with the aggregates and the per-case lines
    @Test
    void decideTheFixtureSetMatchesTheDocumented200() {
        HttpResponse<String> response = decide("{\"fixtureSet\":\"cases-200\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", DECIDE, 200, response.body())).isEmpty();
        assertThat((List<?>) JsonPath.read(response.body(), "$.results")).hasSize(200);
    }

    // Document 2, decide 422. Expected: the served OpenAPI document and the envelope
    @Test
    void decideAnInvalidCaseMatchesTheDocumented422() {
        HttpResponse<String> response = decide("{\"case\":" + demoCase().deepCopy().put("term_months", 0) + "}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(contract.violations("post", DECIDE, 422, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("CASE_INVALID");
    }

    // Document 2, GET a decision. Expected: the served OpenAPI document
    @Test
    void getDecisionMatchesTheDocumented200() {
        String id = JsonPath.read(decide("{\"case\":" + demoCase() + "}").body(), "$.id");

        HttpResponse<String> response = api().get("/api/v1/decisions/" + id).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", DECISION, 200, response.body())).isEmpty();
    }

    // Document 2, NOT_FOUND. Expected: the served OpenAPI document
    @Test
    void anUnknownDecisionMatchesTheDocumented404() {
        HttpResponse<String> response = api().get("/api/v1/decisions/" + UUID.randomUUID()).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contract.violations("get", DECISION, 404, response.body())).isEmpty();
    }

    // Document 2, stats. Expected: the served OpenAPI document
    @Test
    void statsMatchTheDocumented200() {
        decide("{\"case\":" + demoCase() + "}");

        HttpResponse<String> response = api().get(version + "/stats").cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", STATS, 200, response.body())).isEmpty();
    }

    // Document 2, simulate. Expected: the served OpenAPI document
    @Test
    void simulateMatchesTheDocumented200() {
        String id = JsonPath.read(decide("{\"case\":" + demoCase() + "}").body(), "$.id");

        HttpResponse<String> response = api().post(version + "/simulate").web().cookie(session)
                .json("{\"decisionId\":\"" + id + "\",\"overrides\":{\"has_guarantor\":true}}").send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", SIMULATE, 200, response.body())).isEmpty();
    }

    // Brief FR-7; Document 2, VERSION_STATUS_CONFLICT. Expected: the served OpenAPI document
    @Test
    void decidingOnADraftMatchesTheDocumented409() {
        String draft = draftOfItsOwn();

        HttpResponse<String> response = api().post(draft + "/decide").web().cookie(session)
                .json("{\"case\":" + demoCase() + "}").send();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", DECIDE, 409, response.body())).isEmpty();
    }

    private HttpResponse<String> decide(String body) {
        return api().post(version + "/decide").web().cookie(session).json(body).send();
    }

    private String seededVersion() {
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                Seeded.LENDING_RULESET_ID);
        return "/api/v1/rulesets/" + UUID.fromString(ids.getFirst()) + "/versions/1";
    }

    private String draftOfItsOwn() {
        String body = api().method("PUT", version + "/rules").web().cookie(session)
                .json(Fixtures.lendingV1().toString()).send().body();
        return "/api/v1/rulesets/" + JsonPath.<String>read(body, "$.rulesetId") + "/versions/1";
    }

    private static tools.jackson.databind.node.ObjectNode demoCase() {
        JsonNode cases = Fixtures.json("policies/consumer-lending/cases-200.json").required("cases");
        return (tools.jackson.databind.node.ObjectNode) cases.valueStream()
                .filter(fixture -> fixture.path("id").asInt() == 17)
                .findFirst()
                .orElseThrow()
                .required("input");
    }
}
