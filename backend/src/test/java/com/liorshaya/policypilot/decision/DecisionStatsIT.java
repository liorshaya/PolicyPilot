package com.liorshaya.policypilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /rulesets/{id}/versions/{no}/stats}: outcome counts and the top deciding rules of this sandbox on this
 * version (Document 2, API Surface). The expected numbers are the summary of {@code cases-expected.json}.
 */
@Requirement("FR-9")
class DecisionStatsIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FIXTURE_SET = "{\"fixtureSet\":\"cases-200\"}";

    private Decisions decisions;

    @BeforeEach
    void logIn() {
        decisions = new Decisions(api(), api().login());
    }

    // Document 2, stats. Expected: the summary of cases-expected.json (top five, by count then rule id)
    @Test
    void statsAfterTheFixtureRunEqualTheExpectedSummary() {
        decisions.decide(FIXTURE_SET);

        HttpResponse<String> response = decisions.stats();

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode summary = Decisions.expected().required("summary");
        JsonNode stats = JSON.readTree(response.body());
        assertThat(stats.required("outcomes")).isEqualTo(summary.required("outcomes"));
        assertThat(stats.required("topDecidingRules")).isEqualTo(summary.required("topDecidingRules"));
        assertThat(stats.required("decisions").asInt()).isEqualTo(Decisions.cases().size());
    }

    // Document 2: the latest decision of each case counts once. Expected: the same summary after two runs
    @Test
    void rerunningTheFixtureSetDoesNotDoubleTheCounts() {
        decisions.decide(FIXTURE_SET);
        decisions.decide(FIXTURE_SET);

        JsonNode stats = JSON.readTree(decisions.stats().body());

        assertThat(stats.required("outcomes")).isEqualTo(Decisions.expected().required("summary").required("outcomes"));
        assertThat(stats.required("decisions").asInt()).isEqualTo(Decisions.cases().size());
    }

    // Document 5, sandbox scoping of decisions. Expected: another sandbox's run is not counted here
    @Test
    void statsCountOnlyTheCallersSandbox() {
        new Decisions(api(), api().login()).decide(FIXTURE_SET);

        JsonNode stats = JSON.readTree(decisions.stats().body());

        assertThat(stats.required("decisions").asInt()).isZero();
    }

    // Document 2, stats. Expected: zero counts and no deciding rules before anything was decided
    @Test
    void statsOfAVersionWithoutDecisionsAreEmpty() {
        HttpResponse<String> response = decisions.stats();

        Map<String, Integer> outcomes = JsonPath.read(response.body(), "$.outcomes");
        assertThat(outcomes).containsOnly(Map.entry("approve", 0), Map.entry("reject", 0), Map.entry("refer", 0));
        assertThat((List<?>) JsonPath.read(response.body(), "$.topDecidingRules")).isEmpty();
    }
}
