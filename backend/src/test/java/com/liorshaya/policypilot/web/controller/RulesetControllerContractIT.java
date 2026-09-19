package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * The contract of the rule set routes against the OpenAPI document the API serves (Document 2, API Surface;
 * Document 6, Contract level): status codes, the envelope, and the shapes the web app generates its client from.
 */
@Requirement({"FR-6", "FR-7"})
class RulesetControllerContractIT extends ApiIntegrationTest {

    private static final String RULESETS = "/api/v1/rulesets";
    private static final String VERSION = "/api/v1/rulesets/{id}/versions/{no}";

    private String session;
    private OpenApiContract contract;

    @BeforeEach
    void logIn() {
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
    }

    // Document 2, GET /rulesets. Expected: the served OpenAPI document, and the seeded rule set in the list
    @Test
    void listRuleSetsMatchesTheDocumented200() {
        HttpResponse<String> response = api().get(RULESETS).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", RULESETS, 200, response.body())).isEmpty();
        assertThat((List<?>) JsonPath.read(response.body(), "$.rulesets[?(@.protected == true)]")).hasSize(1);
        assertThat((String) JsonPath.read(response.body(), "$.rulesets[0].domain"))
                .isEqualTo(Fixtures.lendingV1().get("id").stringValue());
        assertThat((String) JsonPath.read(response.body(), "$.rulesets[0].versions[0].status")).isEqualTo("PUBLISHED");
    }

    // Document 2, GET a version. Expected: the served OpenAPI document and ruleset.v1.json
    @Test
    void getVersionMatchesTheDocumented200() {
        HttpResponse<String> response = api().get(versionPath(seeded(), 1)).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", VERSION, 200, response.body())).isEmpty();
        assertThat((Integer) JsonPath.read(response.body(), "$.versionNo")).isEqualTo(1);
        assertThat((String) JsonPath.read(response.body(), "$.status")).isEqualTo("PUBLISHED");
        assertThat((List<?>) JsonPath.read(response.body(), "$.ruleSet.rules"))
                .hasSize(Fixtures.lendingV1().withArray("rules").size());
    }

    // Document 2, NOT_FOUND. Expected: the served OpenAPI document
    @Test
    void anUnknownVersionMatchesTheDocumented404() {
        HttpResponse<String> response = api().get(versionPath(UUID.randomUUID(), 1)).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contract.violations("get", VERSION, 404, response.body())).isEmpty();
    }

    // Document 2, PUT rules on a protected version: the sandbox gets its own copy. Expected: the documented 200
    @Test
    void putRulesMatchesTheDocumented200() {
        HttpResponse<String> response = put(seeded(), 1, edited());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("put", VERSION + "/rules", 200, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.status")).isEqualTo("DRAFT");
        assertThat((String) JsonPath.read(response.body(), "$.forkedFromId")).isEqualTo(seeded().toString());
    }

    // Document 2, PUT rules 422 with the error list. Expected: the served OpenAPI document and the envelope
    @Test
    void putRulesInvalidMatchesTheDocumented422() {
        // R-010 and R-020 set the derived fields; moving both behind the rules that read them breaks DERIVED_ORDER
        ObjectNode broken = Fixtures.lendingV1();
        ((ObjectNode) broken.withArray("rules").get(0)).put("priority", 998);
        ((ObjectNode) broken.withArray("rules").get(1)).put("priority", 999);

        HttpResponse<String> response = put(seeded(), 1, broken);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(contract.violations("put", VERSION + "/rules", 422, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("RULESET_INVALID");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].problem")).matches("[A-Z_]+");
    }

    // Document 2, publish. Expected: the served OpenAPI document, status PUBLISHED and the publisher
    @Test
    void publishMatchesTheDocumented200() {
        UUID fork = UUID.fromString(JsonPath.read(put(seeded(), 1, edited()).body(), "$.rulesetId"));

        HttpResponse<String> response = api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", VERSION + "/publish", 200, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.status")).isEqualTo("PUBLISHED");
        assertThat((String) JsonPath.read(response.body(), "$.publishedBy")).isEqualTo(sandboxOf(session));
    }

    // Brief FR-7; Document 2, VERSION_STATUS_CONFLICT. Expected: 409 with the envelope
    @Test
    void publishingAPublishedVersionMatchesTheDocumented409() {
        UUID fork = UUID.fromString(JsonPath.read(put(seeded(), 1, edited()).body(), "$.rulesetId"));
        api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send();

        HttpResponse<String> response = api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", VERSION + "/publish", 409, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("VERSION_STATUS_CONFLICT");
    }

    private HttpResponse<String> put(UUID ruleset, int versionNo, ObjectNode document) {
        return api().method("PUT", versionPath(ruleset, versionNo) + "/rules").web().cookie(session)
                .json(document.toString()).send();
    }

    /** The committed lending document with one rule's priority changed: the edit an analyst makes in the table. */
    private static ObjectNode edited() {
        ObjectNode document = Fixtures.lendingV1();
        ((ObjectNode) document.withArray("rules").get(document.withArray("rules").size() - 1)).put("priority", 901);
        return document;
    }

    /** The seeded rule set, as the list route shows it to any session. */
    private UUID seeded() {
        String body = api().get(RULESETS).cookie(session).send().body();
        List<String> ids = JsonPath.read(body, "$.rulesets[?(@.protected == true)].id");
        return UUID.fromString(ids.getFirst());
    }

    private static String versionPath(UUID ruleset, int versionNo) {
        return "/api/v1/rulesets/" + ruleset + "/versions/" + versionNo;
    }

    /** The cookie is {@code sandboxId.issuedAt.signature}, so its first part is the actor a publish records. */
    private static String sandboxOf(String session) {
        return session.substring(0, session.indexOf('.'));
    }
}
