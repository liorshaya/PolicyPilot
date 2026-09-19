package com.liorshaya.policypilot.decision;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.Api;
import com.liorshaya.policypilot.support.Fixtures;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the decision tests build on: the seeded rule set through the API, the committed cases and the expected
 * outcomes the reference produced. Every expected value comes from {@code fixtures/}, never from the code.
 */
final class Decisions {

    static final String RULESETS = "/api/v1/rulesets";
    static final String DECISIONS = "/api/v1/decisions";
    static final String FIXTURE_SET = "cases-200";
    /** Case 17 of the demo fixture: one credit event, no guarantor (Document 3, Worked Example). */
    static final int DEMO_CASE = 17;

    private final Api api;
    private final String session;

    Decisions(Api api, String session) {
        this.api = api;
        this.session = session;
    }

    /** The version of the seeded rule set, which every sandbox may decide against. */
    String versionPath() {
        List<String> ids = JsonPath.read(api.get(RULESETS).cookie(session).send().body(),
                "$.rulesets[?(@.protected == true)].id");
        return RULESETS + "/" + UUID.fromString(ids.getFirst()) + "/versions/1";
    }

    HttpResponse<String> decide(String body) {
        return api.post(versionPath() + "/decide").web().cookie(session).json(body).send();
    }

    HttpResponse<String> decide(String path, String body) {
        return api.post(path + "/decide").web().cookie(session).json(body).send();
    }

    HttpResponse<String> simulate(String body) {
        return api.post(versionPath() + "/simulate").web().cookie(session).json(body).send();
    }

    HttpResponse<String> stats() {
        return api.get(versionPath() + "/stats").cookie(session).send();
    }

    HttpResponse<String> decision(String id) {
        return api.get(DECISIONS + "/" + id).cookie(session).send();
    }

    /** One case of the committed fixture file, by its number. */
    static ObjectNode input(int caseNo) {
        return (ObjectNode) cases().valueStream()
                .filter(fixture -> fixture.path("id").asInt() == caseNo)
                .findFirst()
                .orElseThrow()
                .required("input");
    }

    static JsonNode cases() {
        return Fixtures.json("policies/consumer-lending/cases-200.json").required("cases");
    }

    /** The expected outcomes the reference implementation produced for the 200 cases. */
    static JsonNode expected() {
        return Fixtures.json("policies/consumer-lending/cases-expected.json");
    }

    /** The expected line of one case: outcome, deciding rule, derived values and flags. */
    static JsonNode expected(int caseNo) {
        return expected().required("cases").valueStream()
                .filter(line -> line.path("id").asInt() == caseNo)
                .findFirst()
                .orElseThrow();
    }
}
