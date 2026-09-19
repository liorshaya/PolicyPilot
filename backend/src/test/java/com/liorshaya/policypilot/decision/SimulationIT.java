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

/**
 * {@code POST /rulesets/{id}/versions/{no}/simulate}: the what-if the chat's guarantor question needs (Document 2,
 * API Surface; Document 3, Simulation; conformance C-31). Nothing is stored, and the decision the simulation is
 * based on stays as it was.
 */
@Requirement("FR-14")
class SimulationIT extends ApiIntegrationTest {

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

    // Conformance C-31. Expected: fixtures/conformance/C-31.json
    @Test
    void simulatingCase17WithAGuarantorApprovesByR900WithTheManualCheckFlag() {
        JsonNode fixture = Fixtures.json("conformance/C-31.json");
        String decisionId = JsonPath.read(decisions.decide(demoCase()).body(), "$.id");

        HttpResponse<String> response = decisions.simulate("{\"decisionId\":\"" + decisionId + "\",\"overrides\":"
                + fixture.required("overrides") + "}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonSubset.mismatch(fixture.required("expected"), JSON.readTree(response.body()))).isNull();
        assertThat(JsonPath.<List<String>>read(response.body(), "$.flags[*].code")).contains(approvalFlag());
    }

    // Document 2, simulate: simulation true and basedOnDecisionId. Expected: the decision it was based on
    @Test
    void theSimulationIsMarkedAndNamesTheDecisionItIsBasedOn() {
        String decisionId = JsonPath.read(decisions.decide(demoCase()).body(), "$.id");

        String body = decisions.simulate(
                "{\"decisionId\":\"" + decisionId + "\",\"overrides\":{\"has_guarantor\":true}}").body();

        assertThat((Boolean) JsonPath.read(body, "$.simulation")).isTrue();
        assertThat((String) JsonPath.read(body, "$.basedOnDecisionId")).isEqualTo(decisionId);
        assertThat((Boolean) JsonPath.read(body, "$.overrides.has_guarantor")).isTrue();
    }

    // C-31; Document 2, decision: simulations are never written. Expected: no new row, the base unchanged
    @Test
    void aSimulationStoresNothingAndLeavesTheBaseDecisionUnchanged() {
        String stored = decisions.decide(demoCase()).body();
        String decisionId = JsonPath.read(stored, "$.id");
        int before = storedDecisions();

        decisions.simulate("{\"decisionId\":\"" + decisionId + "\",\"overrides\":{\"has_guarantor\":true}}");

        assertThat(storedDecisions()).isEqualTo(before);
        assertThat(JSON.readTree(decisions.decision(decisionId).body())).isEqualTo(JSON.readTree(stored));
    }

    // Document 2, simulate: a stored decision id or a case. Expected: C-31's outcome, without a base decision
    @Test
    void anInlineCaseCanBeSimulatedWithOverrides() {
        JsonNode fixture = Fixtures.json("conformance/C-31.json");

        String body = decisions.simulate("{\"case\":" + fixture.required("case") + ",\"overrides\":"
                + fixture.required("overrides") + "}").body();

        assertThat((String) JsonPath.read(body, "$.outcome"))
                .isEqualTo(fixture.required("expected").required("outcome").stringValue());
        assertThat(JsonPath.<Map<String, Object>>read(body, "$")).doesNotContainKey("basedOnDecisionId");
    }

    // Document 5, Tool argument validation: overrides name declared, non-derived fields. Expected: 422 CASE_INVALID
    @Test
    void overridingADerivedFieldIs422() {
        String decisionId = JsonPath.read(decisions.decide(demoCase()).body(), "$.id");

        HttpResponse<String> response = decisions.simulate(
                "{\"decisionId\":\"" + decisionId + "\",\"overrides\":{\"debt_to_income\":0.1}}");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("CASE_INVALID");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].problem")).isEqualTo("CASE_DERIVED_SUPPLIED");
    }

    // Document 2, simulate: overrides must name declared fields. Expected: 400, and the field is not echoed
    @Test
    void overridingAnUndeclaredFieldIsRefused() {
        String decisionId = JsonPath.read(decisions.decide(demoCase()).body(), "$.id");

        HttpResponse<String> response = decisions.simulate(
                "{\"decisionId\":\"" + decisionId + "\",\"overrides\":{\"favourite_colour\":\"blue\"}}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/overrides");
        assertThat(response.body()).doesNotContain("favourite_colour");
    }

    // Document 5, Authorization (sandbox); RT-03's foundation. Expected: 404, no simulation of a foreign decision
    @Test
    void anotherSandboxsDecisionCannotBeSimulated() {
        Decisions other = new Decisions(api(), api().login());
        String foreign = JsonPath.read(other.decide(demoCase()).body(), "$.id");

        HttpResponse<String> response = decisions.simulate(
                "{\"decisionId\":\"" + foreign + "\",\"overrides\":{\"has_guarantor\":true}}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("NOT_FOUND");
    }

    /** The flag every approval of the lending policy carries, as cases-expected.json records it. */
    private static String approvalFlag() {
        return Decisions.expected().required("cases").valueStream()
                .filter(line -> "approve".equals(line.path("outcome").asString()))
                .findFirst()
                .orElseThrow()
                .required("flags")
                .get(0)
                .asString();
    }

    private static String demoCase() {
        return "{\"case\":" + Decisions.input(Decisions.DEMO_CASE) + "}";
    }

    private int storedDecisions() {
        return jdbc.sql("select count(*) from decision where sandbox_id = :sandbox")
                .param("sandbox", UUID.fromString(session.substring(0, session.indexOf('.'))))
                .query(Integer.class).single();
    }
}
