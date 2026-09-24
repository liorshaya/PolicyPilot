package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The contract of the rule set routes against the OpenAPI document the API serves (Document 2, API Surface;
 * Document 6, Contract level): status codes, the envelope, and the shapes the web app generates its client from. The
 * review route's model is the recorded gateway, scripted per test.
 */
@Requirement({"FR-5", "FR-6", "FR-7"})
@Import(RecordedModel.class)
@Isolated
class RulesetControllerContractIT extends ApiIntegrationTest {

    /** Nothing to report: the answer that lets a draft be published (Document 2, Flow 1). */
    private static final String CLEAN = "{\"findings\": [], \"coverage\": {}}";
    /** A gap on paragraph 3 and R-160, SF-4 of fixtures/eval/policies/consumer-lending/seeded.findings.json. */
    private static final String GAP = """
            {"findings": [{"kind": "gap", "severity": "warning", "ruleIds": ["R-160"], "paragraphIndexes": [3],
              "message": "סעיף הוותק לעצמאים אינו מכוסה", "suggestion": "להוסיף כלל", "confidence": 0.7}],
             "coverage": {"3": ["R-150"]}}
            """;

    @Autowired
    private RecordedGateway model;

    private static final String RULESETS = "/api/v1/rulesets";
    private static final String VERSION = "/api/v1/rulesets/{id}/versions/{no}";
    private static final String DIFF = "/api/v1/rulesets/{id}/versions/{a}/diff/{b}";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String session;
    private OpenApiContract contract;

    @BeforeEach
    void logIn() {
        model.reset();
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
    }

    // Document 2, GET /rulesets. Expected: the served OpenAPI document, and the two seeded rule sets in the list (the
    // lending rule set and the second domain's, Document 2, Second domain), the lending one published
    @Test
    void listRuleSetsMatchesTheDocumented200() {
        HttpResponse<String> response = api().get(RULESETS).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", RULESETS, 200, response.body())).isEmpty();
        assertThat((List<String>) JsonPath.read(response.body(), "$.rulesets[?(@.protected == true)].domain"))
                .containsExactlyInAnyOrder(Seeded.LENDING, Seeded.SECOND_DOMAIN);
        assertThat((List<String>) JsonPath.read(response.body(),
                "$.rulesets[?(@.domain == '" + Seeded.LENDING + "')].versions[0].status")).containsExactly("PUBLISHED");
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
        UUID fork = reviewedFork(CLEAN);

        HttpResponse<String> response = api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", VERSION + "/publish", 200, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.status")).isEqualTo("PUBLISHED");
        assertThat((String) JsonPath.read(response.body(), "$.publishedBy")).isEqualTo(sandboxOf(session));
    }

    // Brief FR-7; Document 2, VERSION_STATUS_CONFLICT. Expected: 409 with the envelope
    @Test
    void publishingAPublishedVersionMatchesTheDocumented409() {
        UUID fork = reviewedFork(CLEAN);
        api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send();

        HttpResponse<String> response = api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", VERSION + "/publish", 409, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("VERSION_STATUS_CONFLICT");
    }

    // Document 2, Flow 1 and FINDINGS_UNRESOLVED. Expected: 422 with /review and REVIEW_MISSING
    @Test
    void publishingAnUnreviewedDraftMatchesTheDocumented422() {
        UUID fork = UUID.fromString(JsonPath.read(put(seeded(), 1, edited()).body(), "$.rulesetId"));

        HttpResponse<String> response = api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(contract.violations("post", VERSION + "/publish", 422, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("FINDINGS_UNRESOLVED");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/review");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].problem")).isEqualTo("REVIEW_MISSING");
    }

    // Document 2, POST .../review. Expected: the documented 200 and the gap as F-1, blocking
    @Test
    void reviewMatchesTheDocumented200() {
        UUID fork = UUID.fromString(JsonPath.read(put(seeded(), 1, edited()).body(), "$.rulesetId"));
        model.willAnswer(GAP);

        HttpResponse<String> response = api().post(versionPath(fork, 1) + "/review").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", VERSION + "/review", 200, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.review.status")).isEqualTo("DONE");
        assertThat((String) JsonPath.read(response.body(), "$.review.findings[0].id")).isEqualTo("F-1");
        assertThat((Boolean) JsonPath.read(response.body(), "$.review.findings[0].blocking")).isTrue();
        // Document 2: "a fresh call: the cached answer for the same draft is forgotten first"
        assertThat(model.forgotten()).extracting(spec -> spec.promptName()).containsExactly("review");
    }

    // Document 2, POST .../review: "503 PROVIDER_UNAVAILABLE leaves the review FAILED"
    @Test
    void aReviewWhoseProviderIsDownMatchesTheDocumented503AndLeavesTheReviewFailed() {
        UUID fork = UUID.fromString(JsonPath.read(put(seeded(), 1, edited()).body(), "$.rulesetId"));
        model.willFail(new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "the provider timed out"));

        HttpResponse<String> response = api().post(versionPath(fork, 1) + "/review").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(contract.violations("post", VERSION + "/review", 503, response.body())).isEmpty();
        assertThat((String) JsonPath.read(api().get(versionPath(fork, 1)).cookie(session).send().body(),
                "$.review.status")).isEqualTo("FAILED");
    }

    // Document 2, POST .../review: "409 on a version that is not a DRAFT or is protected"; no model is asked
    @Test
    void reviewingTheProtectedVersionMatchesTheDocumented409() {
        HttpResponse<String> response = api().post(versionPath(seeded(), 1) + "/review").web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", VERSION + "/review", 409, response.body())).isEmpty();
        assertThat(model.asked()).isEmpty();
    }

    // Document 2, acknowledge; Document 3, Publishing gate. Expected: the documented 200, the resolution kept, and
    // the draft publishable afterwards
    @Test
    void acknowledgingTheGapMatchesTheDocumented200AndUnblocksThePublish() {
        UUID fork = reviewedFork(GAP);

        HttpResponse<String> response = acknowledge(fork, "F-1", "{\"resolution\": \"rule_added\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", VERSION + "/findings/{findingId}/acknowledge", 200, response.body()))
                .isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.review.findings[0].acknowledgement.resolution"))
                .isEqualTo("rule_added");
        assertThat((Boolean) JsonPath.read(response.body(), "$.review.findings[0].blocking")).isFalse();
        assertThat(api().post(versionPath(fork, 1) + "/publish").web().cookie(session).send().statusCode())
                .isEqualTo(200);
    }

    // Document 2, acknowledge: "400 REQUEST_INVALID when the resolution or the note the kind needs is missing"
    @Test
    void acknowledgingAGapWithoutAResolutionMatchesTheDocumented400() {
        UUID fork = reviewedFork(GAP);

        HttpResponse<String> response = acknowledge(fork, "F-1", "{\"note\": \"covered\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contract.violations("post", VERSION + "/findings/{findingId}/acknowledge", 400, response.body()))
                .isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/resolution");
    }

    // Document 2, acknowledge: "404 for an unknown finding id"
    @Test
    void acknowledgingAnUnknownFindingMatchesTheDocumented404() {
        UUID fork = reviewedFork(GAP);

        HttpResponse<String> response = acknowledge(fork, "F-2", "{}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", VERSION + "/findings/{findingId}/acknowledge", 404, response.body()))
                .isEmpty();
    }

    // Document 2, GET .../diff/{b}; Document 3, Structural diff. Expected: the served document's shape, and a
    // version against itself with nothing added, removed or modified
    @Test
    void aDiffOfAVersionWithItselfMatchesTheDocumented200() {
        HttpResponse<String> response = api().get(versionPath(seeded(), 1) + "/diff/1").cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", DIFF, 200, response.body())).isEmpty();
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("""
                {"fields": {"added": [], "removed": [], "modified": []},
                 "rules": {"added": [], "removed": [], "modified": []},
                 "defaults": null}"""));
    }

    // Document 5, no existence oracle. Expected: 404 for a version the rule set does not have, and for the version of
    // a rule set another sandbox owns
    @Test
    void aDiffWithAVersionThatIsNotThereMatchesTheDocumented404() {
        UUID fork = UUID.fromString(JsonPath.read(put(seeded(), 1, edited()).body(), "$.rulesetId"));

        HttpResponse<String> missing = api().get(versionPath(seeded(), 1) + "/diff/2").cookie(session).send();
        HttpResponse<String> foreign = api().get(versionPath(fork, 1) + "/diff/1")
                .cookie(api().login("198.51.100.23")).send();

        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(contract.violations("get", DIFF, 404, missing.body())).isEmpty();
        assertThat(foreign.statusCode()).isEqualTo(404);
    }

    /** The sandbox's copy of the seeded rule set, edited and reviewed with the answer given. */
    private UUID reviewedFork(String review) {
        UUID fork = UUID.fromString(JsonPath.read(put(seeded(), 1, edited()).body(), "$.rulesetId"));
        model.willAnswer(review);
        assertThat(api().post(versionPath(fork, 1) + "/review").web().cookie(session).send().statusCode())
                .isEqualTo(200);
        return fork;
    }

    private HttpResponse<String> acknowledge(UUID ruleset, String findingId, String body) {
        return api().post(versionPath(ruleset, 1) + "/findings/" + findingId + "/acknowledge").web().cookie(session)
                .json(body).send();
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
        List<String> ids = JsonPath.read(body, Seeded.LENDING_RULESET_ID);
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
