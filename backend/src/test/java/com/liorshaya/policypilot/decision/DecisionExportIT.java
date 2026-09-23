package com.liorshaya.policypilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.common.Csv;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /decisions/{id}/export} as JSON or CSV (Document 2, API Surface; Document 5: CSV cells are
 * formula-prefixed and served as an attachment; Document 6, Contract level).
 */
@Requirement("FR-10")
class DecisionExportIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EXPORT = "/api/v1/decisions/{id}/export";

    private Decisions decisions;
    private String session;
    private OpenApiContract contract;

    @BeforeEach
    void logIn() {
        session = api().login();
        decisions = new Decisions(api(), session);
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
    }

    // Document 2, export: one row per trace step. Expected: the 20 steps of sample-decision.json
    @Test
    void theCsvExportOfCase17HasOneRowPerTraceStep() {
        String id = decide();

        HttpResponse<String> response = export(id, "text/csv");

        assertThat(response.statusCode()).isEqualTo(200);
        int steps = Fixtures.json("policies/consumer-lending/sample-decision.json").required("trace").size();
        assertThat(response.body().split("\r\n")).hasSize(steps + 1);
        assertThat(response.body()).startsWith(Csv.BYTE_ORDER_MARK + "decision_id,");
    }

    // Document 5, CSV row: text/csv, served as an attachment. Expected: the headers of Document 5
    @Test
    void theCsvExportIsServedAsATextCsvAttachment() {
        String id = decide();

        HttpResponse<String> response = export(id, "text/csv");

        assertThat(response.headers().firstValue("Content-Type")).contains("text/csv;charset=UTF-8");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"decision-" + id + ".csv\"");
    }

    // Document 2, export as JSON. Expected: the body of GET /decisions/{id}, as an attachment
    @Test
    void theJsonExportEqualsTheStoredDecision() {
        String id = decide();

        HttpResponse<String> response = export(id, "application/json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", EXPORT, 200, response.body())).isEmpty();
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree(decisions.decision(id).body()));
        assertThat(response.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"decision-" + id + ".json\"");
    }

    // Document 2, UNSUPPORTED_MEDIA_TYPE: an Accept the route cannot produce. Expected: 415 with the envelope
    @Test
    void anAcceptTheRouteCannotProduceIs415() {
        String id = decide();

        HttpResponse<String> response = export(id, "application/xml");

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    // Document 5, Authorization (sandbox). Expected: 404 for another sandbox's decision
    @Test
    void anotherSandboxsDecisionCannotBeExported() {
        String foreign = new Decisions(api(), api().login()).decide(demoCase()).body();

        HttpResponse<String> response = export(JsonPath.read(foreign, "$.id"), "text/csv");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contract.violations("get", EXPORT, 404, response.body())).isEmpty();
    }

    private String decide() {
        return JsonPath.read(decisions.decide(demoCase()).body(), "$.id");
    }

    private HttpResponse<String> export(String id, String accept) {
        return api().get("/api/v1/decisions/" + UUID.fromString(id) + "/export").cookie(session)
                .header("Accept", accept).send();
    }

    private static String demoCase() {
        return "{\"case\":" + Decisions.input(Decisions.DEMO_CASE) + "}";
    }
}
