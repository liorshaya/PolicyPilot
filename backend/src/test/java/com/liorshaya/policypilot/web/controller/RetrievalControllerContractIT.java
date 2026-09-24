package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@code POST /api/v1/rulesets/{id}/versions/{no}/retrieval} against the OpenAPI document the API serves (Document 2,
 * API Surface: read-only, scoped to the caller's sandbox, 409 while the version is not READY; Document 5: the
 * question follows the chat message limits). The seeded version is embedded at startup by the fake gateway, whose
 * vectors all lie on one axis, so every chunk scores cosine 1 against a question it does not know.
 */
@Requirement({"FR-13", "FR-15"})
class RetrievalControllerContractIT extends ApiIntegrationTest {

    private static final String RETRIEVAL = "/api/v1/rulesets/{id}/versions/{no}/retrieval";

    @Autowired
    private JdbcClient jdbc;

    private String session;
    private OpenApiContract contract;
    private String version;

    @BeforeEach
    void logIn() {
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
        version = seededVersion();
        awaitSeededVersionReady();
    }

    // Document 2, the route's 200: the fused chunks with their ids, scores and citations. Expected: the served OpenAPI
    // document, covered, the top 8 of the 29 lending chunks, and a citation per chunk
    @Test
    void aQuestionOnTheSeededVersionMatchesTheDocumented200() {
        HttpResponse<String> response = retrieve(version, "{\"question\":\"מהי תקופת ההחזר המקסימלית להלוואה?\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", RETRIEVAL, 200, response.body())).isEmpty();
        assertThat((Boolean) JsonPath.read(response.body(), "$.covered")).isTrue();
        assertThat((List<?>) JsonPath.read(response.body(), "$.chunks")).hasSize(8);
        assertThat((List<?>) JsonPath.read(response.body(), "$.citations")).hasSize(8);
    }

    // Document 5, no existence oracle: an unknown rule set is 404 like a foreign one. Expected: NOT_FOUND
    @Test
    void anUnknownRuleSetMatchesTheDocumented404() {
        HttpResponse<String> response = retrieve("/api/v1/rulesets/" + UUID.randomUUID() + "/versions/1",
                "{\"question\":\"שאלה\"}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contract.violations("post", RETRIEVAL, 404, response.body())).isEmpty();
    }

    // Document 2, error codes: VERSION_STATUS_CONFLICT for a version with no READY corpus. Expected: 409 for a DRAFT
    @Test
    void aDraftMatchesTheDocumented409() {
        HttpResponse<String> response = retrieve(draftOfItsOwn(), "{\"question\":\"שאלה\"}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(contract.violations("post", RETRIEVAL, 409, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("VERSION_STATUS_CONFLICT");
    }

    // Document 5, Input limits: "Chat message, retrieval question: 2 KB". Expected: 2,049 bytes and a blank question
    // are REQUEST_INVALID; 2,048 bytes are answered
    @Test
    void theQuestionIsLimitedLikeAChatMessage() {
        String atTheLimit = "a".repeat(2048);
        String overTheLimit = "a".repeat(2049);

        assertThat(retrieve(version, "{\"question\":\"" + atTheLimit + "\"}").statusCode()).isEqualTo(200);
        HttpResponse<String> over = retrieve(version, "{\"question\":\"" + overTheLimit + "\"}");
        assertThat(over.statusCode()).isEqualTo(400);
        assertThat(contract.violations("post", RETRIEVAL, 400, over.body())).isEmpty();
        assertThat(retrieve(version, "{\"question\":\"   \"}").statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> retrieve(String versionPath, String body) {
        return api().post(versionPath + "/retrieval").web().cookie(session).json(body).send();
    }

    private String seededVersion() {
        List<String> ids = JsonPath.read(api().get("/api/v1/rulesets").cookie(session).send().body(),
                Seeded.LENDING_RULESET_ID);
        return "/api/v1/rulesets/" + UUID.fromString(ids.getFirst()) + "/versions/1";
    }

    /** The startup job embeds the seeded version off the main thread; the tests start once it has. */
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

    private String draftOfItsOwn() {
        String body = api().method("PUT", version + "/rules").web().cookie(session)
                .json(Fixtures.lendingV1().toString()).send().body();
        return "/api/v1/rulesets/" + JsonPath.<String>read(body, "$.rulesetId") + "/versions/1";
    }
}
